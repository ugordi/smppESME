package com.mycompany.smppclient.pdu.decoder;
import com.mycompany.smppclient.pdu.exception.DecodeException;

import java.util.Arrays;

public class ByteReader {
    private final byte[] data;
    private int pos;

    public ByteReader(byte[] data) {
        this.data = (data == null) ? new byte[0] : data;
        this.pos = 0;
    }

    public int position() { return pos; }
    public int remaining() { return data.length - pos; }
    public int length() { return data.length; }

    public void skip(int n) {
        ensure(n);
        pos += n;
    }

    public int readInt() {
        ensure(4);
        int v = ((data[pos] & 0xFF) << 24) |
                ((data[pos + 1] & 0xFF) << 16) |
                ((data[pos + 2] & 0xFF) << 8) |
                (data[pos + 3] & 0xFF);
        pos += 4;
        return v;
    }

    public int readShort() {
        ensure(2);
        int v = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        pos += 2;
        return v;
    }

    public int readByte() {
        ensure(1);
        return data[pos++] & 0xFF;
    }

    public byte[] readBytes(int n) {
        ensure(n);
        byte[] out = Arrays.copyOfRange(data, pos, pos + n);
        pos += n;
        return out;
    }

    /**
     * C-octet string: null (0x00) ile biten ASCII string.
     */
    public String readCString() {
        int start = pos;
        while (pos < data.length && data[pos] != 0x00) {
            pos++;
        }
        if (pos >= data.length) {
            throw new DecodeException("CString not terminated with 0x00");
        }
        String s = new String(data, start, pos - start, java.nio.charset.StandardCharsets.US_ASCII);
        pos++; // null terminator
        return s;
    }

    private void ensure(int n) {
        if (pos + n > data.length) {
            throw new DecodeException("Not enough bytes. need=" + n + " remaining=" + remaining());
        }
    }
}
