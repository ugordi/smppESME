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

    // ---------------- UNPACKED ----------------

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

    public static byte[] prependUdh(String text) {
        // 1) UDH
        byte[] udh = new byte[] { 0x03, 0x24, 0x01, 0x01 };

        // 2) Metni unpacked olarak encode et
        byte[] body = Gsm7Codec.encodeUnpacked(text);

        // 3) UDH + mesajı birleştir
        byte[] all = new byte[udh.length + body.length];
        System.arraycopy(udh, 0, all, 0, udh.length);
        System.arraycopy(body, 0, all, udh.length, body.length);

        return all;
    }

}
