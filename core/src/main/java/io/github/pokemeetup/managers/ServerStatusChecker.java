package io.github.pokemeetup.managers;

import com.esotericsoftware.kryonet.Client;

import java.io.IOException;

public class ServerStatusChecker {
    public interface ServerStatusListener {
        void onServerStatusChecked(boolean isAvailable);
    }

    public static void checkServerAvailability(ServerStatusListener listener) {
        new Thread(() -> {
            Client client = new Client();
            try {
                Network.registerClasses(client.getKryo());
                client.start();
                client.connect(3000, Network.SERVER_IP, Network.PORT, Network.UDP_PORT); // 3000 ms timeout
                client.stop();
                listener.onServerStatusChecked(true);
            } catch (IOException e) {
                client.stop();
                listener.onServerStatusChecked(false);
            }
        }).start();
    }
}
