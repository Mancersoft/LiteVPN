package com.mancersoft.litevpn.transport;

import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mancersoft.litevpn.VpnManager;

import org.java_websocket.WebSocketImpl;
import org.java_websocket.client.DnsResolver;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ServerHandshake;

import java.io.Closeable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import static com.mancersoft.litevpn.VpnManager.TAG;


public class WebSocketTransport implements IVpnTransport {

    private static final String SERVER_URI = "wss://cloud.achex.ca";

    private final String mServerId;
    private URI serverUri;
    private WSClient mWebSocketClient;
    private CustomSocketFactory mSocketFactory;

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
        disconnect();
        mSocketFactory = new CustomSocketFactory(serverUri);
        mWebSocketClient = new WSClient(serverUri);
        mWebSocketClient.setSocketFactory(mSocketFactory);
        return mSocketFactory.createNewSocket();
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

    private static class CustomSocketFactory extends SocketFactory {

        private URI mServerUri;
        private Socket mSocket;
        private static final int WEBSOCKET_CONNECT_TIMEOUT_MILLIS = 60000;

        Socket createNewSocket() {
            try {
                mSocket = SocketChannel.open().socket();
                return mSocket;
            } catch (Exception e) {
                Log.e(TAG, "CustomSocketFactory createNewSocket error", e);
                return null;
            }
        }

        private CompletableFuture<Boolean> connectSocketAsync() {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    DnsResolver dnsResolver = uri -> InetAddress.getByName(uri.getHost());
                    InetSocketAddress addr = new InetSocketAddress(dnsResolver.resolve(mServerUri), this.getPort());
                    mSocket.connect(addr, WEBSOCKET_CONNECT_TIMEOUT_MILLIS);
                    if ("wss".equals(mServerUri.getScheme())) {
                        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
                        sslContext.init(null, null, null);
                        SSLSocketFactory factory = sslContext.getSocketFactory();
                        mSocket = factory.createSocket(mSocket, mServerUri.getHost(), getPort(), true);
                    }

                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "CustomSocketFactory connectSocketAsync error", e);
                    return false;
                }
            });
        }

        private int getPort() {
            int port = mServerUri.getPort();
            String scheme = mServerUri.getScheme();
            if ("wss".equals( scheme )) {
                return port == -1 ? WebSocketImpl.DEFAULT_WSS_PORT : port;
            } else if ("ws".equals(scheme)) {
                return port == -1 ? WebSocketImpl.DEFAULT_PORT : port;
            } else {
                throw new IllegalArgumentException( "unknown scheme: " + scheme );
            }
        }

        @Override
        public Socket createSocket() {
            return mSocket;
        }

        @Override
        public Socket createSocket(String host, int port) {
            return mSocket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) {
            return mSocket;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) {
            return mSocket;
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) {
            return mSocket;
        }

        CustomSocketFactory(URI serverUri) {
            super();
            mServerUri = serverUri;
        }
    }

    private static String getUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private class WSClient extends WebSocketClient {

        private CompletableFuture<Boolean> mConnectResult;

        WSClient(URI serverUri) {
            super(serverUri);
        }

        CompletableFuture<Boolean> connectAsync() {
            mConnectResult = new CompletableFuture<>();
            mSocketFactory.connectSocketAsync().thenAccept(isConnected -> {
                if (isConnected) {
                    super.connect();
                } else {
                    mConnectResult.complete(false);
                }
            });

            return mConnectResult;
        }

        void disconnect() {
            super.close();
        }

        @Override
        public void onOpen(ServerHandshake handshakeData) {
            Log.d(TAG, "WebSocketTransport onOpen");
            JsonObject jObj = new JsonObject();
            jObj.addProperty("auth", getUUID());
            jObj.addProperty("passwd", getUUID());
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

                JsonElement error = jObj.get("error");
                if (error != null) {
                    WebSocketTransport.this.disconnect(false);
                    throw new Exception(error.getAsString());
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
