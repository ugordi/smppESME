package com.mycompany.smppclient.pdu.encoding;

import java.util.ArrayList;
import java.util.List;

public final class Gsm7Codec {
    private Gsm7Codec() {}

    /** text -> septets (ESC dahil) */
    public static int[] encodeToSeptets(String text) {
        if (text == null || text.isEmpty()) return new int[0];

        List<Integer> out = new ArrayList<>(text.length() * 2);

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            Integer d = Gsm7Tables.DEFAULT_CHAR_TO_SEPTET.get(ch);
            if (d != null) {
                out.add(d & 0x7F);
                continue;
            }

            Integer tr = Gsm7Tables.TR_CHAR_TO_CODE.get(ch);
            if (tr != null) {
                out.add(Gsm7Tables.ESC);   // 0x1B
                out.add(tr & 0x7F);
                continue;
            }

            out.add(0x3F); // '?'
        }

        int[] septets = new int[out.size()];
        for (int i = 0; i < out.size(); i++) septets[i] = out.get(i);
        return septets;
    }



    /** text -> 1 byte = 1 septet (ESC dahil) */
    public static byte[] encodeUnpacked(String text) {
        int[] septets = encodeToSeptets(text);
        byte[] out = new byte[septets.length];
        for (int i = 0; i < septets.length; i++) {
            out[i] = (byte) (septets[i] & 0x7F);
        }
        return out;
    }

    /** unpacked bytes -> text (ESC handling) */
    public static String decodeUnpacked(byte[] unpacked) {
        if (unpacked == null || unpacked.length == 0) return "";

        StringBuilder sb = new StringBuilder(unpacked.length);

        for (int i = 0; i < unpacked.length; i++) {
            int s = unpacked[i] & 0x7F;

            if (s == Gsm7Tables.ESC) {
                if (i + 1 >= unpacked.length) { sb.append('?'); break; }
                int code = unpacked[++i] & 0x7F;
                Character trCh = Gsm7Tables.TR_CODE_TO_CHAR.get(code);
                sb.append(trCh != null ? trCh : '?');
                continue;
            }

            Character ch = Gsm7Tables.DEFAULT_SEPTET_TO_CHAR.get(s);
            sb.append(ch != null ? ch : '?');
        }

        return sb.toString();
    }

    public static byte[] buildUdhConcat8TrSingleShift(int ref, int total, int seq) {
        return new byte[] {
                (byte)0x08,              // UDHL = 8
                (byte)0x00, (byte)0x03,  // IEI=00 (8-bit concat), IEDL=03
                (byte)(ref & 0xFF),      // RR
                (byte)(total & 0xFF),    // TT
                (byte)(seq & 0xFF),      // SS
                (byte)0x24, (byte)0x01, (byte)0x01 // TR single shift: 24 01 01
        };
    }

    public static byte[] withUdh(byte[] udh, byte[] bodyUnpacked) {
        byte[] all = new byte[udh.length + bodyUnpacked.length];
        System.arraycopy(udh, 0, all, 0, udh.length);
        System.arraycopy(bodyUnpacked, 0, all, udh.length, bodyUnpacked.length);
        return all;
    }



    public static List<String> splitTextByUnpackedSeptetBytes(String text, int maxBodyBytes) {
        List<String> parts = new ArrayList<>();
        if (text == null || text.isEmpty()) return parts;

        StringBuilder cur = new StringBuilder();
        int curBytes = 0;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            // bu karakter kaç septet byte eder?
            int addBytes;
            if (Gsm7Tables.DEFAULT_CHAR_TO_SEPTET.containsKey(ch)) {
                addBytes = 1;
            } else if (Gsm7Tables.TR_CHAR_TO_CODE.containsKey(ch)) {
                addBytes = 2; // ESC + code
            } else {
                addBytes = 1; // '?'
            }

            // sığmıyorsa yeni parçaya geç
            if (curBytes + addBytes > maxBodyBytes) {
                parts.add(cur.toString());
                cur.setLength(0);
                curBytes = 0;
            }

            cur.append(ch);
            curBytes += addBytes;
        }

        if (cur.length() > 0) parts.add(cur.toString());
        return parts;
    }

}
