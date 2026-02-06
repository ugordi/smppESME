package com.mycompany.smppclient.session;

import com.mycompany.smppclient.config.SmppProperties;
import com.mycompany.smppclient.pdu.BindTransceiverReq;
import com.mycompany.smppclient.pdu.SubmitSmReq;
import com.mycompany.smppclient.socket.SmppSocketClient;
import com.mycompany.smppclient.socket.SmppSocketConfig;
import com.mycompany.smppclient.pdu.encoding.Gsm7Codec;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("real")
public class SmppSubmitSmRealSMSCIT {

    @Test
    void realSMSC_bind_submitSm_getMessageId_unbind_ok() throws Exception {
        SmppProperties p = SmppProperties.loadFromTestResources();

        SmppSocketConfig sockCfg = new SmppSocketConfig(5000, 5000, 1, 500);
        try (SmppSocketClient socket = new SmppSocketClient(sockCfg, null)) {

            SmppSessionConfig cfg = new SmppSessionConfig(8000, 2000);
            SmppSessionManager sm = new SmppSessionManager(socket, cfg);

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

            // ---- SUBMIT_SM ----
            SubmitSmReq req = new SubmitSmReq();

            req.setServiceType("");
            req.setSourceAddrTon((byte) 0);
            req.setSourceAddrNpi((byte) 0);
            req.setSourceAddr("nettest"); // sender id

            req.setDestAddrTon((byte) 0);
            req.setDestAddrNpi((byte) 0);

            String dest = SmppSender.normalizeMsisdn("+90 506 142 21 56");
            req.setDestinationAddr(dest);

            req.setEsmClass((byte) 0);
            req.setProtocolId((byte) 0);
            req.setPriorityFlag((byte) 0);

            // registered_delivery:
            // 0 = isteme
            // 1 = delivery receipt iste
            req.setRegisteredDelivery((byte) 1);

            // data_coding:
            req.setDataCoding((byte) 0);

            byte[] msgBytes = Gsm7Codec.encodeTurkishSingleShiftBytes("selam Çağrı şeker");
            req.setShortMessage(msgBytes);

            String messageId = sm.sendSubmitSm(req);
            System.out.println("[REAL] submit_sm message_id=" + messageId);

            assertNotNull(messageId);
            assertFalse(messageId.isBlank());

            // ---- UNBIND ----
            assertTrue(sm.unbind());
            assertFalse(sm.isBound());

            sm.close();
        }
    }
}
