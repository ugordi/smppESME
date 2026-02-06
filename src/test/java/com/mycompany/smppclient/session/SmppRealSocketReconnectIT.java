package com.mycompany.smppclient.session;

import com.mycompany.smppclient.pdu.BindTransceiverReq;
import com.mycompany.smppclient.socket.SmppSocketClient;
import com.mycompany.smppclient.socket.SmppSocketConfig;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SmppRealSocketReconnectIT {

    @Test
    void real_socket_enquire_timeout_triggers_reconnect_and_rebind() throws Exception {
        // 1) Mini SMSC başlat
        MiniSmsc smsc = new MiniSmsc();
        smsc.start();

        // 2) Client + SessionManager
        SmppSocketConfig sockCfg = new SmppSocketConfig(
                1000,  // connectTimeout
                1000,  // readTimeout (socket soTimeout)
                3,     // maxReconnectAttempts
                50     // reconnectBackoff
        );

        // responseTimeout küçük olsun ki enquire hızlı timeout olsun
        SmppSessionConfig cfg = new SmppSessionConfig(
                300,  // responseTimeoutMs
                2000   // enquireLinkIntervalMs
        );

        try (SmppSocketClient socket = new SmppSocketClient(sockCfg, null)) {
            SmppSessionManager sm = new SmppSessionManager(socket, cfg, null);

            BindTransceiverReq bindReq = new BindTransceiverReq();
            bindReq.setSystemId("user");
            bindReq.setPassword("pass");
            bindReq.setSystemType("");
            bindReq.setInterfaceVersion((byte) 0x34);
            bindReq.setAddrTon((byte) 0);
            bindReq.setAddrNpi((byte) 0);
            bindReq.setAddressRange("");

            // 3) İlk bind
            assertTrue(sm.bind("127.0.0.1", smsc.port(), bindReq));
            assertTrue(sm.isBound());

            // 4) enquire task başlat
            sm.startEnquireLinkTask();

            // MiniSmsc davranışı:
            // - bindlere her zaman OK döner
            // - enquire_link'e SADECE 1 kere OK döner, sonra HİÇ cevap vermez
            //   -> client tarafında enquire timeout -> reconnect+rebind tetiklenecek

            // 5) Bekle: rebind gerçekleşene kadar (max 5 saniye)
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                if (smsc.bindCount.get() >= 2 && sm.isBound()) {
                    break;
                }
                Thread.sleep(500);
            }

            assertTrue(smsc.bindCount.get() >= 2,
                    "Beklenen: bağlantı kopunca reconnect+rebind -> SMSC en az 2 bind görmeli (bindCount>=2). " +
                            "Şu an bindCount=" + smsc.bindCount.get());

            assertTrue(sm.isBound(),
                    "Beklenen: rebind sonrası isBound tekrar true olmalı.");

            sm.close();
        } finally {
            smsc.stop();
        }
    }

    /**
     * Lokal mini SMSC:
     * - TCP accept eder
     * - bind_transceiver -> bind_transceiver_resp OK döner
     * - enquire_link -> 1 kere enquire_link_resp OK döner, sonra cevap vermez (timeout olsun diye)
     */
    static class MiniSmsc {
        private ServerSocket server;
        private Thread acceptThread;

        final AtomicInteger connectionCount = new AtomicInteger(0);
        final AtomicInteger bindCount = new AtomicInteger(0);
        final AtomicInteger enquireReqCount = new AtomicInteger(0);

        // sadece 1 enquire'a cevap ver; sonrası cevap yok
        private volatile boolean repliedFirstEnquire = false;

        void start() throws IOException {
            server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            acceptThread = new Thread(this::acceptLoop, "mini-smsc-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        void stop() {
            try { server.close(); } catch (Exception ignored) {}
        }

        private void acceptLoop() {
            while (!server.isClosed()) {
                try {
                    Socket s = server.accept();
                    connectionCount.incrementAndGet();
                    Thread t = new Thread(() -> handleConn(s), "mini-smsc-conn");
                    t.setDaemon(true);
                    t.start();
                } catch (IOException e) {
                    if (!server.isClosed()) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        private void handleConn(Socket s) {
            try (Socket socket = s) {
                socket.setSoTimeout(0); // server tarafı bloklayabilir
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                while (true) {
                    byte[] pdu = readOnePdu(in);
                    if (pdu == null) return;

                    int cmdId = readIntBE(pdu, 4);
                    int seq = readIntBE(pdu, 12);

                    // bind_transceiver (0x00000009)
                    if (cmdId == 0x00000009) {
                        bindCount.incrementAndGet();
                        out.write(buildBindTransceiverRespOk(seq));
                        out.flush();
                        continue;
                    }

                    // enquire_link (0x00000015)
                    if (cmdId == 0x00000015) {
                        enquireReqCount.incrementAndGet();

                        // sadece ilk enquire'a cevap ver
                        if (!repliedFirstEnquire) {
                            repliedFirstEnquire = true;
                            out.write(buildEnquireLinkRespOk(seq));
                            out.flush();
                        } else {
                            // cevap verme -> client tarafında timeout tetiklenmeli
                        }
                        continue;
                    }

                    // diğerleri: şimdilik ignore
                }
            } catch (Exception ignored) {
                // bağlantı kapanabilir, önemli değil
            }
        }

        private static byte[] readOnePdu(InputStream in) throws IOException {
            byte[] lenBuf = in.readNBytes(4);
            if (lenBuf.length < 4) return null;
            int len = readIntBE(lenBuf, 0);
            if (len < 16) throw new IOException("Invalid PDU length: " + len);

            byte[] rest = in.readNBytes(len - 4);
            if (rest.length < len - 4) return null;

            byte[] full = new byte[len];
            System.arraycopy(lenBuf, 0, full, 0, 4);
            System.arraycopy(rest, 0, full, 4, rest.length);
            return full;
        }

        private static byte[] buildBindTransceiverRespOk(int seq) {
            // header(16) + system_id("OK\0")
            byte[] systemId = "OK\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            int len = 16 + systemId.length;

            byte[] pdu = new byte[len];
            writeIntBE(pdu, 0, len);
            writeIntBE(pdu, 4, 0x80000009); // bind_transceiver_resp
            writeIntBE(pdu, 8, 0);          // status OK
            writeIntBE(pdu, 12, seq);
            System.arraycopy(systemId, 0, pdu, 16, systemId.length);
            return pdu;
        }

        private static byte[] buildEnquireLinkRespOk(int seq) {
            int len = 16;
            byte[] pdu = new byte[len];
            writeIntBE(pdu, 0, len);
            writeIntBE(pdu, 4, 0x80000015); // enquire_link_resp
            writeIntBE(pdu, 8, 0);          // status OK
            writeIntBE(pdu, 12, seq);
            return pdu;
        }

        private static int readIntBE(byte[] b, int off) {
            return ((b[off] & 0xFF) << 24) |
                    ((b[off + 1] & 0xFF) << 16) |
                    ((b[off + 2] & 0xFF) << 8) |
                    (b[off + 3] & 0xFF);
        }

        private static void writeIntBE(byte[] b, int off, int v) {
            b[off]     = (byte) ((v >>> 24) & 0xFF);
            b[off + 1] = (byte) ((v >>> 16) & 0xFF);
            b[off + 2] = (byte) ((v >>> 8) & 0xFF);
            b[off + 3] = (byte) (v & 0xFF);
        }
    }
}
