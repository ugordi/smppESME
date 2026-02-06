package com.mycompany.smppclient.pdu;

public class EnquireLinkReq extends Pdu {
    public EnquireLinkReq() {
        super(CommandId.ENQUIRE_LINK);
    }
}