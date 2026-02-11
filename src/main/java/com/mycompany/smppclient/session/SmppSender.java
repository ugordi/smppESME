package com.mycompany.smppclient.session;

import com.mycompany.smppclient.pdu.*;
import com.mycompany.smppclient.pdu.encoder.PduEncoder;
import com.mycompany.smppclient.socket.SmppSocketClient;
import com.mycompany.smppclient.db.SmppDao;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class SmppSender {

    private final SmppSocketClient socket;
    private final SmppSessionConfig cfg;
    private final PduEncoder encoder;
    private final PendingRequestRegistry pending;
    private final AtomicInteger seqGen;

    private final SmppDao dao;


    public SmppSender(
            SmppSocketClient socket,
            SmppSessionConfig cfg,
            PduEncoder encoder,
            PendingRequestRegistry pending,
            AtomicInteger seqGen,
            SmppDao dao

    ) {
        this.socket = Objects.requireNonNull(socket);
        this.cfg = Objects.requireNonNull(cfg);
        this.encoder = Objects.requireNonNull(encoder);
        this.pending = Objects.requireNonNull(pending);
        this.seqGen = Objects.requireNonNull(seqGen);
        this.dao = dao;
    }

    private int nextSeq() {
        int v = seqGen.getAndIncrement();
        if (v <= 0) {
            seqGen.set(1);
            v = seqGen.getAndIncrement();
        }
        return v;
    }


    public String sendSubmitSm(SubmitSmReq req) throws Exception {
        Objects.requireNonNull(req, "req");


        int seq = nextSeq();
        req.setSequenceNumber(seq);
        req.setCommandStatus(0);

        CompletableFuture<Pdu> f = pending.register(seq);

        byte[] bytes = encoder.encode(req);
        socket.sendBytes(bytes);


        if (dao != null) {
            try {
                String rawHex = bytesToHex(bytes);

                // header parse
                int commandId = readInt(bytes, 4);
                int commandStatus = readInt(bytes, 8);
                int sequence = readInt(bytes, 12);

                java.util.Map<String, Object> decoded = new java.util.HashMap<>();
                decoded.put("class", "SubmitSmReq");
                decoded.put("sequence_number", req.getSequenceNumber());
                decoded.put("source_addr", req.getSourceAddr());
                decoded.put("destination_addr", req.getDestinationAddr());
                decoded.put("data_coding", req.getDataCoding() & 0xFF);
                decoded.put("esm_class", req.getEsmClass() & 0xFF);
                byte[] sm = req.getShortMessage();
                decoded.put("sm_length", sm == null ? 0 : sm.length);
                decoded.put("short_message_hex", bytesToHex(sm));

                dao.insertPduLog(
                        com.mycompany.smppclient.db.SmppDao.Direction.OUT,
                        "SubmitSmReq",
                        commandId,
                        commandStatus,
                        sequence,
                        rawHex,
                        decoded
                );
            } catch (Exception ex) {

            }
        }


        Pdu resp;
        try {
            resp = f.get(cfg.getResponseTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            throw te;
        }

        if (!(resp instanceof SubmitSmResp)) {
            throw new IllegalStateException("Expected SubmitSmResp but got: " + resp.getClass().getSimpleName());
        }

        SubmitSmResp ssr = (SubmitSmResp) resp;

        if (ssr.getMessageId() == null || ssr.getMessageId().isBlank()) {
            throw new IllegalStateException("SubmitSmResp message_id is empty");
        }

        return ssr.getMessageId();
    }

    private static String bytesToHex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }

    private static int readInt(byte[] b, int offset) {
        // BIG ENDIAN
        return ((b[offset] & 0xFF) << 24)
                | ((b[offset + 1] & 0xFF) << 16)
                | ((b[offset + 2] & 0xFF) << 8)
                | (b[offset + 3] & 0xFF);
    }

    /** Numaranın başında + varsa siler */
    public static String normalizeMsisdn(String raw) {
        if (raw == null) return null;

        String digits = raw.replaceAll("\\D+", "");

        return digits;
    }
}