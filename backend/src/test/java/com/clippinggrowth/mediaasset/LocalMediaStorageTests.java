package com.clippinggrowth.mediaasset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalMediaStorageTests {

    @TempDir
    private Path root;

    @Test
    void storesExactBytesAcrossMultipleReadsAndReturnsAuthoritativeMetadata() throws Exception {
        byte[] content = generatedContent(24_617);
        UUID mediaAssetId = UUID.fromString("7A4C2F10-8C2B-4A3D-9E11-123456789ABC");
        String storageKey = MediaAssetStorageKey.forId(mediaAssetId);
        LocalMediaStorage storage = new LocalMediaStorage(root);

        StoredMedia stored = storage.store(storageKey, new ByteArrayInputStream(content));

        Path storedPath = root.resolve("media-assets/7a4c2f10-8c2b-4a3d-9e11-123456789abc");
        assertThat(storedPath).hasBinaryContent(content);
        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(stored.sha256()).isEqualTo(sha256(content));
        assertThat(stored.sha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void rejectsZeroBytesWithoutLeavingFinalOrTemporaryFiles() throws Exception {
        String storageKey = MediaAssetStorageKey.forId(UUID.randomUUID());
        LocalMediaStorage storage = new LocalMediaStorage(root);

        assertThatThrownBy(() -> storage.store(storageKey, InputStream.nullInputStream()))
                .isInstanceOf(MediaStorageException.class)
                .hasMessageContaining("must not be empty");

        assertThat(root.resolve(storageKey)).doesNotExist();
        assertThat(partFiles()).isEmpty();
    }

    @Test
    void createsTheControlledNestedDirectoryForAGeneratedKey() {
        UUID mediaAssetId = UUID.randomUUID();
        String storageKey = MediaAssetStorageKey.forId(mediaAssetId);
        LocalMediaStorage storage = new LocalMediaStorage(root);

        storage.store(storageKey, new ByteArrayInputStream(new byte[] {1}));

        assertThat(storageKey).isEqualTo("media-assets/" + mediaAssetId.toString().toLowerCase());
        assertThat(root.resolve("media-assets")).isDirectory();
        assertThat(root.resolve(storageKey)).exists();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../media-assets/00000000-0000-0000-0000-000000000001",
            "media-assets/../00000000-0000-0000-0000-000000000001",
            "media-assets/../../outside",
            "..\\media-assets\\00000000-0000-0000-0000-000000000001",
            "media-assets\\..\\00000000-0000-0000-0000-000000000001"
    })
    void rejectsTraversalAndBackslashTraversal(String storageKey) {
        LocalMediaStorage storage = new LocalMediaStorage(root);

        assertThatThrownBy(() -> storage.store(
                storageKey, new ByteArrayInputStream(new byte[] {1})))
                .isInstanceOf(MediaStorageException.class)
                .hasMessageContaining("Invalid media storage key");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/media-assets/00000000-0000-0000-0000-000000000001",
            "C:/media-assets/00000000-0000-0000-0000-000000000001",
            "C:\\media-assets\\00000000-0000-0000-0000-000000000001"
    })
    void rejectsAbsoluteKeys(String storageKey) {
        LocalMediaStorage storage = new LocalMediaStorage(root);

        assertThatThrownBy(() -> storage.store(
                storageKey, new ByteArrayInputStream(new byte[] {1})))
                .isInstanceOf(MediaStorageException.class);
    }

    @Test
    void collisionNeverOverwritesAnExistingObject() throws Exception {
        String storageKey = MediaAssetStorageKey.forId(UUID.randomUUID());
        Path destination = root.resolve(storageKey);
        Files.createDirectories(destination.getParent());
        byte[] original = new byte[] {1, 2, 3};
        Files.write(destination, original);
        LocalMediaStorage storage = new LocalMediaStorage(root);

        assertThatThrownBy(() -> storage.store(
                storageKey, new ByteArrayInputStream(new byte[] {9, 8, 7})))
                .isInstanceOf(MediaStorageException.class)
                .hasMessageContaining("already exists");

        assertThat(destination).hasBinaryContent(original);
        assertThat(partFiles()).isEmpty();
    }

    @Test
    void concurrentSameKeyStoresProduceOneWinnerWithoutOverwritingIt() throws Exception {
        String storageKey = MediaAssetStorageKey.forId(UUID.randomUUID());
        byte[] contentA = generatedContent(24_617);
        byte[] contentB = generatedContent(24_619);
        contentB[0] = 99;
        LocalMediaStorage storage = new LocalMediaStorage(root);
        CyclicBarrier bothStoresReadyToFinalize = new CyclicBarrier(2);

        List<StoreAttempt> attempts;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<StoreAttempt> futureA = executor.submit(() -> attemptStore(
                    storage,
                    storageKey,
                    contentA,
                    new CoordinatedInputStream(contentA, bothStoresReadyToFinalize)));
            Future<StoreAttempt> futureB = executor.submit(() -> attemptStore(
                    storage,
                    storageKey,
                    contentB,
                    new CoordinatedInputStream(contentB, bothStoresReadyToFinalize)));
            attempts = List.of(
                    futureA.get(10, TimeUnit.SECONDS),
                    futureB.get(10, TimeUnit.SECONDS));
        }

        List<StoreAttempt> successes = attempts.stream()
                .filter(attempt -> attempt.failure() == null)
                .toList();
        List<StoreAttempt> failures = attempts.stream()
                .filter(attempt -> attempt.failure() != null)
                .toList();

        assertThat(successes).hasSize(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst().failure())
                .hasMessageContaining("already exists");
        assertThat(root.resolve(storageKey)).hasBinaryContent(successes.getFirst().content());
        assertThat(partFiles()).isEmpty();
    }

    @Test
    void ordinaryStreamFailureRemovesTheTemporaryFile() throws Exception {
        String storageKey = MediaAssetStorageKey.forId(UUID.randomUUID());
        LocalMediaStorage storage = new LocalMediaStorage(root);
        InputStream failingSource = new InputStream() {
            private boolean firstRead = true;

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                if (!firstRead) {
                    throw new IOException("simulated read failure");
                }
                firstRead = false;
                buffer[offset] = 42;
                return 1;
            }

            @Override
            public int read() throws IOException {
                throw new IOException("single-byte reads are not expected");
            }
        };

        assertThatThrownBy(() -> storage.store(storageKey, failingSource))
                .isInstanceOf(MediaStorageException.class)
                .hasMessageContaining("Failed to store media");

        assertThat(root.resolve(storageKey)).doesNotExist();
        assertThat(partFiles()).isEmpty();
    }

    @Test
    void deleteRemovesAnExistingObjectAndIsIdempotentWhenMissing() {
        String storageKey = MediaAssetStorageKey.forId(UUID.randomUUID());
        LocalMediaStorage storage = new LocalMediaStorage(root);
        storage.store(storageKey, new ByteArrayInputStream(new byte[] {1, 2, 3}));

        storage.delete(storageKey);
        storage.delete(storageKey);

        assertThat(root.resolve(storageKey)).doesNotExist();
    }

    @Test
    void storedObjectPersistsAcrossStorageReconstruction() {
        String storageKey = MediaAssetStorageKey.forId(UUID.randomUUID());
        byte[] content = generatedContent(10_000);
        LocalMediaStorage firstStorage = new LocalMediaStorage(root);
        firstStorage.store(storageKey, new ByteArrayInputStream(content));

        LocalMediaStorage secondStorage = new LocalMediaStorage(root);
        assertThat(root.resolve(storageKey)).hasBinaryContent(content);
        secondStorage.delete(storageKey);

        assertThat(root.resolve(storageKey)).doesNotExist();
    }

    private Stream<Path> partFiles() throws IOException {
        if (Files.notExists(root.resolve("media-assets"))) {
            return Stream.empty();
        }
        try (Stream<Path> files = Files.list(root.resolve("media-assets"))) {
            return files.filter(path -> path.getFileName().toString().endsWith(".part")).toList()
                    .stream();
        }
    }

    private byte[] generatedContent(int size) {
        byte[] content = new byte[size];
        for (int index = 0; index < content.length; index++) {
            content[index] = (byte) (index * 31);
        }
        return content;
    }

    private String sha256(byte[] content) throws NoSuchAlgorithmException {
        return java.util.HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private StoreAttempt attemptStore(
            LocalMediaStorage storage,
            String storageKey,
            byte[] content,
            InputStream source) {
        try {
            return new StoreAttempt(content, storage.store(storageKey, source), null);
        }
        catch (MediaStorageException exception) {
            return new StoreAttempt(content, null, exception);
        }
    }

    private record StoreAttempt(
            byte[] content, StoredMedia storedMedia, MediaStorageException failure) {
    }

    private static final class CoordinatedInputStream extends InputStream {

        private final ByteArrayInputStream delegate;
        private final CyclicBarrier bothStoresReadyToFinalize;
        private boolean coordinated;

        private CoordinatedInputStream(byte[] content, CyclicBarrier bothStoresReadyToFinalize) {
            this.delegate = new ByteArrayInputStream(content);
            this.bothStoresReadyToFinalize = bothStoresReadyToFinalize;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            coordinateAtEnd(read);
            return read;
        }

        @Override
        public int read() throws IOException {
            int read = delegate.read();
            coordinateAtEnd(read);
            return read;
        }

        private void coordinateAtEnd(int read) throws IOException {
            if (read != -1 || coordinated) {
                return;
            }
            coordinated = true;
            try {
                bothStoresReadyToFinalize.await(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while coordinating concurrent finalization", exception);
            }
            catch (BrokenBarrierException | TimeoutException exception) {
                throw new IOException("Failed to coordinate concurrent finalization", exception);
            }
        }
    }
}
