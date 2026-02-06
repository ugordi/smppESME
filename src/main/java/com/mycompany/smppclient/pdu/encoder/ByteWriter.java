package com.mycompany.smppclient.pdu.encoder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class ByteWriter {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public int size() {
        return out.size();
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }

    public void writeByte(int b) {
        out.write(b & 0xFF);
    }

    public void writeBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        out.write(bytes, 0, bytes.length);
    }

    public void writeInt(int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    public void writeCString(String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        writeBytes(bytes);
        writeByte(0x00);
    }

    public void writeShort(int value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    public void writeOctets(byte[] b) {
        if (b == null) return;
        writeBytes(b);
    }
}