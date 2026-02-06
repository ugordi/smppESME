package com.mycompany.smppclient.pdu;

public class DeliverSmResp extends Pdu {

    private String messageId = "";

    public DeliverSmResp() {
        super(CommandId.DELIVER_SM_RESP);
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = (messageId == null) ? "" : messageId; }

    @Override
    public String toString() {
        return super.toString().replace("}", "") +
                ", messageId='" + messageId + '\'' +
                "}";
    }
}
