package com.mycompany.smppclient.session;

import java.util.Locale;


public final class DeliveryReceiptParser {

    private DeliveryReceiptParser() {}

    public static boolean isDeliveryReceiptByEsmClass(byte esmClass) {
        return (esmClass & 0x04) == 0x04;
    }

    public static boolean looksLikeReceipt(String text) {
        if (text == null) return false;
        String t = text.toLowerCase(Locale.ROOT);

        return t.contains("id:") && t.contains("stat:");
    }

    public static DeliveryReceipt parse(String text) {
        if (text == null) return null;
        if (!looksLikeReceipt(text)) return null;

        DeliveryReceipt r = new DeliveryReceipt();
        r.text = text;


        String lower = text.toLowerCase(Locale.ROOT);

        r.submitDate = extractAfter(lower, text, "submit date:");
        r.doneDate = extractAfter(lower, text, "done date:");

        r.messageId = extractTokenValue(lower, text, "id:");
        r.stat = extractTokenValue(lower, text, "stat:");
        r.err = extractTokenValue(lower, text, "err:");

        return r;
    }

    private static String extractTokenValue(String lower, String original, String key) {
        int idx = lower.indexOf(key);
        if (idx < 0) return null;

        int start = idx + key.length();
        int end = lower.indexOf(' ', start);
        if (end < 0) end = original.length();

        String val = original.substring(start, end).trim();
        return val.isEmpty() ? null : val;
    }

    private static String extractAfter(String lower, String original, String key) {
        int idx = lower.indexOf(key);
        if (idx < 0) return null;

        int start = idx + key.length();
        // bir sonraki bilinen anahtar gelene kadar almayı dene
        int end = original.length();

        int nextStat = lower.indexOf(" stat:", start);
        if (nextStat > 0) end = Math.min(end, nextStat);

        int nextErr = lower.indexOf(" err:", start);
        if (nextErr > 0) end = Math.min(end, nextErr);

        int nextText = lower.indexOf(" text:", start);
        if (nextText > 0) end = Math.min(end, nextText);

        String val = original.substring(start, end).trim();
        return val.isEmpty() ? null : val;
    }
}
