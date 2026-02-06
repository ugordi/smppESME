package com.mycompany.smppclient.pdu.tlv;

import java.util.Arrays;

public class OptionalParameter {
    private int tag;        // 2 byte (0..65535)
    private int length;     // 2 byte
    private byte[] value;   // length bytes

    public OptionalParameter(int tag, int length, byte[] value) {
        this.tag = tag;
        this.length = length;
        this.value = value;
    }

    public int getTag() { return tag; }
    public void setTag(int tag) { this.tag = tag; }

    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }

    public byte[] getValue() { return value; }
    public void setValue(byte[] value) { this.value = value; }

    @Override
    public String toString() {
        return "OptionalParameter{tag=0x" + Integer.toHexString(tag) +
                ", length=" + length +
                ", value=" + Arrays.toString(value) + "}";
    }
}
