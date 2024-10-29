package io.github.pokemeetup.managers;

import com.esotericsoftware.kryonet.Client;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.config.ServerConfig;

import java.io.IOException;

public class ServerStatusChecker {
    public interface ServerStatusListener {
        void onServerStatusChecked(boolean isAvailable);
    }

    /**
     * Checks the availability of the server using configurations from ServerConfig.
     *
     * @param listener The listener to notify about the server status.
     */
    public static void checkServerAvailability(ServerStatusListener listener) {
        new Thread(() -> {
            Client client = new Client();
            try {
                NetworkProtocol.registerClasses(client.getKryo());
                client.start();
                ServerConfig config = ServerConfig.getInstance();
                client.connect(3000, config.getServerIP(), config.getTcpPort(), config.getUdpPort()); // 3000 ms timeout
                client.stop();
                listener.onServerStatusChecked(true);
            } catch (IOException e) {
                client.stop();
                listener.onServerStatusChecked(false);
            }
        }).start();
    }
}
