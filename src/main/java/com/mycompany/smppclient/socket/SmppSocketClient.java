package com.mycompany.smppclient.socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SmppSocketClient implements AutoCloseable {
    private static final Logger log = LogManager.getLogger(SmppSocketClient.class);

    public interface BytesListener {
        void onBytesReceived(byte[] data, int length);
    }

    private final SmppSocketConfig config;
    private final BytesListener listener;

    private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile Socket socket;
    private volatile InputStream in;
    private volatile OutputStream out;

    private volatile String host;
    private volatile int port;

    private java.util.function.Consumer<byte[]> onPduBytes;

    private Runnable onDisconnect;

    public int getMaxReconnectAttempts() {
        return config.getMaxReconnectAttempts();
    }

    public int getReconnectBackoffMs() {
        return config.getReconnectBackoffMs();
    }

    public void setOnDisconnect(Runnable onDisconnect) {
        this.onDisconnect = onDisconnect;
    }

    public SmppSocketClient(SmppSocketConfig config, BytesListener listener) {
        this.config = Objects.requireNonNull(config, "config");
        this.listener = listener;
    }


    public void setOnPduBytes(java.util.function.Consumer<byte[]> onPduBytes) {
        this.onPduBytes = onPduBytes;
    }

    public synchronized boolean connect(String host, int port) {
        this.host = host;
        this.port = port;

        try {
            doConnect();
            startReader();
            log.info("Connected to {}:{}", host, port);
            return true;
        } catch (Exception e) {
            log.error("Connect failed to {}:{} - {}", host, port, e.toString());
            safeCloseSocket();
            return false;
        }
    }


    public synchronized void disconnect() {
        running.set(false);
        safeCloseSocket();
        log.info("Disconnected.");
    }


    public void sendBytes(byte[] data) {
        Objects.requireNonNull(data, "data");
        OutputStream localOut = this.out;

        if (localOut == null) {
            throw new IllegalStateException("Not connected: output stream is null");
        }

        try {
            localOut.write(data);
            localOut.flush();
        } catch (IOException e) {
            log.warn("sendBytes failed: {}", e.toString());


            running.set(false);
            safeCloseSocket();

            Runnable cb = onDisconnect;
            if (cb != null) {
                try { cb.run(); } catch (Exception ex) { log.warn("onDisconnect callback failed: {}", ex.toString()); }
            }

            throw new RuntimeException("sendBytes failed", e);
        }
    }


    private void startReader() {
        if (running.getAndSet(true)) return;

        readerExecutor.submit(() -> {
            byte[] readBuf = new byte[4096];

            java.io.ByteArrayOutputStream acc = new java.io.ByteArrayOutputStream();

            while (running.get()) {
                try {
                    InputStream localIn = this.in;
                    if (localIn == null) {
                        break;
                    }
                    int n = localIn.read(readBuf);
                    if (n == -1) throw new IOException("Remote closed connection (read=-1)");

                    acc.write(readBuf, 0, n);

                    byte[] buf = acc.toByteArray();
                    int offset = 0;

                    while (true) {
                        if (buf.length - offset < 4) break;

                        int pduLen = readIntBE(buf, offset);
                        if (pduLen < 16) throw new IOException("Invalid SMPP PDU length: " + pduLen);

                        if (buf.length - offset < pduLen) break;

                        byte[] onePdu = new byte[pduLen];
                        System.arraycopy(buf, offset, onePdu, 0, pduLen);
                        offset += pduLen;

                        log.info("[SOCKET] RX raw PDU len={} cmdId=0x{} seq={} hex={}",
                                pduLen,
                                String.format("%08X", readIntBE(onePdu, 4)),
                                readIntBE(onePdu, 12),
                                toHex(onePdu)
                        );


                        if (onPduBytes != null) {
                            onPduBytes.accept(onePdu);
                        }

                    }

                    java.io.ByteArrayOutputStream next = new java.io.ByteArrayOutputStream();
                    if (offset < buf.length) {
                        next.write(buf, offset, buf.length - offset);
                    }
                    acc = next;

                } catch (java.net.SocketTimeoutException timeout) {
                    log.debug("Read timeout (no data) - continuing...");
                } catch (Exception e) {
                    if (!running.get()) break;

                    log.warn("Reader loop error: {}. stopping reader.", e.toString());

                    running.set(false);
                    safeCloseSocket();

                    Runnable cb = onDisconnect;
                    if (cb != null) {
                        try { cb.run(); } catch (Exception ex) { log.warn("onDisconnect callback failed: {}", ex.toString()); }
                    }
                    break;
                }
            }
        });
    }


    private void doConnect() throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), config.getConnectTimeoutMs());
        s.setSoTimeout(config.getReadTimeoutMs());

        this.socket = s;
        this.in = s.getInputStream();
        this.out = s.getOutputStream();
    }


    private void safeCloseSocket() {
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}

        in = null;
        out = null;
        socket = null;
    }

    public synchronized void reconnect(String host, int port) {
        this.host = host;
        this.port = port;

        // reader dur
        running.set(false);
        safeCloseSocket();

        // tekrar bağlan
        try {
            doConnect();
            startReader();
            log.info("Reconnected (manual) to {}:{}", host, port);
        } catch (Exception e) {
            log.error("Manual reconnect failed to {}:{} - {}", host, port, e.toString());
            safeCloseSocket();
            throw new RuntimeException(e);
        }
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }



    private static int readIntBE(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) |
                ((b[off + 1] & 0xFF) << 16) |
                ((b[off + 2] & 0xFF) << 8) |
                (b[off + 3] & 0xFF);
    }

    @Override
    public void close() {
        disconnect();
        readerExecutor.shutdownNow();
    }

}
