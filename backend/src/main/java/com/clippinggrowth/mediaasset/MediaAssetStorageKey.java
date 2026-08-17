package com.clippinggrowth.mediaasset;

import java.util.Objects;
import java.util.UUID;

public final class MediaAssetStorageKey {

    private MediaAssetStorageKey() {
    }

    public static String forId(UUID mediaAssetId) {
        return "media-assets/" + Objects.requireNonNull(mediaAssetId, "mediaAssetId");
    }
}
