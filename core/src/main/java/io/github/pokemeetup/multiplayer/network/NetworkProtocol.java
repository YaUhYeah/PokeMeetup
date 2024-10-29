package io.github.pokemeetup.multiplayer.network;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.esotericsoftware.kryo.Kryo;
import io.github.pokemeetup.multiplayer.server.entity.CreatureEntity;
import io.github.pokemeetup.multiplayer.server.entity.Entity;
import io.github.pokemeetup.multiplayer.server.entity.EntityType;
import io.github.pokemeetup.multiplayer.server.entity.PokeballEntity;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.utils.UUIDSerializer;

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

        // Enums
        kryo.register(NetworkObjectUpdateType.class);
        kryo.register(NetworkedWorldObject.ObjectType.class);
        kryo.register(ChatType.class);

        // Request and response classes
        kryo.register(LoginRequest.class);
        kryo.register(LoginResponse.class);
        kryo.register(RegisterRequest.class);
        kryo.register(RegisterResponse.class);

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

        // Networked entities
        kryo.register(NetworkedWorldObject.class);
        kryo.register(NetworkedTree.class);
        kryo.register(NetworkedPokeball.class);

        // Miscellaneous
        kryo.register(ServerShutdown.class);
        kryo.register(ChatMessage.class);

        // Additional Entity subclasses
        kryo.register(CreatureEntity.class);
        kryo.register(PokeballEntity.class);
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
    public static class ServerShutdown {
        public String message;
    }

    public static class PlayerJoined {
        public String username;
        public float x;
        public float y;
        public String direction = "down";
        public boolean isMoving = false;
        public List<String> inventoryItemNames = new ArrayList<>();
    }

    public static class PlayerLeft {
        public String username;
    }

    // Update existing classes with timestamps
    public static class PlayerUpdate {
        public String username;
        public float x;
        public float y;
        public String direction;
        public boolean isMoving;
        public boolean wantsToRun;
        public List<String> inventoryItemNames = new ArrayList<>();
        public long timestamp = System.currentTimeMillis();
    }
    // Request and Response Classes

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

    public static class InventoryUpdate {
        public String username;
        public ArrayList<String> itemNames = new ArrayList<>(); // Changed from raw ArrayList to ArrayList<String>
    }

    // Update the PlayerUpdate class in NetworkProtocol.java

    public static class LoginResponse {
        public boolean success;
        public String message;
        public int x;
        public int y;
        public long worldSeed;  // Add this field
        public String username; // Added username field
    }

    public static class PlayerPosition {
        public HashMap<String, PlayerUpdate> players = new HashMap<>();
    }

    public static class ChatMessage {
        public String sender;
        public String content;
        public long timestamp;
        public ChatType type = ChatType.NORMAL;
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
        public List<String> inventory;
    }

    public static class EntityUpdate {
        public UUID entityId;
        public float x;
        public float y;
        public Vector2 velocity;
        public String entityType;
    }
}
