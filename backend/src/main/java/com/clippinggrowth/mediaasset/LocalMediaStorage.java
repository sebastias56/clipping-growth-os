package com.clippinggrowth.mediaasset;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalMediaStorage implements MediaStorage {

    private static final int BUFFER_SIZE = 8192;
    private static final Pattern STORAGE_KEY_PATTERN = Pattern.compile(
            "media-assets/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                    + "[0-9a-f]{4}-[0-9a-f]{12}");

    private final Path root;

    public LocalMediaStorage(@Value("${media.storage.local.root}") Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    @Override
    public StoredMedia store(String storageKey, InputStream source) {
        Objects.requireNonNull(source, "source");
        Path destination = resolve(storageKey);
        Path directory = destination.getParent();
        Path temporary = null;

        try {
            createControlledDirectory(root);
            createControlledDirectory(directory);
            rejectExistingObject(destination);

            temporary = Files.createTempFile(
                    directory, "." + destination.getFileName() + "-", ".part");
            MessageDigest digest = sha256Digest();
            long sizeBytes = write(source, temporary, digest);

            if (sizeBytes == 0) {
                throw new MediaStorageException("Media content must not be empty");
            }

            moveCreateOnly(temporary, destination);
            temporary = null;
            return new StoredMedia(sizeBytes, bytesToLowercaseHex(digest.digest()));
        }
        catch (MediaStorageException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw new MediaStorageException("Failed to store media at key: " + storageKey, exception);
        }
        finally {
            deleteTemporaryFile(temporary);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path destination = resolve(storageKey);
        try {
            verifyExistingControlledDirectory(root);
            verifyExistingControlledDirectory(destination.getParent());
            Files.deleteIfExists(destination);
        }
        catch (IOException exception) {
            throw new MediaStorageException("Failed to delete media at key: " + storageKey, exception);
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || !STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            throw new MediaStorageException("Invalid media storage key");
        }

        Path keyPath;
        try {
            keyPath = Path.of(storageKey);
        }
        catch (RuntimeException exception) {
            throw new MediaStorageException("Invalid media storage key", exception);
        }

        if (keyPath.isAbsolute()) {
            throw new MediaStorageException("Media storage key must be relative");
        }

        Path destination = root.resolve(keyPath).normalize();
        if (!destination.startsWith(root)) {
            throw new MediaStorageException("Media storage key resolves outside the configured root");
        }
        return destination;
    }

    private void createControlledDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        verifyExistingControlledDirectory(directory);
    }

    private void verifyExistingControlledDirectory(Path directory) {
        if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new MediaStorageException("Local media storage hierarchy must use directories");
        }
    }

    private void rejectExistingObject(Path destination) {
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new MediaStorageException("Media already exists at the requested storage key");
        }
    }

    private long write(InputStream source, Path temporary, MessageDigest digest) throws IOException {
        long sizeBytes = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (OutputStream output = Files.newOutputStream(
                temporary, StandardOpenOption.WRITE)) {
            int bytesRead;
            while ((bytesRead = source.read(buffer)) != -1) {
                if (bytesRead == 0) {
                    continue;
                }
                output.write(buffer, 0, bytesRead);
                digest.update(buffer, 0, bytesRead);
                sizeBytes += bytesRead;
            }
        }
        return sizeBytes;
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK SHA-256 implementation is unavailable", exception);
        }
    }

    private void moveCreateOnly(Path temporary, Path destination) throws IOException {
        try {
            Files.move(temporary, destination);
        }
        catch (FileAlreadyExistsException exception) {
            throw new MediaStorageException(
                    "Media already exists at the requested storage key", exception);
        }
    }

    private String bytesToLowercaseHex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private void deleteTemporaryFile(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        }
        catch (IOException exception) {
            // Preserve the storage failure that led here; the random .part file is never finalized.
        }
    }
}
