package com.mycompany.smppclient.session;


public final class DeliveryReceipt {
    public String messageId;
    public String stat;
    public String err;
    public String submitDate;
    public String doneDate;
    public String text;

    @Override
    public String toString() {
        return "DeliveryReceipt{" +
                "messageId='" + messageId + '\'' +
                ", stat='" + stat + '\'' +
                ", err='" + err + '\'' +
                ", submitDate='" + submitDate + '\'' +
                ", doneDate='" + doneDate + '\'' +
                ", text='" + text + '\'' +
                '}';
    }
}
