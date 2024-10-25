package io.github.pokemeetup.managers;

import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.serializers.ImmutableCollectionsSerializers;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Network {
    public static final int PORT = 54555;
    public static final String SERVER_IP = "127.0.0.1";
    public static final int UDP_PORT = 54777;  // UDP port


    public static void registerClasses(Kryo kryo) {
        kryo.register(LoginRequest.class);
        kryo.register(ImmutableCollectionsSerializers.class);
        kryo.register(List.class);
        kryo.register(InventoryUpdate.class);
        kryo.register(InventoryRequest.class);
        kryo.register(RegisterRequest.class);
        kryo.register(LoginResponse.class);
        kryo.register(PlayerUpdate.class);
        kryo.register(PlayerPosition.class);
        kryo.register(java.util.HashMap.class);
        kryo.register(WorldObjectUpdate.class);
        kryo.register(Vector2.class);
        kryo.register(ArrayList.class);
        kryo.register(WorldObject.class);
        kryo.register(WorldObject.ObjectType.class);
        kryo.register(PlayerJoined.class);
        kryo.register(PlayerLeft.class);
        kryo.register(java.util.ArrayList.class);
        // Register any other custom classes you use
    }

    public static class PlayerLeft {
        public String username;
    }
    public static class PlayerUpdate {
        public String username;
        public float x;
        public float y;
        public String worldName;
        public String direction;
        public boolean isMoving;
        public boolean wantsToRun;
        public List<String> inventoryItemNames = new ArrayList<>(); // Changed from array to List
    }

    public static class PlayerPosition {
        public HashMap<String, PlayerUpdate> players = new HashMap<>();
    }

    public static class LoginRequest {
        public String username;
        public String password;
    }

    public static class RegisterRequest {
        public String username;
        public String password;
    }

    public static class WorldObjectUpdate {
        public int chunkX;
        public int chunkY;
        public List<WorldObject> objects;
    }
    public static class LoginResponse {
        public boolean success;
        public boolean isRegistrationResponse;
        public String message;
        public int x;
        public int y;
        public long worldSeed;  // Add this field
        public String username; // Added username field
    }

    public static class PlayerJoined {
        public String username;
        public int x;
        public int y;
    }

    public static class NewPlayerConnected {
        public String username;
        public int x;
        public int y;
    }

    public static class PlayerDisconnected {
        public String username;
    }

    public static class PlayerSnapshot {
        public HashMap<String, PlayerUpdate> players;
    }

    public static class InventoryUpdate {
        public String username;
        public ArrayList itemNames = new ArrayList<>(); // Changed from array to List
    }


    public static class InventoryRequest {
        public String username;

        public InventoryRequest() {
        }
    }

}
