package io.github.pokemeetup.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;

public class JavaFileManager implements FileManager {
    @Override
    public void writeString(String path, String data) throws IOException {
        Path filePath = Paths.get(path);
        Files.write(filePath, data.getBytes());
    }

    @Override
    public String readString(String path) throws IOException {
        Path filePath = Paths.get(path);
        return new String(Files.readAllBytes(filePath));
    }

    @Override
    public boolean exists(String path) {
        Path filePath = Paths.get(path);
        return Files.exists(filePath);
    }

    @Override
    public void createDirectories(String path) throws IOException {
        Path dirPath = Paths.get(path);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
    }

    @Override
    public void deleteFile(String path) throws IOException {
        Path filePath = Paths.get(path);
        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }
    }

    // Implement other methods as needed
}
