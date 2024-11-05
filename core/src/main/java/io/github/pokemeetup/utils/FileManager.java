package io.github.pokemeetup.utils;

import java.io.IOException;

public interface FileManager {
    void writeString(String path, String data) throws IOException;
    String readString(String path) throws IOException;
    boolean exists(String path);
    void createDirectories(String path) throws IOException;
    void deleteFile(String path) throws IOException;
    // Add other necessary file operations
}
