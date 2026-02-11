package com.mycompany.smppclient.pdu;

import com.mycompany.smppclient.pdu.decoder.PduDecoder;
import com.mycompany.smppclient.pdu.encoder.PduEncoder;
import com.mycompany.smppclient.pdu.tlv.OptionalParameter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TlvRoundTripTest {

    private static final int TAG_MESSAGE_PAYLOAD = 0x0424;

    private static String hex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }

    private static String tagHex(int tag) {
        return String.format("%04X", tag & 0xFFFF);
    }

    private static void printTlvs(String title, List<OptionalParameter> tlvs) {
        System.out.println(title);
        if (tlvs == null || tlvs.isEmpty()) {
            System.out.println("  (no TLVs)");
            return;
        }
        for (OptionalParameter op : tlvs) {
            byte[] v = op.getValue();
            String vHex = hex(v);
            String vText = new String(v == null ? new byte[0] : v, StandardCharsets.ISO_8859_1);
            System.out.println("  TLV tag=0x" + tagHex(op.getTag())
                    + " len=" + op.getLength()
                    + " valHex=" + vHex
                    + " valText=\"" + vText + "\"");
        }
    }

    @Test
    void submitSm_messagePayload_tlv_roundTrip_ok() {
        System.out.println("==== TEST: submitSm_messagePayload_tlv_roundTrip_ok ====");

        // given
        PduEncoder enc = new PduEncoder();
        PduDecoder dec = new PduDecoder();

        SubmitSmReq req = new SubmitSmReq();
        req.setCommandStatus(0);
        req.setSequenceNumber(123);

        // minimum required alanlar (senin encoder requireNotNull'larına göre)
        req.setServiceType("");
        req.setSourceAddrTon((byte) 5);
        req.setSourceAddrNpi((byte) 0);
        req.setSourceAddr("nettest");

        req.setDestAddrTon((byte) 1);
        req.setDestAddrNpi((byte) 1);
        req.setDestinationAddr("905000000000");

        req.setEsmClass((byte) 0);
        req.setProtocolId((byte) 0);
        req.setPriorityFlag((byte) 0);
        req.setRegisteredDelivery((byte) 0);
        req.setDataCoding((byte) 0x00);

        req.setShortMessage(new byte[0]);

        byte[] payload = "hello tlv payload".getBytes(StandardCharsets.ISO_8859_1);
        req.getOptionalParameters().add(new OptionalParameter(TAG_MESSAGE_PAYLOAD, payload.length, payload));

        System.out.println("[GIVEN] payloadText=\"hello tlv payload\" payloadHex=" + hex(payload));
        printTlvs("[GIVEN] TLVs:", req.getOptionalParameters());

        // when
        byte[] pduBytes = enc.encode(req);
        System.out.println("[ENCODED] PDU HEX:\n" + hex(pduBytes));

        Pdu decoded = dec.decode(pduBytes);

        // then
        assertTrue(decoded instanceof SubmitSmReq, "Decoded PDU type should be SubmitSmReq");
        SubmitSmReq d = (SubmitSmReq) decoded;

        List<OptionalParameter> tlvs = d.getOptionalParameters();
        printTlvs("[DECODED] TLVs:", tlvs);

        assertNotNull(tlvs);
        assertFalse(tlvs.isEmpty(), "Decoded TLV list should not be empty");

        OptionalParameter mp = tlvs.stream()
                .filter(x -> (x.getTag() & 0xFFFF) == TAG_MESSAGE_PAYLOAD)
                .findFirst()
                .orElseThrow(() -> new AssertionError("message_payload TLV not found after decode"));

        System.out.println("[ASSERT] message_payload found. len=" + mp.getLength() + " hex=" + hex(mp.getValue()));

        assertArrayEquals(payload, mp.getValue(), "message_payload TLV value must round-trip exactly");
        assertEquals(payload.length, mp.getLength(), "Decoded TLV length must match value length");

        System.out.println("==== PASS: submitSm_messagePayload_tlv_roundTrip_ok ====\n");
    }

    @Test
    void deliverSm_messagePayload_tlv_roundTrip_ok() {
        System.out.println("==== TEST: deliverSm_messagePayload_tlv_roundTrip_ok ====");

        // given
        PduEncoder enc = new PduEncoder();
        PduDecoder dec = new PduDecoder();

        DeliverSmReq req = new DeliverSmReq();
        req.setCommandStatus(0);
        req.setSequenceNumber(456);

        req.setDataCoding((byte) 0x00);
        req.setShortMessage(new byte[0]);

        byte[] payload = "dlv payload".getBytes(StandardCharsets.ISO_8859_1);
        req.getOptionalParameters().add(new OptionalParameter(TAG_MESSAGE_PAYLOAD, payload.length, payload));

        System.out.println("[GIVEN] payloadText=\"dlv payload\" payloadHex=" + hex(payload));
        printTlvs("[GIVEN] TLVs:", req.getOptionalParameters());

        // when
        byte[] pduBytes = enc.encode(req);
        System.out.println("[ENCODED] PDU HEX:\n" + hex(pduBytes));

        Pdu decoded = dec.decode(pduBytes);

        // then
        assertTrue(decoded instanceof DeliverSmReq, "Decoded PDU type should be DeliverSmReq");
        DeliverSmReq d = (DeliverSmReq) decoded;

        printTlvs("[DECODED] TLVs:", d.getOptionalParameters());

        OptionalParameter mp = d.getOptionalParameters().stream()
                .filter(x -> (x.getTag() & 0xFFFF) == TAG_MESSAGE_PAYLOAD)
                .findFirst()
                .orElseThrow(() -> new AssertionError("message_payload TLV not found after decode"));

        System.out.println("[ASSERT] message_payload found. len=" + mp.getLength() + " hex=" + hex(mp.getValue()));

        assertArrayEquals(payload, mp.getValue(), "message_payload TLV value must round-trip exactly");
        assertEquals(payload.length, mp.getLength(), "Decoded TLV length must match value length");

        System.out.println("==== PASS: deliverSm_messagePayload_tlv_roundTrip_ok ====\n");
    }
}
