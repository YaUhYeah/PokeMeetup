// New ServerConfig.java
package io.github.pokemeetup.system.servers;

public class ServerConfig {
    private int port = 54555;
    private int maxPlayers = 20;
    private long worldSeed;
    private boolean allowNewRegistrations = true;

    // Add getters/setters
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    public long getWorldSeed() { return worldSeed; }
    public void setWorldSeed(long worldSeed) { this.worldSeed = worldSeed; }
    public boolean isAllowNewRegistrations() { return allowNewRegistrations; }
    public void setAllowNewRegistrations(boolean allow) { this.allowNewRegistrations = allow; }
}
