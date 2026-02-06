package com.mycompany.smppclient.pdu.encoding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public final class Gsm7Codec {

    private Gsm7Codec() {}

    public static final class Encoded {
        public final byte[] packed;
        public final int septetCount;

        public Encoded(byte[] packed, int septetCount) {
            this.packed = packed;
            this.septetCount = septetCount;
        }
    }

    public static Encoded encodeTurkishSingleShift(String text) {
        int[] septets = encodeTurkishSingleShiftSeptets(text);
        byte[] packed = SeptetPacker.pack(septets);
        return new Encoded(packed, septets.length);
    }

    public static byte[] encodeTurkishSingleShiftBytes(String text) {
        return encodeTurkishSingleShift(text).packed;
    }

    public static int[] encodeTurkishSingleShiftSeptets(String text) {
        if (text == null) return new int[0];

        List<Integer> out = new ArrayList<>(text.length() * 2);

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            Integer d = Gsm7Tables.DEFAULT_CHAR_TO_SEPTET.get(ch);
            if (d != null) {
                out.add(d);
                continue;
            }

            Integer trCode = Gsm7Tables.TR_SINGLE_SHIFT_CHAR_TO_CODE.get(ch);
            if (trCode != null) {
                out.add(Gsm7Tables.ESC);
                out.add(trCode);
                continue;
            }

            Integer q = Gsm7Tables.DEFAULT_CHAR_TO_SEPTET.get('?');
            if (q == null) {
                out.add(0x3F);
            } else {
                out.add(q);
            }
        }

        int[] septets = new int[out.size()];
        for (int i = 0; i < out.size(); i++) septets[i] = out.get(i) & 0x7F;
        return septets;
    }

    public static String decodeTurkishSingleShiftFromSeptets(int[] septets) {
        if (septets == null || septets.length == 0) return "";

        StringBuilder sb = new StringBuilder(septets.length);

        Map<Integer, Character> def = Gsm7Tables.DEFAULT_SEPTET_TO_CHAR;
        Map<Integer, Character> tr = Gsm7Tables.TR_SINGLE_SHIFT_CODE_TO_CHAR;

        for (int i = 0; i < septets.length; i++) {
            int s = septets[i] & 0x7F;

            if (s == Gsm7Tables.ESC) {
                if (i + 1 < septets.length) {
                    int code = septets[++i] & 0x7F;
                    Character tch = tr.get(code);
                    if (tch != null) sb.append(tch);
                    else sb.append('?');
                } else {
                    sb.append('?');
                }
                continue;
            }

            Character c = def.get(s);
            sb.append(c != null ? c : '?');
        }

        return sb.toString();
    }

    public static String decodeTurkishSingleShiftFromPacked(byte[] packed) {
        if (packed == null || packed.length == 0) return "";

        int septetCount = (packed.length * 8) / 7;

        int[] septets = SeptetPacker.unpack(packed, septetCount);
        String decoded = decodeTurkishSingleShiftFromSeptets(septets);


        int end = decoded.length();
        while (end > 0 && decoded.charAt(end - 1) == '@') end--;
        return decoded.substring(0, end);
    }


    public static String decodeTurkishSingleShiftFromBytes(byte[] data) {
        if (data == null || data.length == 0) return "";

        // Byte dizisini int[] septet dizisine çevir (packing yapmadan)
        int[] septets = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            septets[i] = data[i] & 0x7F;
        }


        return decodeTurkishSingleShiftFromSeptets(septets);
    }
}
