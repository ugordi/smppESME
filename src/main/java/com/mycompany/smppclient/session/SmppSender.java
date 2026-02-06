package com.mycompany.smppclient.session;

import com.mycompany.smppclient.pdu.*;
import com.mycompany.smppclient.pdu.encoder.PduEncoder;
import com.mycompany.smppclient.socket.SmppSocketClient;

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

    public SmppSender(
            SmppSocketClient socket,
            SmppSessionConfig cfg,
            PduEncoder encoder,
            PendingRequestRegistry pending,
            AtomicInteger seqGen
    ) {
        this.socket = Objects.requireNonNull(socket);
        this.cfg = Objects.requireNonNull(cfg);
        this.encoder = Objects.requireNonNull(encoder);
        this.pending = Objects.requireNonNull(pending);
        this.seqGen = Objects.requireNonNull(seqGen);
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

        socket.sendBytes(encoder.encode(req));

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

    /** Numaranın başında + varsa siler */
    public static String normalizeMsisdn(String raw) {
        if (raw == null) return null;

        String digits = raw.replaceAll("\\D+", "");

        return digits;
    }
}