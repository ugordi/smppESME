package com.mycompany.smppclient.pdu.encoding;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class Gsm7Tables {
    static final int ESC = 0x1B;
    static final Map<Character, Integer> DEFAULT_CHAR_TO_SEPTET;
    static final Map<Integer, Character> DEFAULT_SEPTET_TO_CHAR;
    static final Map<Character, Integer> TR_SINGLE_SHIFT_CHAR_TO_CODE;
    static final Map<Integer, Character> TR_SINGLE_SHIFT_CODE_TO_CHAR;

    static {
        Map<Character, Integer> d = new HashMap<>();
        for (char c = 'A'; c <= 'Z'; c++) d.put(c, (int) c);
        for (char c = 'a'; c <= 'z'; c++) d.put(c, (int) c);
        for (char c = '0'; c <= '9'; c++) d.put(c, (int) c);

        d.put(' ', 0x20); d.put('\n', 0x0A); d.put('\r', 0x0D);
        d.put('.', 0x2E); d.put(',', 0x2C); d.put('!', 0x21);
        d.put('?', 0x3F); d.put(':', 0x3A); d.put(';', 0x3B);
        d.put('-', 0x2D); d.put('(', 0x28); d.put(')', 0x29);
        d.put('\'', 0x27); d.put('"', 0x22); d.put('/', 0x2F);
        d.put('@', 0x00); d.put('_', 0x11);

        // Önemli: Ç, ö, ü gibi karakterler Default Tablodadır
        d.put('Ç', 0x09); d.put('Ö', 0x5C); d.put('Ü', 0x5E);
        d.put('ö', 0x7C); d.put('ü', 0x7E);

        DEFAULT_CHAR_TO_SEPTET = Collections.unmodifiableMap(d);
        Map<Integer, Character> rev = new HashMap<>();
        d.forEach((k, v) -> rev.put(v, k));
        DEFAULT_SEPTET_TO_CHAR = Collections.unmodifiableMap(rev);
    }

    static {
        Map<Character, Integer> tr = new HashMap<>();
        // Görseldeki (A.2.1) Tablosuna Göre
        tr.put('Ğ', 0x47); tr.put('İ', 0x49); tr.put('Ş', 0x53);
        tr.put('ç', 0x63); tr.put('ğ', 0x67); tr.put('ı', 0x69); tr.put('ş', 0x73);

        // Extension sembolleri
        tr.put('^', 0x14); tr.put('{', 0x28); tr.put('}', 0x29);
        tr.put('\\', 0x2F); tr.put('[', 0x3C); tr.put('~', 0x3D);
        tr.put(']', 0x3E); tr.put('|', 0x40); tr.put('€', 0x65);

        TR_SINGLE_SHIFT_CHAR_TO_CODE = Collections.unmodifiableMap(tr);
        Map<Integer, Character> trRev = new HashMap<>();
        tr.forEach((k, v) -> trRev.put(v, k)); // Eksik olan başlatma burası
        TR_SINGLE_SHIFT_CODE_TO_CHAR = Collections.unmodifiableMap(trRev);
    }
}