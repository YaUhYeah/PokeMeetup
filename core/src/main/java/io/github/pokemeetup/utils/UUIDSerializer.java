package io.github.pokemeetup.utils;

import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.UUID;

/**
 * Custom Kryo serializer for java.util.UUID.
 */
public class UUIDSerializer extends Serializer<UUID> {
    @Override
    public void write(Kryo kryo, Output output, UUID uuid) {
        output.writeLong(uuid.getMostSignificantBits());
        output.writeLong(uuid.getLeastSignificantBits());
    }

    @Override
    public UUID read(Kryo kryo, Input input, Class<UUID> type) {
        long mostSigBits = input.readLong();
        long leastSigBits = input.readLong();
        return new UUID(mostSigBits, leastSigBits);
    }
}