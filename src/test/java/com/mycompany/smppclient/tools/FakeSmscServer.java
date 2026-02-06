package com.mycompany.smppclient.tools;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeSmscServer {

    // SMPP command_ids
    private static final int BIND_TRANSCEIVER      = 0x00000009;
    private static final int BIND_TRANSCEIVER_RESP = 0x80000009;
    private static final int SUBMIT_SM            = 0x00000004;
    private static final int SUBMIT_SM_RESP       = 0x80000004;
    private static final int DELIVER_SM           = 0x00000005;
    private static final int DELIVER_SM_RESP      = 0x80000005;
    private static final int ENQUIRE_LINK         = 0x00000015;
    private static final int ENQUIRE_LINK_RESP    = 0x80000015;
    private static final int UNBIND               = 0x00000006;
    private static final int UNBIND_RESP          = 0x80000006;

    private static final AtomicInteger msgIdGen = new AtomicInteger(1000);

    public static void main(String[] args) throws Exception {
        int port = 2775;
        System.out.println("FakeSMSC listening on 0.0.0.0:" + port);

        try (ServerSocket ss = new ServerSocket(port)) {
            Socket s = ss.accept();
            System.out.println("Client connected: " + s.getRemoteSocketAddress());

            s.setTcpNoDelay(true);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();

            ByteArrayOutputStream acc = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];

            while (true) {
                int n = in.read(buf);
                if (n == -1) break;
                acc.write(buf, 0, n);

                byte[] all = acc.toByteArray();
                int off = 0;

                while (true) {
                    if (all.length - off < 4) break;

                    int pduLen = readIntBE(all, off);
                    if (pduLen < 16) throw new IOException("Invalid PDU length: " + pduLen);
                    if (all.length - off < pduLen) break;

                    byte[] pdu = Arrays.copyOfRange(all, off, off + pduLen);
                    off += pduLen;

                    int cmdId = readIntBE(pdu, 4);
                    int status = readIntBE(pdu, 8);
                    int seq = readIntBE(pdu, 12);

                    System.out.println("[RX] len=" + pduLen +
                            " cmdId=0x" + String.format("%08X", cmdId) +
                            " status=" + status +
                            " seq=" + seq +
                            " hex=" + toHex(pdu));

                    if (cmdId == BIND_TRANSCEIVER) {
                        // Respond OK
                        byte[] resp = buildBindRespOk(seq, "FAKE_SMSC");
                        out.write(resp); out.flush();
                        System.out.println("[TX] bind_transceiver_resp OK seq=" + seq);

                    } else if (cmdId == ENQUIRE_LINK) {
                        byte[] resp = buildHeaderOnly(ENQUIRE_LINK_RESP, 0, seq);
                        out.write(resp); out.flush();
                        System.out.println("[TX] enquire_link_resp OK seq=" + seq);

                    } else if (cmdId == SUBMIT_SM) {
                        String messageId = "Fake" + msgIdGen.getAndIncrement();
                        byte[] resp = buildSubmitRespOk(seq, messageId);
                        out.write(resp); out.flush();
                        System.out.println("[TX] submit_sm_resp OK seq=" + seq + " message_id=" + messageId);

                        // küçük gecikme sonra DELIVER_SM gönder (ASCII "123")
                        new Thread(() -> {
                            try {
                                Thread.sleep(500);

                                // deliver_sm: data_coding=0, esm_class=0, short_message="123"
                                byte[] deliver = buildDeliverSmAscii(
                                        /*seq*/ 777,          // server->client seq, istediğin bir şey
                                        "905000000000",       // source_addr
                                        "nettest",            // destination_addr (senin source_addr'ın)
                                        "1"                 // short_message
                                );

                                out.write(deliver); out.flush();
                                System.out.println("[TX] deliver_sm (ASCII) sent seq=777 text=123");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();

                    } else if (cmdId == DELIVER_SM_RESP) {
                        System.out.println("[RX] deliver_sm_resp ack seq=" + seq);

                    } else if (cmdId == UNBIND) {
                        byte[] resp = buildHeaderOnly(UNBIND_RESP, 0, seq);
                        out.write(resp); out.flush();
                        System.out.println("[TX] unbind_resp OK seq=" + seq);
                        return;
                    } else {
                        // ignore
                    }
                }

                ByteArrayOutputStream next = new ByteArrayOutputStream();
                if (off < all.length) next.write(all, off, all.length - off);
                acc = next;
            }
        }
    }

    // ----------------- PDU builders -----------------

    private static byte[] buildHeaderOnly(int cmdId, int status, int seq) {
        byte[] b = new byte[16];
        writeIntBE(b, 0, 16);
        writeIntBE(b, 4, cmdId);
        writeIntBE(b, 8, status);
        writeIntBE(b, 12, seq);
        return b;
    }

    private static byte[] buildBindRespOk(int seq, String systemId) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(systemId.getBytes(StandardCharsets.ISO_8859_1));
        body.write(0x00); // CString terminator

        byte[] header = new byte[16];
        int len = 16 + body.size();
        writeIntBE(header, 0, len);
        writeIntBE(header, 4, BIND_TRANSCEIVER_RESP);
        writeIntBE(header, 8, 0);
        writeIntBE(header, 12, seq);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header);
        out.write(body.toByteArray());
        return out.toByteArray();
    }

    private static byte[] buildSubmitRespOk(int seq, String messageId) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(messageId.getBytes(StandardCharsets.ISO_8859_1));
        body.write(0x00);

        byte[] header = new byte[16];
        int len = 16 + body.size();
        writeIntBE(header, 0, len);
        writeIntBE(header, 4, SUBMIT_SM_RESP);
        writeIntBE(header, 8, 0);
        writeIntBE(header, 12, seq);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header);
        out.write(body.toByteArray());
        return out.toByteArray();
    }

    /**
     * DeliverSm body: (SMPP 3.4)
     * service_type CString
     * source_addr_ton 1
     * source_addr_npi 1
     * source_addr CString
     * dest_addr_ton 1
     * dest_addr_npi 1
     * destination_addr CString
     * esm_class 1
     * protocol_id 1
     * priority_flag 1
     * schedule_delivery_time CString
     * validity_period CString
     * registered_delivery 1
     * replace_if_present_flag 1
     * data_coding 1
     * sm_default_msg_id 1
     * sm_length 1
     * short_message sm_length
     */
    private static byte[] buildDeliverSmAscii(int seq, String srcAddr, String dstAddr, String text) throws IOException {
        byte[] sm = text.getBytes(StandardCharsets.ISO_8859_1);

        ByteArrayOutputStream body = new ByteArrayOutputStream();

        writeCString(body, "");           // service_type
        body.write(0x01);                 // source_ton
        body.write(0x01);                 // source_npi
        writeCString(body, srcAddr);      // source_addr

        body.write(0x01);                 // dest_ton
        body.write(0x01);                 // dest_npi
        writeCString(body, dstAddr);      // destination_addr

        body.write(0x00);                 // esm_class
        body.write(0x00);                 // protocol_id
        body.write(0x00);                 // priority_flag

        writeCString(body, "");           // schedule_delivery_time
        writeCString(body, "");           // validity_period

        body.write(0x00);                 // registered_delivery
        body.write(0x00);                 // replace_if_present_flag
        body.write(0x00);                 // data_coding (default alphabet)
        body.write(0x00);                 // sm_default_msg_id

        body.write(sm.length & 0xFF);     // sm_length (unsigned)
        body.write(sm);                   // short_message

        byte[] header = new byte[16];
        int len = 16 + body.size();
        writeIntBE(header, 0, len);
        writeIntBE(header, 4, DELIVER_SM);
        writeIntBE(header, 8, 0);
        writeIntBE(header, 12, seq);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header);
        out.write(body.toByteArray());
        return out.toByteArray();
    }

    private static void writeCString(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
        out.write(0x00);
    }

    // ----------------- utils -----------------

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

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }
}
