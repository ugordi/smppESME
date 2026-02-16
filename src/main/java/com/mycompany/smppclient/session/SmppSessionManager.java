    package com.mycompany.smppclient.session;

    import com.mycompany.smppclient.pdu.*;
    import com.mycompany.smppclient.pdu.decoder.PduDecoder;
    import com.mycompany.smppclient.pdu.encoder.PduEncoder;
    import com.mycompany.smppclient.pdu.encoding.Gsm7Codec;
    import com.mycompany.smppclient.socket.SmppSocketClient;
    import org.apache.logging.log4j.LogManager;
    import org.apache.logging.log4j.Logger;

    import java.util.List;
    import java.util.concurrent.*;
    import java.util.concurrent.atomic.AtomicInteger;
    import java.util.Arrays;
    import java.nio.ByteBuffer;
    import java.nio.ByteOrder;
    import java.util.HashMap;
    import java.util.Map;


    public class SmppSessionManager implements AutoCloseable {

        private static final Logger log = LogManager.getLogger(SmppSessionManager.class);

        private final SmppSocketClient socket;
        private final SmppSessionConfig cfg;
        private final PduEncoder encoder = new PduEncoder();
        private final PduDecoder decoder = new PduDecoder();
        private final PendingRequestRegistry pending = new PendingRequestRegistry();

        private final AtomicInteger seqGen = new AtomicInteger(1);

        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        private volatile boolean bound = false;

        private final AtomicInteger enquireOkCount = new AtomicInteger(0);

        private final SmppSender sender;

        private final IncomingMessageHandler incomingHandler;


        private volatile long lastEnquireOkAtMs = 0;
        private volatile BindTransceiverReq lastBindReq;
        private volatile String lastHost;
        private volatile int lastPort;

        private com.mycompany.smppclient.db.SmppDao dao;
        private String sessionId;
        private String systemId;


        private final java.util.concurrent.ConcurrentHashMap<String, java.util.List<PendingDeliver>> pendingDeliverByMsgId
                = new java.util.concurrent.ConcurrentHashMap<>();


        public SmppSessionManager(SmppSocketClient socket, SmppSessionConfig cfg) {
            this(socket, cfg, null);
        }

        public SmppSessionManager(SmppSocketClient socket, SmppSessionConfig cfg, IncomingMessageHandler handler) {
            this(socket, cfg, handler, null, null, null); // <-- DB yok
        }

        public SmppSessionManager(
                SmppSocketClient socket,
                SmppSessionConfig cfg,
                IncomingMessageHandler handler,
                com.mycompany.smppclient.db.SmppDao dao,
                String sessionId,
                String systemId
        ) {
            this.socket = socket;
            this.cfg = cfg;
            this.incomingHandler = handler;

            this.dao = dao;
            this.sessionId = sessionId;
            this.systemId = systemId;
            this.sender = new SmppSender(socket, cfg, encoder, pending, seqGen, dao, sessionId, systemId);

        }

        static final class PendingDeliver {
            final String messageId;
            final boolean isDlr;
            final String srcAddr;
            final String dstAddr;
            final int dataCoding;
            final int esmClass;
            final String text;
            final long deliverLogId;

            PendingDeliver(String messageId, boolean isDlr, String srcAddr, String dstAddr,
                           int dataCoding, int esmClass, String text, long deliverLogId) {
                this.messageId = messageId;
                this.isDlr = isDlr;
                this.srcAddr = srcAddr;
                this.dstAddr = dstAddr;
                this.dataCoding = dataCoding;
                this.esmClass = esmClass;
                this.text = text;
                this.deliverLogId = deliverLogId;
            }
        }


        public boolean isBound() { return bound; }

        private static final int MAX_SEQ = 0x7FFFFFFF;
        private int nextSeq() {
            // dönen değer: 1 ile MAX_SEQ arasında olmalı
            return seqGen.getAndUpdate(cur -> (cur >= MAX_SEQ ? 1 : cur + 1));
        }

        //wrapper
        public List<String> sendConcatTrSingleShiftUnpacked(SubmitSmReq baseReq, String text) throws Exception {
            if (!bound) throw new IllegalStateException("Session not bound");
            return sender.sendConcatTrSingleShiftUnpacked(baseReq, text);
        }


        public void onIncomingPduBytes(byte[] data) {
            String rawHex = bytesToHex(data);
            PduHeader h = parseHeader(data);

            try {
                Pdu pdu = decoder.decode(data);
                log.debug("RX: {}", pdu);


                if (pdu instanceof GenericNack gn) {
                    int seq = gn.getSequenceNumber();
                    int st = gn.getCommandStatus();

                    log.error("[GENERIC_NACK] RX seq={} status=0x{}", seq, String.format("%08X", st));

                    // ilgili bekleyeni bitirir
                    pending.fail(seq, new RuntimeException("GENERIC_NACK status=0x" + String.format("%08X", st)));


                    return;
                }


                boolean isEnquire = (pdu instanceof EnquireLinkReq) || (pdu instanceof EnquireLinkResp);

                long inLogId = -1;

                if (dao != null && !isEnquire) {
                    try {
                        inLogId = dao.insertPduLog(
                                com.mycompany.smppclient.db.SmppDao.Direction.IN,
                                pdu.getClass().getSimpleName(),
                                h.commandId,
                                h.commandStatus,
                                h.sequence,
                                rawHex,
                                toMap(pdu)
                        );
                    } catch (Exception ex) {
                        log.warn("DB insert (IN) failed", ex);
                    }
                }

                if (pdu instanceof SubmitSmResp ssr) {
                    if (dao != null) {
                        try {
                            int seq = ssr.getSequenceNumber();
                            String mid = normalizeMessageId(ssr.getMessageId());

                            // 1) RAM’den submit’i çek
                            SmppSender.PendingSubmit ps = sender.consumePendingSubmit(sessionId, seq);
                            if (ps == null) {
                                log.warn("SubmitSmResp geldi ama pending submit yok! sessionId={} seq={} mid={}",
                                        sessionId, seq, mid);
                                return;
                            }

                            // 2) submit tablosuna INSERT (message_id artık belli)
                            long submitId = dao.insertSubmitOnResp(
                                    ps.sessionId,
                                    ps.systemId,
                                    ps.submitSeq,
                                    ps.srcAddr,
                                    ps.dstAddr,
                                    ps.dataCoding,
                                    ps.esmClass,
                                    ps.submitSmHex,
                                    ssr.getCommandStatus(),
                                    mid,
                                    ps.submitLogId,
                                    inLogId // submit_sm_resp IN pdu_log id
                            );

                            log.info("SUBMIT INSERT OK submitId={} sessionId={} seq={} mid={}",
                                    submitId, sessionId, seq, mid);

                            // 3) bu message_id için daha önce deliver geldiyse flush et
                            java.util.List<PendingDeliver> pend = pendingDeliverByMsgId.remove(mid);
                            if (pend != null) {
                                for (PendingDeliver d : pend) {
                                    try {
                                        dao.insertDeliver(
                                                submitId,
                                                mid,
                                                d.isDlr,
                                                d.srcAddr,
                                                d.dstAddr,
                                                d.dataCoding,
                                                d.esmClass,
                                                d.text,
                                                d.deliverLogId
                                        );
                                    } catch (Exception ex2) {
                                        log.warn("deliver flush insert failed mid={}", mid, ex2);
                                    }
                                }
                                log.info("DELIVER FLUSH OK mid={} count={}", mid, pend.size());
                            }

                        } catch (Exception ex) {
                            log.warn("DB insert submit on submit_sm_resp failed", ex);
                        }
                    }
                }


                if (pdu instanceof DeliverSmReq d) {
                    handleDeliverSm(d, inLogId);
                    return;
                }
                if (pdu instanceof EnquireLinkReq) {
                    EnquireLinkResp r = new EnquireLinkResp();
                    r.setCommandStatus(0);
                    r.setSequenceNumber(pdu.getSequenceNumber());

                    socket.sendBytes(encoder.encode(r));

                    log.info("[ENQUIRE] RX EnquireLinkReq -> TX EnquireLinkResp seq={}", pdu.getSequenceNumber());
                    return;
                }
                if (pdu instanceof UnbindReq) {
                    UnbindResp r = new UnbindResp();
                    r.setCommandStatus(0);
                    r.setSequenceNumber(pdu.getSequenceNumber());

                    sendAndLog(r, "UnbindResp");



                    bound = false;
                    log.info("[UNBIND] RX UnbindReq -> TX UnbindResp seq={}", pdu.getSequenceNumber());
                    return;
                }

                boolean matched = pending.complete(pdu.getSequenceNumber(), pdu);
                if (!matched) {
                    log.info("[UNSOLICITED] RX {} seq={} status={}",
                            pdu.getClass().getSimpleName(),
                            pdu.getSequenceNumber(),
                            pdu.getCommandStatus());
                }

            } catch (Exception e) {
                log.error("RX decode failed: {}", rawHex, e);

                if (e instanceof com.mycompany.smppclient.pdu.exception.DecodeException
                        || e instanceof com.mycompany.smppclient.pdu.exception.InvalidPduException) {

                    if (bound) {
                        log.warn("[RX] decode/invalid pdu -> recover");
                        pending.clearAll(e);
                        bound = false;
                        reconnectAndRebind();
                    }
                }

                if (dao != null) {
                        try {
                            dao.insertPduLog(
                                    com.mycompany.smppclient.db.SmppDao.Direction.IN,
                                    "DECODE_FAILED",
                                    h.commandId,
                                    h.commandStatus,
                                    h.sequence,
                                    rawHex,
                                    Map.of("error", String.valueOf(e.getMessage()))
                            );
                        } catch (Exception ex) {
                            log.warn("DB insert (IN decode_failed) failed", ex);
                        }
                    }
            }
        }


        private static String toHex(byte[] b) {
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02X", x));
            return sb.toString();
        }

        // ----------------- Bind ----------
        public boolean bind(String host, int port, BindTransceiverReq req) throws Exception {
            this.lastHost = host;
            this.lastPort = port;
            this.lastBindReq = req;

            boolean ok = socket.connect(host, port);
            if (!ok) return false;

            socket.setOnPduBytes(this::onIncomingPduBytes);

            socket.setOnDisconnect(() -> {

                pending.clearAll(new RuntimeException("socket disconnected"));

                if (bound) {
                    log.warn("[SOCKET] disconnected -> recover");
                    bound = false;
                    reconnectAndRebind();
                }
            });

            boolean bindOk = doBindOnly(req);
            bound = bindOk;
            return bindOk;
        }

        private boolean doBindOnly(BindTransceiverReq req) throws Exception {
            int seq = nextSeq();
            req.setSequenceNumber(seq);
            req.setCommandStatus(0);

            CompletableFuture<Pdu> f = pending.register(seq);

            sendAndLog(req, "BindTransceiverReq");

            Pdu resp = awaitResponse("BIND", seq, f);
            if (!(resp instanceof BindTransceiverResp)) {
                throw new IllegalStateException("Expected BindTransceiverResp but got: " + resp.getClass().getSimpleName());
            }

            return resp.getCommandStatus() == 0;
        }

        private static BindTransceiverReq copyBind(BindTransceiverReq src) {
            BindTransceiverReq r = new BindTransceiverReq();
            r.setSystemId(src.getSystemId());
            r.setPassword(src.getPassword());
            r.setSystemType(src.getSystemType());
            r.setInterfaceVersion(src.getInterfaceVersion());
            r.setAddrTon(src.getAddrTon());
            r.setAddrNpi(src.getAddrNpi());
            r.setAddressRange(src.getAddressRange());
            return r;
        }
        // ---------- Unbind ----------
        public boolean unbind() throws Exception {
            if (!bound) {
                System.out.println("[UNBIND] not bound -> disconnect");
                socket.disconnect();
                return true;
            }

            UnbindReq req = new UnbindReq();
            req.setCommandStatus(0);

            int seq = nextSeq();
            req.setSequenceNumber(seq);

            CompletableFuture<Pdu> f = pending.register(seq);

            System.out.println("[UNBIND] TX UnbindReq seq=" + seq);


            sendAndLog(req, "UnbindReq");


            try {
                Pdu resp = awaitResponse("UNBIND", seq, f);

                System.out.println("[UNBIND] RX " + resp.getClass().getSimpleName()
                        + " seq=" + resp.getSequenceNumber()
                        + " status=0x" + String.format("%08X", resp.getCommandStatus()));

                if (!(resp instanceof UnbindResp)) {
                    throw new IllegalStateException("Expected UnbindResp but got: " + resp.getClass().getSimpleName());
                }

                boolean ok = resp.getCommandStatus() == 0;
                System.out.println("[UNBIND] RESULT ok=" + ok);

                bound = false;
                socket.disconnect();
                return ok;

            } catch (TimeoutException te) {
                System.out.println("[UNBIND] TIMEOUT waiting UnbindResp (will disconnect anyway)");
                bound = false;
                socket.disconnect();
                return false;
            }
        }




        // ---------- EnquireLink task ----------
        public void startEnquireLinkTask() {
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    if (!bound) return;

                    EnquireLinkReq req = new EnquireLinkReq();
                    req.setCommandStatus(0);
                    int seq = nextSeq();
                    req.setSequenceNumber(seq);

                    CompletableFuture<Pdu> f = pending.register(seq);

                    System.out.println("[ENQUIRE] TX EnquireLinkReq seq=" + seq);
                    socket.sendBytes(encoder.encode(req));

                    Pdu resp = awaitResponse("ENQUIRE", seq, f);


                    System.out.println("[ENQUIRE] RX " + resp.getClass().getSimpleName()
                            + " seq=" + resp.getSequenceNumber()
                            + " status=" + resp.getCommandStatus());

                    if (resp instanceof EnquireLinkResp && resp.getCommandStatus() == 0) {
                        enquireOkCount.incrementAndGet();
                        lastEnquireOkAtMs = System.currentTimeMillis();
                    } else {
                        log.warn("Expected EnquireLinkResp OK, got {}", resp.getClass().getSimpleName());
                    }

                } catch (TimeoutException te) {
                    log.warn("[ENQUIRE] TIMEOUT => assume dead, recover");
                    bound = false;
                    reconnectAndRebind();
                } catch (Exception e) {
                    System.out.println("[ENQUIRE] ERROR: " + e);
                    log.warn("EnquireLink task error", e);
                }
            }, 0, cfg.getEnquireLinkIntervalMs(), TimeUnit.MILLISECONDS);
        }

        public int getEnquireOkCount() {
            return enquireOkCount.get();
        }

        public String sendSubmitSm(SubmitSmReq req) throws Exception {
            if (!bound) throw new IllegalStateException("Session not bound");
            return sender.sendSubmitSm(req);
        }

        private void handleDeliverSm(DeliverSmReq req, long deliverLogId) {
            try {
                // SMSC'ye ACK: DeliverSmResp
                DeliverSmResp resp = new DeliverSmResp();
                resp.setCommandStatus(0);
                resp.setSequenceNumber(req.getSequenceNumber());

                sendAndLog(resp, "DeliverSmResp");

                // Mesaj decode
                String text = decodeDeliverSmText(req);

                byte[] raw = getDeliverSmPayloadBytes(req);
                log.info("[DELIVER_SM RAW] dc=0x{} esm=0x{} sm_hex={}",
                        String.format("%02X", req.getDataCoding()),
                        String.format("%02X", req.getEsmClass()),
                        toHex(raw));


                // Receipt mi?
                boolean byEsm = DeliveryReceiptParser.isDeliveryReceiptByEsmClass(req.getEsmClass());
                boolean byText = DeliveryReceiptParser.looksLikeReceipt(text);


                // Öncelik: esm_class
                boolean isReceipt = byEsm || byText;

                DeliveryReceipt receipt = isReceipt ? DeliveryReceiptParser.parse(text) : null;

                if (dao != null && isReceipt && receipt != null) {
                    try {
                        String mid = normalizeMessageId(receipt.messageId);

                        Long submitId = dao.findSubmitIdByMessageId(mid);

                        if (submitId != null) {
                            // submit varsa direkt deliver insert
                            dao.insertDeliver(
                                    submitId,
                                    mid,
                                    true,
                                    req.getSourceAddr(),
                                    req.getDestinationAddr(),
                                    req.getDataCoding() & 0xFF,
                                    req.getEsmClass() & 0xFF,
                                    text,
                                    deliverLogId
                            );
                            log.info("DELIVER INSERT OK mid={} submitId={}", mid, submitId);

                        } else {
                            // submit yoksa RAM'de beklet
                            pendingDeliverByMsgId
                                    .computeIfAbsent(mid, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                                    .add(new PendingDeliver(
                                            mid,
                                            true,
                                            req.getSourceAddr(),
                                            req.getDestinationAddr(),
                                            req.getDataCoding() & 0xFF,
                                            req.getEsmClass() & 0xFF,
                                            text,
                                            deliverLogId
                                    ));

                            log.warn("DELIVER geldi ama submit yok -> RAM pending mid={}", mid);
                        }

                    } catch (Exception ex) {
                        log.warn("deliver insert/pending failed", ex);
                    }
                }

                log.info("[DLR CHECK] esm=0x{} byEsm={} byText={} isReceipt={}",
                        String.format("%02X", req.getEsmClass()),
                        byEsm, byText, isReceipt);


                DeliverSmEvent ev = new DeliverSmEvent(
                        req,
                        req.getSourceAddr(),
                        req.getDestinationAddr(),
                        req.getEsmClass(),
                        req.getDataCoding(),
                        text,
                        isReceipt,
                        receipt
                );

                //Handler varsa çağır, yoksa logla
                if (incomingHandler != null) {
                    incomingHandler.onDeliverSm(ev);
                } else {
                    log.info("[DELIVER_SM] from={} to={} dc=0x{} esm=0x{} text={}",
                            ev.sourceAddr, ev.destinationAddr,
                            String.format("%02X", ev.dataCoding),
                            String.format("%02X", ev.esmClass),
                            ev.text
                    );

                    if (ev.isDeliveryReceipt && ev.receipt != null) {
                        log.info("[DELIVERY_RECEIPT] {}", ev.receipt);
                    }
                }

            } catch (Exception e) {
                log.warn("handleDeliverSm error", e);
            }
        }

        private String decodeDeliverSmText(DeliverSmReq req) {
            byte[] raw = getDeliverSmPayloadBytes(req);
            if (raw == null || raw.length == 0) return "";

            int dc  = req.getDataCoding() & 0xFF;
            int esm = req.getEsmClass() & 0xFF;

            //  dc kontrol
            if (dc == 0x00) {

                if ((esm & 0x40) != 0 && raw.length > 0) {
                    int udhl = raw[0] & 0xFF;
                    int udhTotalBytes = 1 + udhl;
                    if (udhTotalBytes <= raw.length) {
                        byte[] body = java.util.Arrays.copyOfRange(raw, udhTotalBytes, raw.length);
                        String tUdh = Gsm7Codec.decodeUnpacked(body);
                        if (!looksBroken(tUdh)) return tUdh;
                    }
                }

                String t1 = Gsm7Codec.decodeUnpacked(raw);
                if (!looksBroken(t1)) {
                    return t1;
                }


                String latin = new String(raw, java.nio.charset.StandardCharsets.ISO_8859_1);
                if (DeliveryReceiptParser.looksLikeReceipt(latin)) {
                    return latin;
                }


                return latin;
            }

            // 2) UCS2
            if (dc == 0x08) {
                return new String(raw, java.nio.charset.StandardCharsets.UTF_16BE);
            }

            // 3) fallback
            return new String(raw, java.nio.charset.StandardCharsets.ISO_8859_1);
        }

        private static boolean looksBroken(String s) {
            if (s == null) return true;
            if (s.isEmpty()) return true;

            int bad = 0;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);

                // kontrol karakterleri (tab/newline hariç)
                if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') bad++;

                // çok fazla soru işareti de kötü işaret
                if (c == '?') bad++;
            }

            // oran bazlı karar
            double ratio = (double) bad / (double) s.length();
            return ratio > 0.25; // %25'ten fazlası "kötü" ise bozuk say
        }



        private byte[] getDeliverSmPayloadBytes(DeliverSmReq req) {
            byte[] sm = req.getShortMessage();
            if (sm != null && sm.length > 0) return sm;

            // short_message boşsa TLV message_payload'e bak
            if (req.getOptionalParameters() != null) {
                for (var tlv : req.getOptionalParameters()) {
                    if ((tlv.getTag() & 0xFFFF) == 0x0424) { // message_payload
                        byte[] v = tlv.getValue();
                        if (v != null) return v;
                    }
                }
            }
            return new byte[0];
        }



        private final Object reconnectLock = new Object();
        private volatile boolean recovering = false;

        private void reconnectAndRebind() {
            synchronized (reconnectLock) {
                if (bound) return;
                if (recovering) return;
                recovering = true;
            }

            new Thread(() -> {
                try {
                    final int maxAttempts = Math.max(1, socket.getMaxReconnectAttempts());
                    final int backoffMs  = Math.max(100, socket.getReconnectBackoffMs());

                    while (!bound) {

                        boolean success = false;

                        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                            if (bound) return;

                            try {
                                log.warn("[RECOVER] attempt {}/{} reconnecting to {}:{} ...",
                                        attempt, maxAttempts, lastHost, lastPort);

                                pending.clearAll(new RuntimeException("reconnect"));

                                socket.reconnect(lastHost, lastPort);
                                socket.setOnPduBytes(this::onIncomingPduBytes);

                                BindTransceiverReq br = copyBind(lastBindReq);
                                boolean bindOk = doBindOnly(br);
                                bound = bindOk;

                                if (bindOk) {
                                    log.info("[RECOVER] rebind OK");
                                    success = true;
                                    return;
                                } else {
                                    log.error("[RECOVER] rebind FAILED (status!=0)");
                                }

                            } catch (Exception e) {
                                log.warn("[RECOVER] attempt {}/{} failed: {}",
                                        attempt, maxAttempts, e.toString());
                            }

                            // denemeler arası backoff
                            try { Thread.sleep(backoffMs); } catch (InterruptedException ignored) {}
                        }

                        // Bu turda maxAttempts bitti, yine olmadı → 10 saniye bekle, tekrar tur başlat
                        if (!success && !bound) {
                            log.warn("[RECOVER] all {} attempts failed. waiting 10s then retry batch...", maxAttempts);
                            try { Thread.sleep(10_000); } catch (InterruptedException ignored) {}
                        }
                    }
                } finally {
                    synchronized (reconnectLock) {
                        recovering = false;
                    }
                }
            }, "smpp-recover-thread").start();
        }




        private static String bytesToHex(byte[] b) {
            if (b == null) return "";
            StringBuilder sb = new StringBuilder(b.length * 2);
            for (byte x : b) sb.append(String.format("%02X", x));
            return sb.toString();
        }

        private static final class PduHeader {
            final int commandLength;
            final int commandId;
            final int commandStatus;
            final int sequence;
            PduHeader(int l, int id, int st, int seq) {
                this.commandLength = l;
                this.commandId = id;
                this.commandStatus = st;
                this.sequence = seq;
            }
        }

        private static PduHeader parseHeader(byte[] data) {
            if (data == null || data.length < 16) return new PduHeader(0, 0, 0, 0);
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
            int len = bb.getInt();
            int cid = bb.getInt();
            int st  = bb.getInt();
            int seq = bb.getInt();
            return new PduHeader(len, cid, st, seq);
        }

        /** decoded_json için minimal map */
        private static Map<String, Object> toMap(Pdu pdu) {
            Map<String, Object> m = new HashMap<>();
            if (pdu == null) return m;

            m.put("class", pdu.getClass().getSimpleName());
            m.put("sequence_number", pdu.getSequenceNumber());
            m.put("command_status", pdu.getCommandStatus());
            m.put("command_length", pdu.getCommandLength());

            if (pdu instanceof BindTransceiverReq r) {
                m.put("system_id", r.getSystemId());
                m.put("system_type", r.getSystemType());
                m.put("interface_version", r.getInterfaceVersion() & 0xFF);
                m.put("addr_ton", r.getAddrTon() & 0xFF);
                m.put("addr_npi", r.getAddrNpi() & 0xFF);
                m.put("address_range", r.getAddressRange());
            } else if (pdu instanceof BindTransceiverResp r) {
                m.put("system_id", r.getSystemId());
            } else if (pdu instanceof SubmitSmReq r) {
                m.put("service_type", r.getServiceType());
                m.put("source_addr", r.getSourceAddr());
                m.put("destination_addr", r.getDestinationAddr());
                m.put("esm_class", r.getEsmClass() & 0xFF);
                m.put("data_coding", r.getDataCoding() & 0xFF);
                byte[] sm = r.getShortMessage();
                m.put("sm_length", sm == null ? 0 : sm.length);
                m.put("short_message_hex", bytesToHex(sm));
            } else if (pdu instanceof SubmitSmResp r) {
                m.put("message_id", r.getMessageId());
            } else if (pdu instanceof DeliverSmReq r) {
                m.put("source_addr", r.getSourceAddr());
                m.put("destination_addr", r.getDestinationAddr());
                m.put("esm_class", r.getEsmClass() & 0xFF);
                m.put("data_coding", r.getDataCoding() & 0xFF);
                byte[] sm = r.getShortMessage();
                m.put("sm_length", sm == null ? 0 : sm.length);
                m.put("short_message_hex", bytesToHex(sm));
            }
            return m;
        }

    private void sendAndLog(Pdu pdu, String pduTypeForDb) throws Exception {
        byte[] bytes = encoder.encode(pdu);
        socket.sendBytes(bytes);

        if (dao != null) {
            String rawHex = bytesToHex(bytes);
            PduHeader h = parseHeader(bytes);
            try {
                dao.insertPduLog(
                        com.mycompany.smppclient.db.SmppDao.Direction.OUT,
                        pduTypeForDb,
                        h.commandId,
                        h.commandStatus,
                        h.sequence,
                        rawHex,
                        toMap(pdu)
                );
            } catch (Exception ex) {
                log.warn("DB insert (OUT) failed", ex);
            }
        }
    }


        private Pdu awaitResponse(String op, int seq, CompletableFuture<Pdu> f) throws Exception {
            try {
                return f.get(cfg.getResponseTimeoutMs(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                pending.fail(seq, te);
                log.warn("[{}] TIMEOUT waiting response seq={}", op, seq);

                throw te;
            }
        }


        private static String normalizeMessageId(String s) {
            if (s == null) return null;
            s = s.trim();

            // çok basit normalize:
            // 1) tırnak/boşluk temizle
            s = s.replace("\"", "").trim();

            // 2) sadece rakamsa baştaki 0'ları kırp
            if (s.matches("\\d+")) {
                s = s.replaceFirst("^0+(?!$)", "");
            }
            return s;
        }

        @Override
        public void close() {
            try { scheduler.shutdownNow(); } catch (Exception ignored) {}
            try { socket.disconnect(); } catch (Exception ignored) {}
        }
    }
