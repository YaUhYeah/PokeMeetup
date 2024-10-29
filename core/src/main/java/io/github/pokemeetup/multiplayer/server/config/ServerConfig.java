package io.github.pokemeetup.multiplayer.server.config;

public class ServerConfig {
    private int tcpPort;
    private String serverIP; // Typically "localhost" or specific server IP
    private int udpPort;
    private int maxPlayers;
    private long worldSeed;
    private String dataDirectory;

    private static ServerConfig instance;

    // Default constructor (required for JSON deserialization)
    public ServerConfig() {
        this.worldSeed = System.currentTimeMillis();
    }

    // Getters and Setters
    public int getTcpPort() {
        return tcpPort;
    }

    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    public String getServerIP() {
        return serverIP;
    }

    public void setServerIP(String serverIP) {
        this.serverIP = serverIP;
    }

    public int getUdpPort() {
        return udpPort;
    }

    public void setUdpPort(int udpPort) {
        this.udpPort = udpPort;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public long getWorldSeed() {
        return worldSeed;
    }

    public void setWorldSeed(long worldSeed) {
        this.worldSeed = worldSeed;
    }

    public String getDataDirectory() {
        return dataDirectory;
    }

    public void setDataDirectory(String dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public static synchronized ServerConfig getInstance() {
        if (instance == null) {
            instance = new ServerConfig();
        }
        return instance;
    }

    public static synchronized void setInstance(ServerConfig config) {
        instance = config;
    }

    /**
     * Provides default server configurations.
     *
     * @return A ServerConfig object with default settings.
     */
    public static ServerConfig getDefault() {
        ServerConfig defaultConfig = new ServerConfig();
        defaultConfig.setServerIP("localhost");
        defaultConfig.setTcpPort(54555);
        defaultConfig.setUdpPort(54556);
        defaultConfig.setMaxPlayers(100);
        defaultConfig.setDataDirectory("data"); // Default data directory
        return defaultConfig;
    }
}
