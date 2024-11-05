package io.github.pokemeetup.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.sun.jna.platform.mac.MacFileUtils;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.PointerByReference;

import java.io.IOException;

public class LibGDXFileManager implements FileManager {
    @Override
    public void writeString(String path, String data) throws IOException {
        FileHandle file = Gdx.files.local(path);
        file.writeString(data, false);
    }

    @Override
    public String readString(String path) throws IOException {
        FileHandle file = Gdx.files.local(path);
        return file.readString();
    }

    @Override
    public boolean exists(String path) {
        FileHandle file = Gdx.files.local(path);
        return file.exists();
    }

    @Override
    public void createDirectories(String path) throws IOException {
        FileHandle dir = Gdx.files.local(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    @Override
    public void deleteFile(String path) throws IOException {
        FileHandle file = Gdx.files.local(path);
        if (file.exists()) {
            file.delete();
        }
    }
}
