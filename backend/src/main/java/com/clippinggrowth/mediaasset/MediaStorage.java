package com.clippinggrowth.mediaasset;

import java.io.InputStream;

public interface MediaStorage {

    StoredMedia store(String storageKey, InputStream source);

    void delete(String storageKey);
}
