package de.maxhenkel.voicechat.voice.client;


import de.maxhenkel.voicechat.Main;
import de.maxhenkel.voicechat.voice.common.AuthenticatePacket;
import de.maxhenkel.voicechat.voice.common.KeepAlivePacket;
import de.maxhenkel.voicechat.voice.common.NetworkMessage;
import de.maxhenkel.voicechat.voice.common.Utils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Client extends Thread {

    private Socket socket;
    private ObjectInputStream fromServer;
    private ObjectOutputStream toServer;
    private List<AudioChannel> audioChannels;
    private MicThread micThread;
    private boolean running;

    public Client(String serverIp, int serverPort) throws IOException {
        this.socket = new Socket(serverIp, serverPort);
        this.fromServer = new ObjectInputStream(socket.getInputStream());
        this.toServer = new ObjectOutputStream(socket.getOutputStream());
        this.audioChannels = new ArrayList<>();
        this.running = true;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            try {
                micThread = new MicThread(toServer);
                micThread.start();
            } catch (Exception e) {
                Main.LOGGER.error("Mic unavailable " + e);
            }
            while (running) {
                if (socket.getInputStream().available() > 0) {
                    NetworkMessage<?> in = (NetworkMessage<?>) (fromServer.readObject());
                    if (in.getData() instanceof KeepAlivePacket) { //TODO more elegant solution
                        continue;
                    }
                    AudioChannel sendTo = audioChannels.stream().filter(audioChannel -> audioChannel.getUUID().equals(in.getPlayerUUID())).findFirst().orElse(null); //TODO to map
                    if (sendTo == null) {
                        AudioChannel ch = new AudioChannel(in.getPlayerUUID());
                        ch.addToQueue(in);
                        ch.start();
                        audioChannels.add(ch);
                    } else {
                        sendTo.addToQueue(in);
                    }
                } else {
                    audioChannels.stream().filter(AudioChannel::canKill).forEach(AudioChannel::closeAndKill);
                    audioChannels.removeIf(AudioChannel::canKill);
                    Utils.sleep(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void authenticate(UUID playerUUID, UUID secret) {
        try {
            toServer.writeObject(new NetworkMessage<>(new AuthenticatePacket(playerUUID, secret)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        Main.LOGGER.info("Disconnecting client");
        running = false;
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        micThread.close();
    }

    public boolean isConnected() {
        return running && !socket.isClosed();
    }
}
 