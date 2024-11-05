package io.github.pokemeetup.multiplayer.network;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.github.pokemeetup.multiplayer.server.entity.CreatureEntity;
import io.github.pokemeetup.multiplayer.server.entity.Entity;
import io.github.pokemeetup.multiplayer.server.entity.EntityType;
import io.github.pokemeetup.multiplayer.server.entity.PokeballEntity;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.system.data.ItemData;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.UUIDSerializer;

import java.io.Serializable;
import java.util.*;

public class NetworkProtocol {
    public static void registerClasses(Kryo kryo) {
        // Basic and commonly used classes
        kryo.register(UUID.class, new UUIDSerializer());
        kryo.register(Vector2.class);
        kryo.register(ArrayList.class);
        kryo.register(List.class);
        kryo.register(HashMap.class);
        kryo.register(Map.class);
        kryo.register(java.util.concurrent.ConcurrentHashMap.class);


        // Enums
        kryo.register(NetworkObjectUpdateType.class);
        kryo.register(NetworkedWorldObject.ObjectType.class);
        kryo.register(ChatType.class);

        // Request and response classes
        kryo.register(LoginRequest.class);
        kryo.register(LoginResponse.class);
        kryo.register(RegisterRequest.class);
        kryo.register(RegisterResponse.class);
        kryo.register(ItemData.class);
        kryo.register(ItemData[].class);
        kryo.register(UUID.class);
        kryo.register(InventoryUpdate.class);
        // Game state and network classes
        kryo.register(PlayerPosition.class);
        kryo.register(PlayerUpdate.class);
        kryo.register(InventoryUpdate.class);
        kryo.register(PlayerJoined.class);
        kryo.register(PlayerLeft.class);
        kryo.register(WorldObjectUpdate.class);

        // Complex data models and entities
        kryo.register(WorldState.class);
        kryo.register(PlayerState.class);
        kryo.register(EntityUpdate.class);
        kryo.register(Entity.class);
        kryo.register(EntityType.class);
        registerPokemonClasses(kryo);
        // Networked entities
        kryo.register(NetworkedWorldObject.class);
        kryo.register(NetworkedTree.class);
        kryo.register(NetworkedPokeball.class);
        kryo.register(ConnectionResponse.class);
        kryo.register(ConnectionRequest.class);
        kryo.register(ConnectionStatus.class);

        kryo.register(UsernameCheckRequest.class);
        kryo.register(UsernameCheckResponse.class);
        kryo
            .register(io.github.pokemeetup.system.data.WorldData.class);
        kryo
            .register(io.github.pokemeetup.system.data.PlayerData.class);
        // Miscellaneous
        kryo.register(io.github.pokemeetup.system.data.WorldData.WorldConfig.class);

        kryo.register(ChunkUpdate.class);
        kryo.register(TeamCreate.class);

        kryo.register(ServerShutdown.class);
        kryo.register(TeleportRequest.class);
        kryo.register(TeleportResponse.class);
        kryo.register(World.WorldObjectData.class);
        kryo.register(World.ChunkData.class);
        kryo.register(ChatMessage.class);
        kryo.register(TeamInvite.class);
        kryo.register(TeamHQUpdate.class);
        // Additional Entity subclasses
        kryo.register(CreatureEntity.class);
        kryo.register(PokeballEntity.class);
        kryo.register(Object.class);

        kryo.register(UUID.class, new com.esotericsoftware.kryo.Serializer<UUID>() {

            @Override
            public void write(Kryo kryo, Output output, UUID uuid) {
                output.writeLong(uuid.getMostSignificantBits());
                output.writeLong(uuid.getLeastSignificantBits());
            }

            @Override
            public UUID read(Kryo kryo, Input input, Class<UUID> type) {
                return new UUID(input.readLong(), input.readLong());
            }
        });
    }

    public static void registerPokemonClasses(Kryo kryo) {
        kryo.register(PokemonUpdate.class);
        kryo.register(PokemonSpawn.class);
        kryo.register(PokemonDespawn.class);
        kryo.register(PokemonSpawnRequest.class);
        kryo.register(PartyUpdate.class);
        kryo.register(WildPokemonSpawn.class);
        kryo.register(WildPokemonDespawn.class);
        kryo.register(PokemonData.class);
        kryo.register(Pokemon.PokemonType.class);
        kryo.register(ArrayList.class);
        kryo.register(int[].class);
    }

    public enum ChatType {
        NORMAL,
        SYSTEM,
        WHISPER,
        TEAM
    }

    public enum NetworkObjectUpdateType {
        ADD,
        UPDATE,
        REMOVE

    }

    public static class ConnectionResponse {
        public boolean success;
        public String message;
    }

    public static class ConnectionRequest {
        public String version;
        public long timestamp;
    }

    public static class ConnectionStatus {
        public int connectionId;
        public String status;
        public long timestamp;
    }

    public static class PokemonUpdate implements Serializable {
        public UUID uuid;
        public float x;
        public float y;
        public String direction;
        public boolean isMoving;
        public boolean isAttacking;
        public PokemonData data;
        public long timestamp;
        public String status;  // e.g., "NORMAL", "FAINTED", "SLEEPING"
        public Map<String, Object> extraData = new HashMap<>();  // For extensibility
        public int level;
        public float currentHp;
    }

    public static class PokemonSpawnRequest implements Serializable {
        public UUID uuid;
        public long timestamp = System.currentTimeMillis();
        public String requestingPlayer; // Username of requesting client
    }

