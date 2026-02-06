package com.mycompany.smppclient.pdu;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class AllPduPojoTest {

    @Test
    void allPdus_basicPojoContracts() {
        // --- Bind
        BindTransceiverReq bindReq = new BindTransceiverReq();
        bindReq.setSequenceNumber(10);
        bindReq.setCommandStatus(0);
        bindReq.setSystemId("sys");
        bindReq.setPassword("pw");
        bindReq.setSystemType("cp");
        bindReq.setAddrTon((byte) 0);
        bindReq.setAddrNpi((byte) 0);
        bindReq.setAddressRange("");

        assertEquals(CommandId.BIND_TRANSCEIVER, bindReq.getCommandId());
        assertEquals(10, bindReq.getSequenceNumber());
        assertFalse(bindReq.toString().isBlank());
        assertTrue(bindReq.toString().contains("sys"));

        BindTransceiverResp bindResp = new BindTransceiverResp();
        bindResp.setSequenceNumber(10);
        bindResp.setCommandStatus(0);
        bindResp.setSystemId("smsc");
        assertEquals(CommandId.BIND_TRANSCEIVER_RESP, bindResp.getCommandId());
        assertTrue(bindResp.toString().contains("smsc"));

        // --- Unbind
        UnbindReq unbindReq = new UnbindReq();
        unbindReq.setSequenceNumber(11);
        assertEquals(CommandId.UNBIND, unbindReq.getCommandId());
        assertFalse(unbindReq.toString().isBlank());

        UnbindResp unbindResp = new UnbindResp();
        unbindResp.setSequenceNumber(11);
        assertEquals(CommandId.UNBIND_RESP, unbindResp.getCommandId());
        assertFalse(unbindResp.toString().isBlank());

        // --- EnquireLink
        EnquireLinkReq elReq = new EnquireLinkReq();
        elReq.setSequenceNumber(12);
        assertEquals(CommandId.ENQUIRE_LINK, elReq.getCommandId());

        EnquireLinkResp elResp = new EnquireLinkResp();
        elResp.setSequenceNumber(12);
        assertEquals(CommandId.ENQUIRE_LINK_RESP, elResp.getCommandId());

        // --- SubmitSm
        SubmitSmReq submitReq = new SubmitSmReq();
        submitReq.setSequenceNumber(20);
        submitReq.setCommandStatus(0);
        submitReq.setSourceAddrTon((byte) 1);
        submitReq.setSourceAddrNpi((byte) 1);
        submitReq.setSourceAddr("SENDER");
        submitReq.setDestAddrTon((byte) 1);
        submitReq.setDestAddrNpi((byte) 1);
        submitReq.setDestinationAddr("905551112233");
        submitReq.setDataCoding((byte) 0x00);
        submitReq.setShortMessage("hi".getBytes(StandardCharsets.US_ASCII));

        assertEquals(CommandId.SUBMIT_SM, submitReq.getCommandId());
        assertTrue(submitReq.toString().contains("SENDER"));
        assertTrue(submitReq.toString().contains("905551112233"));

        SubmitSmResp submitResp = new SubmitSmResp();
        submitResp.setSequenceNumber(20);
        submitResp.setCommandStatus(0);
        submitResp.setMessageId("abc123");
        assertEquals(CommandId.SUBMIT_SM_RESP, submitResp.getCommandId());
        assertTrue(submitResp.toString().contains("abc123"));


        DeliverSmReq deliverReq = new DeliverSmReq();
        deliverReq.setSequenceNumber(30);
        deliverReq.setCommandStatus(0);
        deliverReq.setDataCoding((byte) 0x00);
        deliverReq.setShortMessage("msg".getBytes(StandardCharsets.US_ASCII));
        assertEquals(CommandId.DELIVER_SM, deliverReq.getCommandId());
        assertFalse(deliverReq.toString().isBlank());


        DeliverSmResp deliverResp = new DeliverSmResp();
        deliverResp.setSequenceNumber(30);
        deliverResp.setCommandStatus(0);
        deliverResp.setMessageId("ok");
        assertEquals(CommandId.DELIVER_SM_RESP, deliverResp.getCommandId());
        assertTrue(deliverResp.toString().contains("ok"));


        GenericNack nack = new GenericNack();
        nack.setSequenceNumber(99);
        nack.setCommandStatus(0x00000003); // örnek status
        assertEquals(CommandId.GENERIC_NACK, nack.getCommandId());
        assertFalse(nack.toString().isBlank());
    }
}
