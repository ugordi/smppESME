package com.mycompany.smppclient.pdu;

public class BindTransceiverResp extends Pdu {
    private String systemId;

    public BindTransceiverResp() {
        super(CommandId.BIND_TRANSCEIVER_RESP);
    }

    public String getSystemId() { return systemId; }
    public void setSystemId(String systemId) { this.systemId = systemId; }

    @Override
    public String toString() {
        return super.toString().replace("}", "") +
                ", systemId='" + systemId + '\'' +
                "}";
    }
}
