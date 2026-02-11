package com.mycompany.smppclient.session;

import com.mycompany.smppclient.config.SmppProperties;
import com.mycompany.smppclient.pdu.BindTransceiverReq;
import com.mycompany.smppclient.pdu.SubmitSmReq;
import com.mycompany.smppclient.pdu.encoding.Gsm7Codec;
import com.mycompany.smppclient.socket.SmppSocketClient;
import com.mycompany.smppclient.socket.SmppSocketConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("real")
public class SmppDeliverSmReceiptRealSMSCIT {

    @Test
    void realSMSC_submit_and_wait_delivery_receipt_deliverSm_ok() throws Exception {
        SmppProperties p = SmppProperties.loadFromTestResources();

        SmppSocketConfig sockCfg = new SmppSocketConfig(5000, 5000, 1, 500);

        BlockingQueue<DeliverSmEvent> inbox = new LinkedBlockingQueue<>();

        IncomingMessageHandler handler = inbox::offer;

        try (SmppSocketClient socket = new SmppSocketClient(sockCfg, null)) {

            SmppSessionConfig cfg = new SmppSessionConfig(15000, 30000);
            SmppSessionManager sm = new SmppSessionManager(socket, cfg, handler);

            // ---- BIND ----
            BindTransceiverReq bindReq = new BindTransceiverReq();
            bindReq.setSystemId(p.systemId);
            bindReq.setPassword(p.password);
            bindReq.setSystemType(p.systemType);
            bindReq.setInterfaceVersion(p.interfaceVersion);
            bindReq.setAddrTon(p.addrTon);
            bindReq.setAddrNpi(p.addrNpi);
            bindReq.setAddressRange(p.addressRange);

            assertTrue(sm.bind(p.host, p.port, bindReq));
            assertTrue(sm.isBound());

            sm.startEnquireLinkTask();

            // ---- SUBMIT_SM ----
            SubmitSmReq req = new SubmitSmReq();
            req.setServiceType("");
            req.setSourceAddrTon((byte) 0);
            req.setSourceAddrNpi((byte) 0);
            req.setSourceAddr("nettest");

            req.setDestAddrTon((byte) 1);
            req.setDestAddrNpi((byte) 1);

            String dest = SmppSender.normalizeMsisdn("+90 506 142 21 56");
            req.setDestinationAddr(dest);

            req.setEsmClass((byte) 0);
            req.setProtocolId((byte) 0);
            req.setPriorityFlag((byte) 0);

            // receipt iste
            req.setRegisteredDelivery((byte) 1);

            // GSM7
            req.setDataCoding((byte) 0);
            byte[] msgBytes = Gsm7Codec.encodeUnpacked("selam");
            req.setShortMessage(msgBytes);

            String messageId = sm.sendSubmitSm(req);
            System.out.println("[REAL] submit_sm message_id=" + messageId);

            assertNotNull(messageId);
            assertFalse(messageId.isBlank());

            // ---- WAIT DELIVER_SM  ----

            DeliverSmEvent ev = inbox.poll(6000, TimeUnit.SECONDS);

            assertNotNull(ev, "Expected deliver_sm event (receipt) but nothing arrived within timeout.");

            System.out.println("[REAL] DELIVER_SM from=" + ev.sourceAddr + " to=" + ev.destinationAddr
                    + " dc=0x" + String.format("%02X", ev.dataCoding)
                    + " esm=0x" + String.format("%02X", ev.esmClass));
            System.out.println("[REAL] DELIVER_SM text=" + ev.text);


            assertTrue(ev.isDeliveryReceipt, "Expected a delivery receipt deliver_sm, but got non-receipt deliver_sm");

            if (ev.receipt != null) {
                System.out.println("[REAL] RECEIPT parsed=" + ev.receipt);
            }

            // ---- UNBIND ----
            assertTrue(sm.unbind());
            assertFalse(sm.isBound());

            sm.close();
        }
    }
}
