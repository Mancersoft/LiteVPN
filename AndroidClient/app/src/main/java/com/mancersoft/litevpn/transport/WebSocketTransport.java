package com.mancersoft.litevpn.transport;

import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mancersoft.litevpn.VpnManager;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ServerHandshake;

import java.io.Closeable;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.mancersoft.litevpn.VpnManager.TAG;


public class WebSocketTransport implements IVpnTransport {

    private static final String SERVER_URI = "wss://cloud.achex.ca";

    private final String mServerId;
    private URI serverUri;
    private WSClient mWebSocketClient;

    private MessageListener mMessageListener;
    private ClosedListener mClosedListener;

    public WebSocketTransport(String serverId) {
        mServerId = serverId;
        try {
            serverUri = new URI(SERVER_URI);
        } catch (Exception e) {
            Log.e(TAG, "WebSocketTransport URI error", e);
        }
    }

    @Override
    public Closeable createSocket() {
        mWebSocketClient = new WSClient(serverUri);
        return mWebSocketClient.getSocket();
    }

    @Override
    public boolean isReliable() {
        return true;
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        disconnect();
        return mWebSocketClient.connectAsync();
    }

    private void disconnect(boolean isByUser) {
        disconnect();
        if (mClosedListener != null) {
            mClosedListener.onClosed(isByUser);
        }
    }

    @Override
    public void disconnect() {
        if (mWebSocketClient != null && mWebSocketClient.isOpen()) {
            mWebSocketClient.disconnect();
        }
    }

    @Override
    public void sendAsync(Packet packet) {
        try {
            ByteBuffer encoded =
                    Base64.getEncoder().encode(ByteBuffer.wrap(packet.getData(), 0, packet.getLength()));
            String dataString = new String(encoded.array(), StandardCharsets.ISO_8859_1);
            JsonObject jObj = new JsonObject();
            jObj.addProperty("to", mServerId);
            jObj.addProperty("data", dataString);
            mWebSocketClient.sendAsync(jObj.toString());
        } catch (WebsocketNotConnectedException e1) {
            disconnect(true);
        } catch (Exception e2) {
            Log.e(TAG, "WebSocketTransport sendAsync error", e2);
            disconnect(false);
        }
    }

    @Override
    public void setOnMessageListener(MessageListener messageListener) {
        mMessageListener = messageListener;
    }

    @Override
    public void setOnClosedListener(ClosedListener closedListener) {
        mClosedListener = closedListener;
    }

    private class WSClient extends WebSocketClient {

        private CompletableFuture<Boolean> mConnectResult;

        WSClient(URI serverUri) {
            super(serverUri);
        }

        CompletableFuture<Boolean> connectAsync() {
            mConnectResult = new CompletableFuture<>();
            super.connect();
            return mConnectResult;
        }

        void disconnect() {
            super.close();
        }

        @Override
        public void onOpen(ServerHandshake handshakeData) {
            Log.d(TAG, "WebSocketTransport onOpen");
            JsonObject jObj = new JsonObject();
            jObj.addProperty("auth", UUID.randomUUID().toString());
            jObj.addProperty("passwd", UUID.randomUUID().toString());
            sendAsync(jObj.toString());
        }

        public void onMessage(String message) {
            try {

                JsonObject jObj = JsonParser.parseString(message).getAsJsonObject();
                JsonElement auth = jObj.get("auth");
                if (auth != null) {
                    mConnectResult.complete(auth.getAsString().toLowerCase().equals("ok"));
                    return;
                }

                String dataString = jObj.get("data").getAsString();
                Packet packet = new Packet();
                byte[] data = Base64.getDecoder().decode(dataString);
                if (data.length > VpnManager.MAX_PACKET_SIZE) {
                    throw new Exception("Oversized packet received");
                }

                packet.setData(data);
                packet.setLength(data.length);
                if (!jObj.get("FROM").getAsString().equals(mServerId)) {
                    throw new Exception("Received packet is not from server");
                }

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
            WebSocketTransport.this.disconnect(!remote);
        }

        @Override
        public void onError(Exception e) {
            Log.e(TAG, "WebSocketTransport WSClient error", e);
            if (mConnectResult != null) {
                mConnectResult.complete(false);
            }
        }

        void sendAsync(String message) {
            super.send(message);
        }
    }
}
