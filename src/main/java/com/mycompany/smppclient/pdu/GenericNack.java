package com.mycompany.smppclient.pdu;

public class GenericNack extends Pdu {
    public GenericNack() {
        super(CommandId.GENERIC_NACK);
    }
}