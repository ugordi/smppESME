package com.mycompany.smppclient.pdu;

import com.mycompany.smppclient.pdu.tlv.OptionalParameter;

import java.util.ArrayList;
import java.util.List;

public class DeliverSmReq extends Pdu {
    private String serviceType = "";

    private byte sourceAddrTon;
    private byte sourceAddrNpi;
    private String sourceAddr;

    private byte destAddrTon;
    private byte destAddrNpi;
    private String destinationAddr;

    private byte esmClass;
    private byte protocolId;
    private byte priorityFlag;

    private String scheduleDeliveryTime = "";
    private String validityPeriod = "";

    private byte registeredDelivery;
    private byte replaceIfPresentFlag;
    private byte dataCoding;
    private byte smDefaultMsgId;

    private byte[] shortMessage = new byte[0];

    private final List<OptionalParameter> optionalParameters = new ArrayList<>();

    public DeliverSmReq() {
        super(CommandId.DELIVER_SM);
    }

    public byte[] getShortMessage() { return shortMessage; }
    public void setShortMessage(byte[] shortMessage) { this.shortMessage = shortMessage; }

    public byte getDataCoding() { return dataCoding; }
    public void setDataCoding(byte dataCoding) { this.dataCoding = dataCoding; }

    public String getSourceAddr() { return sourceAddr; }
    public void setSourceAddr(String sourceAddr) { this.sourceAddr = sourceAddr; }

    public String getDestinationAddr() { return destinationAddr; }
    public void setDestinationAddr(String destinationAddr) { this.destinationAddr = destinationAddr; }

    public byte getEsmClass() { return esmClass; }
    public void setEsmClass(byte esmClass) { this.esmClass = esmClass; }

    public byte getSourceAddrTon() { return sourceAddrTon; }
    public void setSourceAddrTon(byte v) { this.sourceAddrTon = v; }

    public byte getSourceAddrNpi() { return sourceAddrNpi; }
    public void setSourceAddrNpi(byte v) { this.sourceAddrNpi = v; }

    public byte getDestAddrTon() { return destAddrTon; }
    public void setDestAddrTon(byte v) { this.destAddrTon = v; }

    public byte getDestAddrNpi() { return destAddrNpi; }
    public void setDestAddrNpi(byte v) { this.destAddrNpi = v; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String s) { this.serviceType = s; }

    public byte getProtocolId() { return protocolId; }
    public void setProtocolId(byte v) { this.protocolId = v; }

    public byte getPriorityFlag() { return priorityFlag; }
    public void setPriorityFlag(byte v) { this.priorityFlag = v; }

    public byte getRegisteredDelivery() { return registeredDelivery; }
    public void setRegisteredDelivery(byte v) { this.registeredDelivery = v; }

    public byte getReplaceIfPresentFlag() { return replaceIfPresentFlag; }
    public void setReplaceIfPresentFlag(byte v) { this.replaceIfPresentFlag = v; }

    public byte getSmDefaultMsgId() { return smDefaultMsgId; }
    public void setSmDefaultMsgId(byte v) { this.smDefaultMsgId = v; }

    public String getScheduleDeliveryTime() { return scheduleDeliveryTime; }
    public void setScheduleDeliveryTime(String s) { this.scheduleDeliveryTime = s; }

    public String getValidityPeriod() { return validityPeriod; }
    public void setValidityPeriod(String s) { this.validityPeriod = s; }

    public List<OptionalParameter> getOptionalParameters() { return optionalParameters; }

    @Override
    public String toString() {
        return super.toString().replace("}", "") +
                ", sourceAddr=" + sourceAddr +
                ", destinationAddr=" + destinationAddr +
                ", esmClass=0x" + Integer.toHexString(esmClass & 0xFF) +
                ", dataCoding=0x" + Integer.toHexString(dataCoding & 0xFF) +
                ", shortMessageLen=" + (shortMessage == null ? 0 : shortMessage.length) +
                ", tlvCount=" + optionalParameters.size() +
                "}";
    }
}
