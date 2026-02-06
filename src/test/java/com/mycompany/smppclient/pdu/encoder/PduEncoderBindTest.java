package com.mycompany.smppclient.pdu.encoder;

import com.mycompany.smppclient.pdu.BindTransceiverReq;
import com.mycompany.smppclient.pdu.CommandId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PduEncoderBindTest {

    @Test
    void bindTransceiverReq_headerAndLength_ok() {
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

        int cmdLen = readIntBE(data, 0);
        assertEquals(data.length, cmdLen, "command_length must equal total byte[] length");

        int cmdId = readIntBE(data, 4);
        assertEquals(CommandId.BIND_TRANSCEIVER, cmdId, "command_id should be BIND_TRANSCEIVER");

        int status = readIntBE(data, 8);
        assertEquals(0, status);

        int seq = readIntBE(data, 12);
        assertEquals(1, seq);
    }

    @Test
    void bindTransceiverReq_knownHex_compare_andPrint() {
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

        String expected =
                "0000001E" +  // command_length
                        "00000009" +  // command_id (bind_transceiver)
                        "00000000" +  // command_status
                        "00000001" +  // sequence_number
                        "73797300" +  // sys\0
                        "707700" +    // pw\0
                        "637000" +    // cp\0
                        "34" +        // interface_version
                        "00" +        // addr_ton
                        "00" +        // addr_npi
                        "00";         // address_range "" -> \0

        String actual = toHex(data);


        System.out.println("\n=== PduEncoderBindTest ===");
        System.out.println("EXPECTED: " + expected.toLowerCase());
        System.out.println("ACTUAL  : " + actual);
        System.out.println("ACTUAL (spaced): " + toHexSpaced(data));
        System.out.println("=========================\n");

        assertEquals(expected.toLowerCase(), actual);
    }

    // ---- Helpers ----
    private static int readIntBE(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) |
                ((b[off + 1] & 0xFF) << 16) |
                ((b[off + 2] & 0xFF) << 8) |
                (b[off + 3] & 0xFF);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte x : bytes) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }

    private static String toHexSpaced(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02x", bytes[i]));
            if (i < bytes.length - 1) sb.append(' ');
        }
        return sb.toString();
    }
}
