    package com.mycompany.smppclient.socket;

    import org.junit.jupiter.api.Test;

    import java.io.InputStream;
    import java.net.ServerSocket;
    import java.net.Socket;
    import java.nio.charset.StandardCharsets;
    import java.util.concurrent.ArrayBlockingQueue;
    import java.util.concurrent.TimeUnit;

    import static org.junit.jupiter.api.Assertions.*;

    public class SmppSocketClientTest {

        @Test
        void connectAndSendBytes_serverReceivesData() throws Exception {
            ArrayBlockingQueue<byte[]> received = new ArrayBlockingQueue<>(1);

            try (ServerSocket server = new ServerSocket(0)) {
                int port = server.getLocalPort();

                // 2) Server accept'i ayrı thread'de çalıştır
                Thread serverThread = new Thread(() -> {
                    try (Socket client = server.accept()) {
                        InputStream in = client.getInputStream();
                        byte[] buf = new byte[1024];
                        int n = in.read(buf);
                        if (n > 0) {
                            byte[] exact = new byte[n];
                            System.arraycopy(buf, 0, exact, 0, n);
                            received.offer(exact);
                        }
                    } catch (Exception ignored) {}
                });
                serverThread.start();

                // 3) Client connect + send
                SmppSocketConfig cfg = new SmppSocketConfig(2000, 2000, 1, 200);
                try (SmppSocketClient client = new SmppSocketClient(cfg, null)) {
                    boolean ok = client.connect("127.0.0.1", port);
                    assertTrue(ok, "connect should return true");

                    byte[] payload = "selam".getBytes(StandardCharsets.US_ASCII);
                    client.sendBytes(payload);
                }

                // 4) Assert: server aldı mı?
                byte[] got = received.poll(2, TimeUnit.SECONDS);
                assertNotNull(got, "server should receive bytes");
                assertEquals("selam", new String(got, StandardCharsets.US_ASCII));
            }
        }


        @Test
        void sendBytes_whenServerCloses_clientAttemptsReconnect() throws Exception {
            // Server: bağlantıyı kabul edip hemen kapatsın (kopma simülasyonu)
            try (ServerSocket server = new ServerSocket(0)) {
                int port = server.getLocalPort();

                Thread serverThread = new Thread(() -> {
                    try (Socket client = server.accept()) {
                        // Accept eder etmez kapat: client tarafında write/read bozulacak
                    } catch (Exception ignored) {}
                });
                serverThread.start();

                SmppSocketConfig cfg = new SmppSocketConfig(
                        2000, // connect timeout
                        500,  // read timeout
                        2,    // max reconnect attempts
                        100   // backoff
                );

                try (SmppSocketClient client = new SmppSocketClient(cfg, null)) {
                    assertTrue(client.connect("127.0.0.1", port));


                    assertThrows(RuntimeException.class, () ->
                            client.sendBytes("selam".getBytes(StandardCharsets.US_ASCII))
                    );
                }
            }
        }

    }
