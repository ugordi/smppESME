package com.mycompany.smppclient.session;

import com.mycompany.smppclient.pdu.*;
import com.mycompany.smppclient.pdu.decoder.PduDecoder;
import com.mycompany.smppclient.pdu.encoder.PduEncoder;
import com.mycompany.smppclient.socket.SmppSocketClient;
import com.mycompany.smppclient.socket.SmppSocketConfig;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class SmppSessionManagerTest {

    @Test
    void bind_enquireLink_unbind_flow_ok() throws Exception {
        PduEncoder enc = new PduEncoder();
        PduDecoder dec = new PduDecoder();

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();

            ExecutorService es = Executors.newSingleThreadExecutor();
            Future<?> serverFuture = es.submit(() -> {
                try (Socket s = server.accept()) {
                    s.setSoTimeout(5000);
                    InputStream in = s.getInputStream();
                    OutputStream out = s.getOutputStream();

                    System.out.println("[SERVER] accepted");

                    byte[] bindReqBytes = readOnePdu(in);
                    Pdu p1 = dec.decode(bindReqBytes);
                    System.out.println("[SERVER] RX=" + p1.getClass().getSimpleName() + " seq=" + p1.getSequenceNumber());
                    assertTrue(p1 instanceof BindTransceiverReq);

                    int bindSeq = p1.getSequenceNumber();

                    // bind_resp dön
                    BindTransceiverResp bindResp = new BindTransceiverResp();
                    bindResp.setCommandStatus(0);
                    bindResp.setSequenceNumber(bindSeq);
                    bindResp.setSystemId("SMSC");
                    out.write(enc.encode(bindResp));
                    out.flush();
                    System.out.println("[SERVER] TX=BindTransceiverResp seq=" + bindSeq);

                    int enquireCount = 0;

                    // 2) unbind gelene kadar dön
                    while (true) {
                        byte[] pduBytes = readOnePdu(in);
                        Pdu p = dec.decode(pduBytes);

                        System.out.println("[SERVER] RX=" + p.getClass().getSimpleName() + " seq=" + p.getSequenceNumber());

                        if (p instanceof EnquireLinkReq) {
                            enquireCount++;
                            EnquireLinkResp r = new EnquireLinkResp();
                            r.setCommandStatus(0);
                            r.setSequenceNumber(p.getSequenceNumber());
                            out.write(enc.encode(r));
                            out.flush();
                            System.out.println("[SERVER] TX=EnquireLinkResp seq=" + p.getSequenceNumber() + " (count=" + enquireCount + ")");
                        }
                        else if (p instanceof UnbindReq) {
                            UnbindResp r = new UnbindResp();
                            r.setCommandStatus(0);
                            r.setSequenceNumber(p.getSequenceNumber());
                            out.write(enc.encode(r));
                            out.flush();
                            System.out.println("[SERVER] TX=UnbindResp seq=" + p.getSequenceNumber());
                            break;
                        }
                        else {
                            throw new RuntimeException("Unexpected PDU: " + p.getClass().getSimpleName());
                        }
                    }

                    System.out.println("[SERVER] DONE. enquireCount=" + enquireCount);

                } catch (Exception e) {
                    System.out.println("[SERVER] EXCEPTION !!!");
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            });

            // Client/session side
            SmppSocketConfig sockCfg = new SmppSocketConfig(2000, 2000, 1, 0);
            try (SmppSocketClient socket = new SmppSocketClient(sockCfg, null)) {
                SmppSessionConfig cfg = new SmppSessionConfig(2000, 200);
                SmppSessionManager sm = new SmppSessionManager(socket, cfg);

                BindTransceiverReq bindReq = new BindTransceiverReq();
                bindReq.setSystemId("sys");
                bindReq.setPassword("pw");
                bindReq.setSystemType("cp");
                bindReq.setInterfaceVersion((byte) 0x34);
                bindReq.setAddrTon((byte) 0);
                bindReq.setAddrNpi((byte) 0);
                bindReq.setAddressRange("");

                boolean bound = sm.bind("127.0.0.1", port, bindReq);
                assertTrue(bound);
                assertTrue(sm.isBound());

                sm.startEnquireLinkTask();

                // 2 enquire_link'in gidip gelmesi için kısa bekle
                Thread.sleep(800);

                boolean unboundOk = sm.unbind();
                assertTrue(unboundOk);
                assertFalse(sm.isBound());

                sm.close();
            }

            // Server thread bitmeli
            serverFuture.get(3, TimeUnit.SECONDS);
            es.shutdownNow();
        }
    }

    // TCP stream'den 1 PDU okumak: önce 4 byte length, sonra kalan length-4 byte
    private static byte[] readOnePdu(InputStream in) throws Exception {
        byte[] lenBytes = in.readNBytes(4);
        if (lenBytes.length < 4) throw new RuntimeException("EOF while reading length");
        int len = ((lenBytes[0] & 0xFF) << 24) |
                ((lenBytes[1] & 0xFF) << 16) |
                ((lenBytes[2] & 0xFF) << 8) |
                (lenBytes[3] & 0xFF);

        byte[] rest = in.readNBytes(len - 4);
        if (rest.length < len - 4) throw new RuntimeException("EOF while reading body");
        byte[] full = new byte[len];
        System.arraycopy(lenBytes, 0, full, 0, 4);
        System.arraycopy(rest, 0, full, 4, rest.length);
        return full;
    }
}
