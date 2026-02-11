package com.mycompany.smppclient.pdu;

public class SubmitSmResp extends Pdu {
    private String messageId;

    public SubmitSmResp() {
        super(CommandId.SUBMIT_SM_RESP);
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }


    @Override
    public String toString() {
        return super.toString().replace("}", "") +
                ", messageId='" + messageId + '\'' +
                "}";
    }
}
