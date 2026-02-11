package com.mycompany.smppclient.pdu.encoding;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class Gsm7Tables {
    private Gsm7Tables() {}

    static final int ESC = 0x1B;

    static final Map<Integer, Character> DEFAULT_SEPTET_TO_CHAR;


    static final Map<Character, Integer> DEFAULT_CHAR_TO_SEPTET;

    // Turkish single-shift: ESC + code
    static final Map<Character, Integer> TR_CHAR_TO_CODE;
    static final Map<Integer, Character> TR_CODE_TO_CHAR;

    static {
        Map<Character, Integer> def = new HashMap<>();

        // ASCII harf/rakam (çoğu SMSC zaten bunları destekler)
        for (char c = 'A'; c <= 'Z'; c++) def.put(c, (int) c);
        for (char c = 'a'; c <= 'z'; c++) def.put(c, (int) c);
        for (char c = '0'; c <= '9'; c++) def.put(c, (int) c);

        // temel punctuation
        def.put(' ', 0x20);
        def.put('\n', 0x0A);
        def.put('\r', 0x0D);
        def.put('.', 0x2E);
        def.put(',', 0x2C);
        def.put('!', 0x21);
        def.put('?', 0x3F);
        def.put(':', 0x3A);
        def.put(';', 0x3B);
        def.put('-', 0x2D);
        def.put('(', 0x28);
        def.put(')', 0x29);
        def.put('\'', 0x27);
        def.put('"', 0x22);
        def.put('/', 0x2F);
        def.put('@', 0x00);
        def.put('_', 0x11);


        def.put('Ç', 0x09);
        def.put('Ö', 0x5C);
        def.put('Ü', 0x5E);
        def.put('ö', 0x7C);
        def.put('ü', 0x7E);

        // '?' fallback
        def.put('?', 0x3F);

        DEFAULT_CHAR_TO_SEPTET = Collections.unmodifiableMap(def);

        Map<Integer, Character> defRev = new HashMap<>();
        def.forEach((ch, code) -> defRev.put(code & 0x7F, ch));
        DEFAULT_SEPTET_TO_CHAR = Collections.unmodifiableMap(defRev);

        // --- Turkish single shift codes (ESC + code) ---
        Map<Character, Integer> tr = new HashMap<>();
        tr.put('ğ', 0x67);
        tr.put('Ğ', 0x47);
        tr.put('ş', 0x73);
        tr.put('Ş', 0x53);
        tr.put('ı', 0x69);
        tr.put('İ', 0x49);
        tr.put('ç', 0x63);

        //tr.put('ö', 0x6F);

        TR_CHAR_TO_CODE = Collections.unmodifiableMap(tr);

        Map<Integer, Character> trRev = new HashMap<>();
        tr.forEach((ch, code) -> trRev.put(code & 0x7F, ch));
        TR_CODE_TO_CHAR = Collections.unmodifiableMap(trRev);
    }
}
