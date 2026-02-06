package com.mycompany.smppclient.session;

public interface IncomingMessageHandler {
    void onDeliverSm(DeliverSmEvent event);
}
