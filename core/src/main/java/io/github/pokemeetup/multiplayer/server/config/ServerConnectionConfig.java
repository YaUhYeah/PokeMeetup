package io.github.pokemeetup.multiplayer.server.config;

// Create a new ServerConnectionConfig class

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

    public static synchronized ServerConnectionConfig getInstance() {
        if (instance == null) {
            instance = getDefault();
        }
        return instance;
    }

    public static synchronized void setInstance(ServerConnectionConfig config) {
        instance = config;
    }

    public static ServerConnectionConfig getDefault() {
        return new ServerConnectionConfig("localhost", 55555, 55556, "Local Server", true, 100);
    }

    public void validate() throws IllegalArgumentException {
        if (serverIP == null || serverIP.trim().isEmpty()) {
            throw new IllegalArgumentException("Server IP cannot be empty");
        }
        if (tcpPort <= 0 || tcpPort > 65535) {
            throw new IllegalArgumentException("Invalid TCP port");
        }
        if (udpPort <= 0 || udpPort > 65535) {
            throw new IllegalArgumentException("Invalid UDP port");
        }
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

