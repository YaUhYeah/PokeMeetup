    package io.github.pokemeetup.multiplayer.client;

    import io.github.pokemeetup.managers.Network;

    import java.io.IOException;
    import java.util.List;
    import java.util.Map;
    import java.util.concurrent.ConcurrentHashMap;

    public class GameClientSingleton {
        private static GameClient instance;
        private static DummyGameClient singlePlayerInstance;

        // Private constructor to prevent instantiation
        private GameClientSingleton() {}

        public static synchronized GameClient getInstance() throws IOException {
            if (instance == null) {
                instance = new GameClient(false); // false for multiplayer mode
            }
            return instance;
        }

        public static synchronized GameClient getSinglePlayerInstance() throws IOException {
            if (singlePlayerInstance == null) {
                singlePlayerInstance = new DummyGameClient();
            }
            return singlePlayerInstance;
        }

        public static synchronized void dispose() {
            if (instance != null) {
                instance.dispose();
                instance = null;
            }
            if (singlePlayerInstance != null) {
                singlePlayerInstance.dispose();
                singlePlayerInstance = null;
            }
        }

        private static class DummyGameClient extends GameClient {
            public DummyGameClient() throws IOException {
                super(true); // true for single player mode
            }

            @Override
            public void sendPlayerUpdate(Network.PlayerUpdate update) {
                // Do nothing in single player
            }

            @Override
            public void sendInventoryUpdate(String username, List<String> itemNames) {
                // Do nothing in single player
            }

            @Override
            public void dispose() {
                super.dispose();
            }

            @Override
            public Map<String, Network.PlayerUpdate> getOtherPlayers() {
                return new ConcurrentHashMap<>(); // Return empty map for single player
            }
        }
    }
