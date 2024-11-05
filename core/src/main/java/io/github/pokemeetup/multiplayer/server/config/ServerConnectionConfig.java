    package io.github.pokemeetup.multiplayer.server.config;

    // Create a new ServerConnectionConfig class

    import io.github.pokemeetup.utils.GameLogger;

    import java.io.IOException;

    public class ServerConnectionConfig {
        private static ServerConnectionConfig instance;
        private String serverIP;
        private int tcpPort;
        private int maxPlayers;
        private String dataDirectory;
        private int udpPort;
        private String serverName;
        private boolean isDefault;

        public ServerConnectionConfig(String serverIP, int tcpPort, int udpPort, String serverName, boolean isDefault, int maxPlayers) {
            this.serverIP = serverIP;
            this.tcpPort = tcpPort;
            this.udpPort = udpPort;
            this.maxPlayers = maxPlayers;
            this.serverName = serverName;
            this.isDefault = isDefault;
            setDataDirectory("assets/configs/");
        }

        public ServerConnectionConfig() {
            setDataDirectory("assets/configs/");
        }

        public static ServerConnectionConfig getInstance() {
            if (instance == null) {
                synchronized (ServerConnectionConfig.class) {
                    if (instance == null) {
                        // Default multiplayer configuration
                        instance = new ServerConnectionConfig(
                            "103.6.171.157", // Your server IP
                            54555,           // TCP Port
                            54556,           // UDP Port
                            "Default Server",
                            false,
                            100
                        );
                    }
                }
            }

            // Validate the instance
            try {
                validateServerConnection(instance);
            } catch (IOException e) {
                GameLogger.error("Server validation failed: " + e.getMessage());
                throw new RuntimeException("Server is not available: " + e.getMessage());
            }

            return instance;
        }

        private static void validateServerConnection(ServerConnectionConfig config) throws IOException {
            // Check if server is running
            try (java.net.Socket socket = new java.net.Socket()) {
                // Set a short timeout
                socket.connect(
                    new java.net.InetSocketAddress(config.getServerIP(), config.getTcpPort()),
                    2000
                );
            } catch (IOException e) {
                throw new IOException("Cannot connect to server at " +
                    config.getServerIP() + ":" + config.getTcpPort() +
                    " - " + e.getMessage());
            }
        }

        public void validate() {
            if (serverIP == null || serverIP.isEmpty()) {
                throw new IllegalArgumentException("Server IP cannot be empty");
            }
            if (tcpPort <= 0 || tcpPort > 65535) {
                throw new IllegalArgumentException("Invalid TCP port: " + tcpPort);
            }
            if (udpPort <= 0 || udpPort > 65535) {
                throw new IllegalArgumentException("Invalid UDP port: " + udpPort);
            }
            if (serverName == null || serverName.isEmpty()) {
                throw new IllegalArgumentException("Server name cannot be empty");
            }
            if (maxPlayers <= 0) {
                throw new IllegalArgumentException("Max players must be greater than 0");
            }
        }

        public static synchronized void setInstance(ServerConnectionConfig config) {
            instance = config;
        }

        public static ServerConnectionConfig getDefault() {
            return new ServerConnectionConfig("localhost", 55555, 55556, "Local Server", true, 100);
        }



        public int getMaxPlayers() {
            return maxPlayers;
        }

        public void setMaxPlayers(int maxPlayers) {
            this.maxPlayers = maxPlayers;
        }

        public String getDataDirectory() {
            return dataDirectory;
        }

        public void setDataDirectory(String dataDirectory) {
            this.dataDirectory = dataDirectory;
        }

        public String getServerIP() {
            return serverIP;
        }

        public void setServerIP(String serverIP) {
            this.serverIP = serverIP;
        }

        public int getTcpPort() {
            return tcpPort;
        }


        public void setTcpPort(int tcpPort) {
            this.tcpPort = tcpPort;
        }

        public int getUdpPort() {
            return udpPort;
        }


        public void setUdpPort(int udpPort) {
            this.udpPort = udpPort;
        }

        public String getServerName() {
            return serverName;
        }


        public void setServerName(String serverName) {
            this.serverName = serverName;
        }

        public boolean isDefault() {
            return isDefault;
        }

        public void setDefault(boolean aDefault) {
            isDefault = aDefault;
        }


        @Override
        public String toString() {
            return serverName + " (" + serverIP + ":" + tcpPort + ")";
        }
    }

