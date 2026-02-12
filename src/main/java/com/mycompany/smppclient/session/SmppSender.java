package com.mycompany.smppclient.session;

import com.mycompany.smppclient.pdu.*;
import com.mycompany.smppclient.pdu.encoder.PduEncoder;
import com.mycompany.smppclient.pdu.encoding.Gsm7Codec;
import com.mycompany.smppclient.socket.SmppSocketClient;
import com.mycompany.smppclient.db.SmppDao;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SmppSender {

    private final SmppSocketClient socket;
    private final SmppSessionConfig cfg;
    private final PduEncoder encoder;
    private final PendingRequestRegistry pending;
    private final AtomicInteger seqGen;

    private final SmppDao dao;

    private final String sessionId;
    private final String systemId;

    private static final Logger log = LogManager.getLogger(SmppSender.class);


    public SmppSender(
            SmppSocketClient socket,
            SmppSessionConfig cfg,
            PduEncoder encoder,
            PendingRequestRegistry pending,
            AtomicInteger seqGen,
            SmppDao dao,
            String sessionId,
            String systemId

    ) {
        this.socket = Objects.requireNonNull(socket);
        this.cfg = Objects.requireNonNull(cfg);
        this.encoder = Objects.requireNonNull(encoder);
        this.pending = Objects.requireNonNull(pending);
        this.seqGen = Objects.requireNonNull(seqGen);
        this.dao = dao;
        this.sessionId = sessionId;
        this.systemId = systemId;
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

        // önce register
        CompletableFuture<Pdu> f = pending.register(seq);

        // encode
        byte[] bytes = encoder.encode(req);
        byte[] sm = req.getShortMessage();

        long submitLogId = -1;

        // ✅ DB işlemleri ÖNCE (send’den önce)
        if (dao != null) {
            try {
                String rawHex = bytesToHex(bytes);

                int commandId = readInt(bytes, 4);
                int commandStatus = readInt(bytes, 8);
                int sequence = readInt(bytes, 12);

                java.util.Map<String, Object> decoded = new java.util.HashMap<>();
                decoded.put("class", "SubmitSmReq");
                decoded.put("sequence_number", seq);
                decoded.put("source_addr", req.getSourceAddr());
                decoded.put("destination_addr", req.getDestinationAddr());
                decoded.put("data_coding", req.getDataCoding() & 0xFF);
                decoded.put("esm_class", req.getEsmClass() & 0xFF);
                decoded.put("sm_length", sm == null ? 0 : sm.length);
                decoded.put("short_message_hex", bytesToHex(sm));

                submitLogId = dao.insertPduLog(
                        SmppDao.Direction.OUT,
                        "SubmitSmReq",
                        commandId,
                        commandStatus,
                        sequence,
                        rawHex,
                        decoded
                );

                dao.insertMessageFlowOnSubmit(
                        sessionId,
                        systemId,
                        seq,                         // ✅ submit_seq kesin bu
                        req.getSourceAddr(),
                        req.getDestinationAddr(),
                        req.getDataCoding() & 0xFF,
                        req.getEsmClass() & 0xFF,
                        bytesToHex(sm),
                        submitLogId
                );

            } catch (Exception ex) {
                log.warn("DB submit log/flow insert failed", ex);
            }
        }


        socket.sendBytes(bytes);

        // response bekle
        Pdu resp = f.get(cfg.getResponseTimeoutMs(), TimeUnit.MILLISECONDS);

        if (!(resp instanceof SubmitSmResp ssr)) {
            throw new IllegalStateException("Expected SubmitSmResp but got: " + resp.getClass().getSimpleName());
        }

        if (ssr.getMessageId() == null || ssr.getMessageId().isBlank()) {
            throw new IllegalStateException("SubmitSmResp message_id is empty");
        }

        return ssr.getMessageId();
    }

    public List<String> sendSubmitSmSarConcat(SubmitSmReq baseReq, String text) throws Exception {

        // 1) encoding (sen şu an unpacked gönderiyorsun)
        byte[] payload;
        int dc = baseReq.getDataCoding() & 0xFF;

        if (dc == 0x00) {
            payload = Gsm7Codec.encodeUnpacked(text);
        } else if (dc == 0x08) {
            payload = text.getBytes(java.nio.charset.StandardCharsets.UTF_16BE);
        } else {
            payload = text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        }

        // 2) tek SMS’e sığıyorsa normal gönder
        int maxBytesPerSeg = (dc == 0x08) ? 134 : 153;

        if (payload.length <= maxBytesPerSeg) {
            SubmitSmReq one = cloneBase(baseReq);
            one.setEsmClass((byte)0x00);
            one.setShortMessage(payload);
            String mid = sendSubmitSm(one);
            return List.of(mid);
        }

        // 3) parçala
        List<byte[]> parts = split(payload, maxBytesPerSeg);
        int total = parts.size();

        // 4) ref num (0..65535)
        int refNum = ThreadLocalRandom.current().nextInt(0, 0x10000);

        List<String> ids = new ArrayList<>(total);

        for (int i = 0; i < total; i++) {
            int seq = i + 1;

            SubmitSmReq seg = cloneBase(baseReq);
            seg.setEsmClass((byte)0x00);            // UDH yok
            seg.setShortMessage(parts.get(i));

            // SAR TLV ekle
            seg.addSarTlvs(refNum, total, seq);

            String mid = sendSubmitSm(seg);
            ids.add(mid);
        }

        return ids;
    }

    private static List<byte[]> split(byte[] data, int chunk) {
        List<byte[]> out = new ArrayList<>();
        for (int off = 0; off < data.length; off += chunk) {
            int n = Math.min(chunk, data.length - off);
            byte[] p = new byte[n];
            System.arraycopy(data, off, p, 0, n);
            out.add(p);
        }
        return out;
    }

    private static SubmitSmReq cloneBase(SubmitSmReq src) {
        SubmitSmReq r = new SubmitSmReq();
        r.setServiceType(src.getServiceType());
        r.setSourceAddrTon(src.getSourceAddrTon());
        r.setSourceAddrNpi(src.getSourceAddrNpi());
        r.setSourceAddr(src.getSourceAddr());
        r.setDestAddrTon(src.getDestAddrTon());
        r.setDestAddrNpi(src.getDestAddrNpi());
        r.setDestinationAddr(src.getDestinationAddr());
        r.setProtocolId(src.getProtocolId());
        r.setPriorityFlag(src.getPriorityFlag());
        r.setRegisteredDelivery(src.getRegisteredDelivery());
        r.setDataCoding(src.getDataCoding());
        return r;
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