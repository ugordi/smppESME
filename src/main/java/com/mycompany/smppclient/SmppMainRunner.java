package com.mycompany.smppclient;

import com.mycompany.smppclient.config.SmppProperties;
import com.mycompany.smppclient.db.Db;
import com.mycompany.smppclient.db.SmppDao;
import com.mycompany.smppclient.pdu.BindTransceiverReq;
import com.mycompany.smppclient.pdu.SubmitSmReq;
import com.mycompany.smppclient.session.DeliverSmEvent;
import com.mycompany.smppclient.session.SmppSender;
import com.mycompany.smppclient.session.SmppSessionConfig;
import com.mycompany.smppclient.session.SmppSessionManager;
import com.mycompany.smppclient.socket.SmppSocketClient;
import com.mycompany.smppclient.socket.SmppSocketConfig;

import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class SmppMainRunner {

    public static void main(String[] args) throws Exception {
        SmppProperties p = SmppProperties.loadFromTestResources();

        // ---- DB ----
        Db db = new Db(p.dbUrl, p.dbUser, p.dbPass);
        SmppDao dao = new SmppDao(db);

        // ---- 1 hesap (aynı host/port/system ile 2 session açacaksın) ----
        SmppDao.SmscAccount a1 = dao.loadSmscAccountByName("smscdef");
        if (a1 == null) throw new RuntimeException("DB’de smpp.smsc_account name='smscdef' yok / pasif");

        // ---- socket cfg ----
        SmppSocketConfig sockCfg = new SmppSocketConfig(5000, 5000, 3, 1000);

        // ---- 2 socket ----
        SmppSocketClient socket1 = new SmppSocketClient(sockCfg, null);
        SmppSocketClient socket2 = new SmppSocketClient(sockCfg, null);

        // ---- 2 inbox ----
        BlockingQueue<DeliverSmEvent> inbox1 = new LinkedBlockingQueue<>();
        BlockingQueue<DeliverSmEvent> inbox2 = new LinkedBlockingQueue<>();

        // ---- session id ----
        String sessionId1 = "sess-1-" + UUID.randomUUID();
        String sessionId2 = "sess-2-" + UUID.randomUUID();

        // ---- flags ----
        AtomicBoolean alive1 = new AtomicBoolean(false); // başlangıçta KAPALI
        AtomicBoolean alive2 = new AtomicBoolean(false); // başlangıçta KAPALI

        // ---- session cfg ----
        SmppSessionConfig cfg = new SmppSessionConfig(15000, 30000);

        // ---- 2 session manager ----
        SmppSessionManager sm1 = new SmppSessionManager(
                socket1, cfg, inbox1::offer, dao, sessionId1, a1.systemId
        );
        SmppSessionManager sm2 = new SmppSessionManager(
                socket2, cfg, inbox2::offer, dao, sessionId2, a1.systemId
        );

        System.out.println("READY (NOT CONNECTED).");
        System.out.println("Komutlar:");
        System.out.println("  open1 / open 1     (session1 bind/connect)");
        System.out.println("  open2 / open 2     (session2 bind/connect)");
        System.out.println("  send1 <msisdn> <msg>");
        System.out.println("  send2 <msisdn> <msg>");
        System.out.println("  quit1 / quit 1     (session1 unbind+disconnect)");
        System.out.println("  quit2 / quit 2     (session2 unbind+disconnect)");
        System.out.println("  quit               (ikisini kapat + çık)");

        // ---- INPUT ----
        Thread input = new Thread(() -> {
            Scanner sc = new Scanner(System.in);

            while (true) {
                try {
                    System.out.print("> ");
                    String line = sc.nextLine();
                    if (line == null) continue;
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    // ---- OPEN1 ----
                    if (line.equalsIgnoreCase("open1") || line.equalsIgnoreCase("open 1")) {
                        if (alive1.get()) {
                            System.out.println("[OPEN1] zaten açık/bound");
                            continue;
                        }

                        BindTransceiverReq bind1 = new BindTransceiverReq();
                        bind1.setSystemId(a1.systemId);
                        bind1.setPassword(a1.password);
                        bind1.setSystemType(a1.systemType);
                        bind1.setInterfaceVersion(a1.interfaceVersion);
                        bind1.setAddrTon(a1.addrTon);
                        bind1.setAddrNpi(a1.addrNpi);
                        bind1.setAddressRange(a1.addressRange);

                        boolean ok = sm1.bind(a1.host, a1.port, bind1);
                        if (!ok) {
                            System.out.println("[OPEN1] bind failed");
                            alive1.set(false);
                            continue;
                        }

                        sm1.startEnquireLinkTask();
                        alive1.set(true);
                        System.out.println("[OPEN1] OK (bound)");
                        continue;
                    }

                    // ---- OPEN2 ----
                    if (line.equalsIgnoreCase("open2") || line.equalsIgnoreCase("open 2")) {
                        if (alive2.get()) {
                            System.out.println("[OPEN2] zaten açık/bound");
                            continue;
                        }

                        BindTransceiverReq bind2 = new BindTransceiverReq();
                        bind2.setSystemId(a1.systemId);
                        bind2.setPassword(a1.password);
                        bind2.setSystemType(a1.systemType);
                        bind2.setInterfaceVersion(a1.interfaceVersion);
                        bind2.setAddrTon(a1.addrTon);
                        bind2.setAddrNpi(a1.addrNpi);
                        bind2.setAddressRange(a1.addressRange);

                        boolean ok = sm2.bind(a1.host, a1.port, bind2);
                        if (!ok) {
                            System.out.println("[OPEN2] bind failed");
                            alive2.set(false);
                            continue;
                        }

                        sm2.startEnquireLinkTask();
                        alive2.set(true);
                        System.out.println("[OPEN2] OK (bound)");
                        continue;
                    }

                    // ---- QUIT1 ----
                    if (line.equalsIgnoreCase("quit1") || line.equalsIgnoreCase("quit 1")) {
                        if (!alive1.get()) {
                            System.out.println("[QUIT1] zaten kapalı");
                            continue;
                        }
                        alive1.set(false);
                        try { System.out.println("[QUIT1] unbind=" + sm1.unbind()); } catch (Exception e) { System.out.println("[QUIT1] unbind err=" + e.getMessage()); }
                        // socket1.close() YOK! (tekrar open1 yapabilmek için)
                        System.out.println("[QUIT1] CLOSED");
                        continue;
                    }

                    // ---- QUIT2 ----
                    if (line.equalsIgnoreCase("quit2") || line.equalsIgnoreCase("quit 2")) {
                        if (!alive2.get()) {
                            System.out.println("[QUIT2] zaten kapalı");
                            continue;
                        }
                        alive2.set(false);
                        try { System.out.println("[QUIT2] unbind=" + sm2.unbind()); } catch (Exception e) { System.out.println("[QUIT2] unbind err=" + e.getMessage()); }

                        System.out.println("[QUIT2] CLOSED");
                        continue;
                    }

                    // ---- QUIT (EXIT) ----
                    if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                        if (alive1.getAndSet(false)) {
                            try { System.out.println("[QUIT] smsc1 unbind=" + sm1.unbind()); } catch (Exception ignored) {}
                        }
                        if (alive2.getAndSet(false)) {
                            try { System.out.println("[QUIT] smsc2 unbind=" + sm2.unbind()); } catch (Exception ignored) {}
                        }
                        try { socket1.close(); } catch (Exception ignored) {}
                        try { socket2.close(); } catch (Exception ignored) {}
                        System.exit(0);
                    }

                    // ---- SEND ----
                    boolean use1 = line.startsWith("send1 ");
                    boolean use2 = line.startsWith("send2 ");
                    if (!use1 && !use2) {
                        System.out.println("Kullanım: open1/open2, send1/send2, quit1/quit2, quit");
                        continue;
                    }

                    String[] parts = line.split(" ", 3);
                    if (parts.length < 3) {
                        System.out.println("Kullanım: send1 +905xxxxxxxxx mesaj  |  send2 +905xxxxxxxxx mesaj");
                        continue;
                    }

                    String msisdn = parts[1];
                    String msg = parts[2];

                    SubmitSmReq req = new SubmitSmReq();
                    req.setServiceType("");

                    req.setSourceAddrTon((byte) 5);
                    req.setSourceAddrNpi((byte) 0);
                    req.setSourceAddr("nettest");

                    req.setDestAddrTon((byte) 1);
                    req.setDestAddrNpi((byte) 1);
                    req.setDestinationAddr(SmppSender.normalizeMsisdn(msisdn));

                    req.setEsmClass((byte) 0x40);
                    req.setProtocolId((byte) 0);
                    req.setPriorityFlag((byte) 0);
                    req.setRegisteredDelivery((byte) 1);
                    req.setDataCoding((byte) 0x00);

                    if (use1) {
                        if (!alive1.get()) {
                            System.out.println("[SMSC1] kapalı. önce open1");
                            continue;
                        }
                        List<String> ids = sm1.sendConcatTrSingleShiftUnpacked(req, msg);
                        System.out.println("[SMSC1 SUBMIT] ids=" + ids);
                    } else {
                        if (!alive2.get()) {
                            System.out.println("[SMSC2] kapalı. önce open2");
                            continue;
                        }
                        List<String> ids = sm2.sendConcatTrSingleShiftUnpacked(req, msg);
                        System.out.println("[SMSC2 SUBMIT] ids=" + ids);
                    }

                } catch (Exception e) {
                    System.out.println("[INPUT ERROR] " + e.getMessage());
                }
            }
        });

        input.setDaemon(true);
        input.start();

        // ---- RX1 ----
        Thread rx1 = new Thread(() -> {
            while (true) {
                try {
                    DeliverSmEvent ev = inbox1.take();
                    System.out.println("[SMSC1 DELIVER_SM] esm=0x" + String.format("%02X", ev.esmClass)
                            + " dc=0x" + String.format("%02X", ev.dataCoding)
                            + " from=" + ev.sourceAddr
                            + " to=" + ev.destinationAddr
                            + " text=" + ev.text);

                    if (ev.isDeliveryReceipt && ev.receipt != null) {
                        System.out.println("[SMSC1 DLR] " + ev.receipt);
                    }
                } catch (Exception ignored) {}
            }
        });
        rx1.setDaemon(true);
        rx1.start();

        // ---- RX2 ----
        Thread rx2 = new Thread(() -> {
            while (true) {
                try {
                    DeliverSmEvent ev = inbox2.take();
                    System.out.println("[SMSC2 DELIVER_SM] esm=0x" + String.format("%02X", ev.esmClass)
                            + " dc=0x" + String.format("%02X", ev.dataCoding)
                            + " from=" + ev.sourceAddr
                            + " to=" + ev.destinationAddr
                            + " text=" + ev.text);

                    if (ev.isDeliveryReceipt && ev.receipt != null) {
                        System.out.println("[SMSC2 DLR] " + ev.receipt);
                    }
                } catch (Exception ignored) {}
            }
        });
        rx2.setDaemon(true);
        rx2.start();

        Thread.currentThread().join();
    }
}