    public static class ChunkUpdate {
        public Vector2 position;
        public World.ChunkData chunkData;
        public List<World.WorldObjectData> objects;
    }

    public static class LoginResponse {
        public boolean success;
        public String message;
        public String username;
        public int x;
        public int y;
        public String worldName;
        public long worldSeed;
        public io.github.pokemeetup.system.data.WorldData worldData;  // Add serializable world data
    }

    public static class WorldData implements Serializable {
        // Add fields that need to be synced to clients
        public long seed;
        public String name;
        public Map<String, Object> worldProperties;
        // Add other necessary world data
    }

    public static class UsernameCheckRequest {
        public String username;
        public long timestamp;
    }

    public static class UsernameCheckResponse {
        public String username;
        public boolean available;
        public String message;
    }

    public static class ServerShutdown {
        public String message;
    }

    public static class PlayerJoined {
        public String username;
        public float x;
        public float y;
        public String direction = "down";
        public boolean isMoving = false;
        public ItemData[] inventoryItems;
        public ItemData[] hotbarItems;
    }

    public static class PlayerLeft {
        public String username;
    }
    // Request and Response Classes

    // Update existing classes with timestamps
    public static class PlayerUpdate {
        public String username;
        public float x;
        public float y;
        public String direction;
        public boolean isMoving;
        public boolean wantsToRun;
        public ItemData[] inventoryItems;
        public ItemData[] hotbarItems;
        public long timestamp = System.currentTimeMillis();
    }

    // Add validation methods to request classes
    public static class LoginRequest {
        public String username;
        public String password;

        public void validate() throws IllegalArgumentException {
            if (username == null || username.trim().isEmpty()) {
                throw new IllegalArgumentException("Username cannot be empty");
            }
            if (password == null || password.trim().isEmpty()) {
                throw new IllegalArgumentException("Password cannot be empty");
            }
        }
    }

    public static class RegisterRequest {
        public String username;
        public String password;
    }
    // Update the PlayerUpdate class in NetworkProtocol.java

    public static class InventoryUpdate {
        public String username;
        public ItemData[] inventoryItems;
    }


    public static class PlayerPosition {
        public HashMap<String, PlayerUpdate> players = new HashMap<>();
    }

    public static class ChatMessage {
        public String sender;
        public String content;
        public long timestamp;
        public ChatType type;
        public String recipient; // Add this field for private messages
    }

    public static class WeatherUpdate implements Serializable {
        public String weatherType;  // e.g., "RAIN", "CLEAR", "SNOW"
        public float intensity;     // 0.0 to 1.0
        public long duration;       // in milliseconds
        public long timestamp;
    }

    public static class TimeSync implements Serializable {
        public double worldTimeInMinutes;
        public long playedTime;
        public float dayLength;
        public long timestamp;
    }

    public static class WorldStateUpdate implements Serializable {
        public io.github.pokemeetup.system.data.WorldData worldData;
        public long timestamp;
        public Map<String, Object> extraData = new HashMap<>();  // For additional sync data
    }

    public static class TeamCreate {
        public String name;
        public String tag;
        public String leader;
        public long timestamp = System.currentTimeMillis();
    }

    public static class TeamInvite {
        public String teamName;
        public String inviter;
        public String invitee;
        public long timestamp = System.currentTimeMillis();
    }

    public static class TeamHQUpdate {
        public String teamName;
        public int x;
        public int y;
        public long timestamp = System.currentTimeMillis();
    }

    public static class PokemonSpawn {
        public UUID uuid;
        public String name;
        public int level;
        public float x;
        public float y;
        public PokemonData data;
        public long timestamp;
    }

    public static class PokemonDespawn {
        public UUID uuid;
        public long timestamp;
    }

    public static class PartyUpdate {
        public String username;
        public List<PokemonData> party;
        public long timestamp;
    }


    // Add to NetworkProtocol.java
    public static class TeleportRequest {
        public TeleportType type;
        public String player;
        public String target;  // For player teleports
        public String homeName;  // For home teleports
        public long timestamp;
        public enum TeleportType {
            SPAWN, HOME, PLAYER
        }
    }

    public static class WildPokemonSpawn {
        public UUID uuid;
        public float x;
        public float y;
        public PokemonData data;
        public long timestamp;
    }

    public static class WildPokemonDespawn {
        public UUID uuid;
        public long timestamp;
    }

    public static class RegisterResponse {
        public boolean success;
        public String message;
        public int x;
        public int y;
        public long worldSeed;  // Add this field
        public String username; // Added username field
    }

    public static class WorldObjectUpdate {
        public String objectId;
        public NetworkObjectUpdateType type;
        public float x;
        public float y;
        public String textureName;
        public NetworkedWorldObject.ObjectType objectType;
        public Map<String, Object> data;
    }

    // World State Classes
    public static class WorldState {
        public long timestamp;
        public List<EntityUpdate> entities;
        public List<PlayerState> players;
    }

    public static class PlayerState {
        public String username;
        public float x;
        public float y;
        public String direction;
        public boolean isMoving;
        public List<ItemData> inventory;
    }

    public static class EntityUpdate {
        public UUID entityId;
        public float x;
        public float y;
        public Vector2 velocity;
        public String entityType;
    }

    public class TeleportResponse {
        public String from;
        public String to;
        public boolean accepted;
        public long timestamp;
    }
}
