package com.mancersoft.litevpnserver.transport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mancersoft.litevpnserver.VpnManager;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import trikita.log.Log;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.mancersoft.litevpnserver.VpnManager.TAG;

public class WebSocketTransport implements IVpnTransport {

    private static final String SERVER_URI = "wss://cloud.achex.ca";

    private final String mServerId;
    private URI serverUri;
    private MessageListener mMessageListener;
    private WSClient mWebSocketClient;

    public WebSocketTransport(String serverId) {
        mServerId = serverId;
        try {
            serverUri = new URI(SERVER_URI);
        } catch (Exception e) {
            Log.e(TAG, "WebSocketTransport URI error", e);
        }
    }

    @Override
    public boolean isReliable() {
        return true;
    }

    @Override
    public CompletableFuture<Boolean> start() {
        stop();
        mWebSocketClient = new WSClient(serverUri);
        return mWebSocketClient.connectAsync();
    }

    @Override
    public void stop() {
        if (mWebSocketClient != null) {
            mWebSocketClient.disconnect();
        }
    }

    private void restart() {
        Log.d(TAG, "WebSocketTransport restarting...");
        start();
    }

    @Override
    public void sendAsync(Packet packet) {
        var encoded = Base64.getEncoder().encode(ByteBuffer.wrap(packet.getData(), 0, packet.getLength()));
        String dataString = new String(encoded.array(), StandardCharsets.ISO_8859_1);
        var jObj = new JsonObject();
        jObj.addProperty("to", packet.getDestination().toString());
        jObj.addProperty("data", dataString);
        mWebSocketClient.sendAsync(jObj.toString());
    }

    @Override
    public void setOnMessageListener(MessageListener messageListener) {
        mMessageListener = messageListener;
    }

    private class WSClient extends WebSocketClient {

        private CompletableFuture<Boolean> mConnectResult;

        public WSClient(URI serverUri) {
            super(serverUri);
        }

        public CompletableFuture<Boolean> connectAsync() {
            mConnectResult = new CompletableFuture<>();
            super.connect();
            return mConnectResult;
        }

        public void disconnect() {
            super.close();
        }

        @Override
        public void onOpen(ServerHandshake handshakeData) {
            Log.d(TAG, "WebSocketTransport onOpen");
            var jObj = new JsonObject();
            jObj.addProperty("auth", mServerId);
            jObj.addProperty("passwd", UUID.randomUUID().toString());
            sendAsync(jObj.toString());
        }

        public void onMessage(String message) {
            try {

                var jObj = JsonParser.parseString(message).getAsJsonObject();
                var auth = jObj.get("auth");
                if (auth != null) {
                    mConnectResult.complete(auth.getAsString().toLowerCase().equals("ok"));
                    return;
                }

                String dataString = jObj.get("data").getAsString();
                var packet = new Packet();
                byte[] data = Base64.getDecoder().decode(dataString);
                if (data.length > VpnManager.MAX_PACKET_SIZE) {
                    throw new Exception("Oversized packet received");
                }

                packet.setData(data);
                packet.setLength(data.length);
                packet.setSource(jObj.get("FROM").getAsString());
                if (mMessageListener != null) {
                    mMessageListener.onMessage(packet);
                }
            } catch (Exception e) {
                Log.e(TAG, "WebSocketTransport onMessage error", e);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            Log.d(TAG, "WebSocketTransport onClose reason: " + reason);
            restart();
        }

        @Override
        public void onError(Exception e) {
            Log.e(TAG, "WebSocketTransport WSClient error", e);
            if (mConnectResult != null) {
                mConnectResult.complete(false);
            }
        }

        public void sendAsync(String message) {
            super.send(message);
        }
    }
}
