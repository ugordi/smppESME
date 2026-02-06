package com.mycompany.smppclient.pdu;

public class UnbindReq extends Pdu {
    public UnbindReq() {
        super(CommandId.UNBIND);
    }
}