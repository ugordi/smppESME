package com.mycompany.smppclient;

import com.mycompany.smppclient.config.SmppProperties;
import com.mycompany.smppclient.pdu.BindTransceiverReq;
import com.mycompany.smppclient.pdu.SubmitSmReq;
import com.mycompany.smppclient.pdu.encoding.Gsm7Codec;
import com.mycompany.smppclient.session.*;
import com.mycompany.smppclient.socket.SmppSocketClient;
import com.mycompany.smppclient.socket.SmppSocketConfig;

import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import com.mycompany.smppclient.db.Db;
import com.mycompany.smppclient.db.SmppDao;

public class SmppMainRunner {

    public static void main(String[] args) throws Exception {
        SmppProperties p = SmppProperties.loadFromTestResources();

        SmppSocketConfig sockCfg = new SmppSocketConfig(5000, 5000, 3, 1000);
        BlockingQueue<DeliverSmEvent> inbox = new LinkedBlockingQueue<>();

        try (SmppSocketClient socket = new SmppSocketClient(sockCfg, null)) {

            SmppSessionConfig cfg = new SmppSessionConfig(15000, 60000);

            // ---- DB ----
            Db db = new Db(p.dbUrl, p.dbUser, p.dbPass);
            SmppDao dao = new SmppDao(db);

            // ---- SESSION ----
            SmppSessionManager sm = new SmppSessionManager(
                    socket,
                    cfg,
                    inbox::offer,
                    dao,
                    "sess-1",
                    p.systemId
            );

            // ---- BIND ----
            BindTransceiverReq bindReq = new BindTransceiverReq();
            bindReq.setSystemId(p.systemId);
            bindReq.setPassword(p.password);
            bindReq.setSystemType(p.systemType);
            bindReq.setInterfaceVersion(p.interfaceVersion);

            bindReq.setAddrTon((byte) 5);
            bindReq.setAddrNpi((byte) 0);
            bindReq.setAddressRange("");

            if (!sm.bind(p.host, p.port, bindReq)) throw new RuntimeException("bind failed");
            sm.startEnquireLinkTask();

            System.out.println("READY. Komut: send <msisdn> <mesaj> | quit");

            // INPUT
            Thread t = new Thread(() -> {
                Scanner sc = new Scanner(System.in);
                while (true) {
                    try {
                        System.out.print("> ");
                        String line = sc.nextLine();
                        if (line == null) continue;
                        line = line.trim();

                        if (line.equalsIgnoreCase("quit")) {
                            try {
                                boolean ok = sm.unbind();
                                System.out.println("[QUIT] unbind ok=" + ok);
                            } catch (Exception e) {
                                System.out.println("[QUIT] unbind error=" + e.getMessage());
                            }
                            System.exit(0);
                        }


                        if (!line.startsWith("send ")) {
                            System.out.println("Kullanım: send +905xxxxxxxxx selam");
                            continue;
                        }

                        String[] parts = line.split(" ", 3);
                        if (parts.length < 3) {
                            System.out.println("Kullanım: send +905xxxxxxxxx selam");
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

                        req.setEsmClass((byte) 0);
                        req.setProtocolId((byte) 0);
                        req.setPriorityFlag((byte) 0);

                        req.setRegisteredDelivery((byte) 3); // DLR iste
                        req.setDataCoding((byte) 0);         // GSM7
                        req.setShortMessage(Gsm7Codec.encodeTurkishSingleShiftBytes(msg));

                        String messageId = sm.sendSubmitSm(req);
                        System.out.println("[SUBMIT_SM] message_id=" + messageId);

                    } catch (Exception e) {
                        System.out.println("[SEND ERROR] " + e.getMessage());
                    }
                }
            });
            t.setDaemon(true);
            t.start();

            while (true) {
                DeliverSmEvent ev = inbox.take();
                System.out.println("[DELIVER_SM] esm=0x" + String.format("%02X", ev.esmClass)
                        + " dc=0x" + String.format("%02X", ev.dataCoding)
                        + " from=" + ev.sourceAddr
                        + " to=" + ev.destinationAddr
                        + " text=" + ev.text);

                if (ev.isDeliveryReceipt && ev.receipt != null) {
                    System.out.println("[DLR] " + ev.receipt);
                }
            }
        }
    }
}