package com.mycompany.smppclient.pdu.encoding;

import java.util.Arrays;


public final class SeptetPacker {

    private SeptetPacker() {}

    public static byte[] pack(int[] septets) {
        if (septets == null) return new byte[0];

        int outLen = (septets.length * 7 + 7) / 8;
        byte[] out = new byte[outLen];

        int bitPos = 0;
        for (int s : septets) {
            int septet = s & 0x7F;

            int byteIndex = bitPos / 8;
            int shift = bitPos % 8;

            out[byteIndex] |= (byte) ((septet << shift) & 0xFF);

            if (shift > 1) {
                if (byteIndex + 1 < outLen) {
                    out[byteIndex + 1] |= (byte) ((septet >> (8 - shift)) & 0xFF);
                }
            }

            bitPos += 7;
        }

        return out;
    }


    public static int[] unpack(byte[] packed, int septetCount) {
        if (septetCount <= 0) return new int[0];
        if (packed == null || packed.length == 0) return new int[septetCount];

        int[] septets = new int[septetCount];

        int bitPos = 0;
        for (int i = 0; i < septetCount; i++) {
            int byteIndex = bitPos / 8;
            int shift = bitPos % 8;

            int b0 = (byteIndex < packed.length) ? (packed[byteIndex] & 0xFF) : 0;
            int b1 = (byteIndex + 1 < packed.length) ? (packed[byteIndex + 1] & 0xFF) : 0;

            int v = (b0 >> shift) & 0xFF;
            if (shift > 1) {
                int carry = (b1 << (8 - shift)) & 0xFF;
                v |= carry;
            }

            septets[i] = v & 0x7F;
            bitPos += 7;
        }

        return septets;
    }

    public static String toHex(byte[] b) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }

    public static String septetsToString(int[] s) {
        return Arrays.toString(s);
    }
}
