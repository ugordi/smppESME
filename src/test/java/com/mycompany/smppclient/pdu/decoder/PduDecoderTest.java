package com.mycompany.smppclient.pdu.decoder;

import com.mycompany.smppclient.pdu.BindTransceiverReq;
import com.mycompany.smppclient.pdu.BindTransceiverResp;
import com.mycompany.smppclient.pdu.encoder.PduEncoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PduDecoderTest {

    @Test
    void decode_bindTransceiverResp_returnsObject_withPrint() {
        BindTransceiverResp resp = new BindTransceiverResp();
        resp.setCommandStatus(0);
        resp.setSequenceNumber(7);
        resp.setSystemId("SMSC");

        PduEncoder enc = new PduEncoder();
        byte[] data = enc.encode(resp);

        PduDecoder dec = new PduDecoder();
        var out = dec.decode(data);

        assertTrue(out instanceof BindTransceiverResp);
        BindTransceiverResp parsed = (BindTransceiverResp) out;

        System.out.println("\n=== decode_bindTransceiverResp_returnsObject ===");
        System.out.println("ENCODED HEX: " + toHex(data));
        System.out.println("ORIGINAL:   " + resp);
        System.out.println("PARSED:     " + parsed);
        System.out.println("MATCH? systemId=" + resp.getSystemId().equals(parsed.getSystemId())
                + ", seq=" + (resp.getSequenceNumber() == parsed.getSequenceNumber())
                + ", status=" + (resp.getCommandStatus() == parsed.getCommandStatus()));
        System.out.println("==============================================\n");

        assertEquals(0, parsed.getCommandStatus());
        assertEquals(7, parsed.getSequenceNumber());
        assertEquals("SMSC", parsed.getSystemId());
    }

    @Test
    void roundTrip_bindTransceiverReq_encodeDecode_fieldsMatch_withPrint() {
        BindTransceiverReq req = new BindTransceiverReq();
        req.setCommandStatus(0);
        req.setSequenceNumber(1);

        req.setSystemId("sys");
        req.setPassword("pw");
        req.setSystemType("cp");
        req.setInterfaceVersion((byte) 0x34);
        req.setAddrTon((byte) 0);
        req.setAddrNpi((byte) 0);
        req.setAddressRange("");

        PduEncoder enc = new PduEncoder();
        byte[] data = enc.encode(req);

        PduDecoder dec = new PduDecoder();
        var out = dec.decode(data);

        assertTrue(out instanceof BindTransceiverReq);
        BindTransceiverReq parsed = (BindTransceiverReq) out;

        System.out.println("\n=== roundTrip_bindTransceiverReq_encodeDecode ===");
        System.out.println("ENCODED HEX: " + toHex(data));
        System.out.println("ORIGINAL:   " + req);
        System.out.println("PARSED:     " + parsed);
        System.out.println("-- Field-by-field match --");
        System.out.println("seq:            " + req.getSequenceNumber() + " == " + parsed.getSequenceNumber());
        System.out.println("status:         " + req.getCommandStatus() + " == " + parsed.getCommandStatus());
        System.out.println("systemId:       " + req.getSystemId() + " == " + parsed.getSystemId());
        System.out.println("password:       " + req.getPassword() + " == " + parsed.getPassword());
        System.out.println("systemType:     " + req.getSystemType() + " == " + parsed.getSystemType());
        System.out.println("interfaceVer:   0x" + String.format("%02X", req.getInterfaceVersion())
                + " == 0x" + String.format("%02X", parsed.getInterfaceVersion()));
        System.out.println("addrTon:        " + (req.getAddrTon() & 0xFF) + " == " + (parsed.getAddrTon() & 0xFF));
        System.out.println("addrNpi:        " + (req.getAddrNpi() & 0xFF) + " == " + (parsed.getAddrNpi() & 0xFF));
        System.out.println("addressRange:   '" + req.getAddressRange() + "' == '" + parsed.getAddressRange() + "'");
        System.out.println("-- Overall: " + (allFieldsMatch(req, parsed) ? "MATCH ✅" : "MISMATCH ❌"));
        System.out.println("===============================================\n");

        assertEquals(req.getSequenceNumber(), parsed.getSequenceNumber());
        assertEquals(req.getSystemId(), parsed.getSystemId());
        assertEquals(req.getPassword(), parsed.getPassword());
        assertEquals(req.getSystemType(), parsed.getSystemType());
        assertEquals(req.getInterfaceVersion(), parsed.getInterfaceVersion());
        assertEquals(req.getAddrTon(), parsed.getAddrTon());
        assertEquals(req.getAddrNpi(), parsed.getAddrNpi());
        assertEquals(req.getAddressRange(), parsed.getAddressRange());
    }

    private static boolean allFieldsMatch(BindTransceiverReq a, BindTransceiverReq b) {
        if (a.getSequenceNumber() != b.getSequenceNumber()) return false;
        if (a.getCommandStatus() != b.getCommandStatus()) return false;
        if (!safeEq(a.getSystemId(), b.getSystemId())) return false;
        if (!safeEq(a.getPassword(), b.getPassword())) return false;
        if (!safeEq(a.getSystemType(), b.getSystemType())) return false;
        if (a.getInterfaceVersion() != b.getInterfaceVersion()) return false;
        if (a.getAddrTon() != b.getAddrTon()) return false;
        if (a.getAddrNpi() != b.getAddrNpi()) return false;
        return safeEq(a.getAddressRange(), b.getAddressRange());
    }

    private static boolean safeEq(String x, String y) {
        if (x == null && y == null) return true;
        if (x == null || y == null) return false;
        return x.equals(y);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte x : bytes) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}