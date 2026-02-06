package com.mycompany.smppclient.pdu.exception;

public class InvalidPduException extends RuntimeException {
    public InvalidPduException(String message) {
        super(message);
    }
}