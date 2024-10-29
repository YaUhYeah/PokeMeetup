package io.github.pokemeetup.multiplayer.server.config;

public class ClientConfig {
    private String serverIP;
    private int tcpPort;
    private int udpPort;

    // Default constructor
    public ClientConfig() {}

    // Getters and Setters
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

    // Singleton Instance
    private static ClientConfig instance;

    public static synchronized ClientConfig getInstance() {
        if (instance == null) {
            instance = new ClientConfig();
        }
        return instance;
    }

    public static synchronized void setInstance(ClientConfig config) {
        instance = config;
    }

    /**
     * Provides default client configurations.
     *
     * @return A ClientConfig object with default settings.
     */
    public static ClientConfig getDefault() {
        ClientConfig defaultConfig = new ClientConfig();
        defaultConfig.setServerIP("localhost");
        defaultConfig.setTcpPort(54555); // Ensure this matches server's TCP port
        defaultConfig.setUdpPort(54556); // Ensure this matches server's UDP port
        return defaultConfig;
    }
}
