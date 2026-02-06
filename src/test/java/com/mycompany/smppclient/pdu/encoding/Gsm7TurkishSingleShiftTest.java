package com.mycompany.smppclient.pdu.encoding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Gsm7TurkishSingleShiftTest {

    @Test
    void doc_vectors_cagri_seker_encode_pack_unpack_decode_and_log() {
        runAndAssert("Çağrı", "ESC+09,61,ESC+1F,72,ESC+0C");
        runAndAssert("şeker", "ESC+1E,65,6B,65,72");
    }

    // -------------------- helpers --------------------

    private static void runAndAssert(String text, String expectedDocStyle) {
        System.out.println("====================================");
        System.out.println("TEXT      : " + text);

        // 1) Encode -> septets
        int[] septets = Gsm7Codec.encodeTurkishSingleShiftSeptets(text);
        System.out.println("SEPTETS   : " + SeptetPacker.septetsToString(septets));

        // 2) Doküman formatı: ESC+XX,65,6B,...
        String docStyle = toDocStyleEscHex(septets);
        System.out.println("DOC STYLE : " + docStyle);
        System.out.println("EXPECTED  : " + expectedDocStyle);

        // Dokümandaki beklenen çıktıyı birebir yakala
        assertEquals(expectedDocStyle, docStyle, "Doc-style output must match");

        // 3) Pack
        byte[] packed = SeptetPacker.pack(septets);
        System.out.println("PACKEDHEX : " + SeptetPacker.toHex(packed));

        // 4) Unpack
        int[] unpacked = SeptetPacker.unpack(packed, septets.length);
        System.out.println("UNPACKED  : " + SeptetPacker.septetsToString(unpacked));
        assertArrayEquals(septets, unpacked, "Septets must match after pack/unpack");

        // 5) Decode
        String decoded = Gsm7Codec.decodeTurkishSingleShiftFromSeptets(unpacked);
        System.out.println("DECODED   : " + decoded);
        assertEquals(text, decoded, "Decoded text must match original");

        System.out.println("====================================")  ;
    }


    private static String toDocStyleEscHex(int[] septets) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < septets.length; i++) {
            int s = septets[i] & 0x7F;

            if (s == Gsm7Tables.ESC) {
                if (i + 1 < septets.length) {
                    int code = septets[++i] & 0x7F;
                    sb.append("ESC+").append(String.format("%02X", code));
                } else {
                    sb.append("ESC+??");
                }
            } else {
                sb.append(String.format("%02X", s));
            }

            if (i < septets.length - 1) sb.append(",");
        }
        return sb.toString();
    }
}
