package com.mycompany.smppclient.pdu.exception;

public class DecodeException extends RuntimeException {
    public DecodeException(String message) { super(message); }
    public DecodeException(String message, Throwable cause) { super(message, cause); }
}