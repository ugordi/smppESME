package com.mycompany.smppclient.session;

import com.mycompany.smppclient.pdu.DeliverSmReq;

public final class DeliverSmEvent {
    public final DeliverSmReq raw;

    public final String sourceAddr;
    public final String destinationAddr;
    public final byte esmClass;
    public final byte dataCoding;

    /** decode edilmiş mesaj */
    public final String text;

    /** receipt gibi görünüyo mu */
    public final boolean isDeliveryReceipt;

    /** receipt parse edilebildiyse */
    public final DeliveryReceipt receipt;

    public DeliverSmEvent(
            DeliverSmReq raw,
            String sourceAddr,
            String destinationAddr,
            byte esmClass,
            byte dataCoding,
            String text,
            boolean isDeliveryReceipt,
            DeliveryReceipt receipt
    ) {
        this.raw = raw;
        this.sourceAddr = sourceAddr;
        this.destinationAddr = destinationAddr;
        this.esmClass = esmClass;
        this.dataCoding = dataCoding;
        this.text = text;
        this.isDeliveryReceipt = isDeliveryReceipt;
        this.receipt = receipt;
    }
}
