package com.mycompany.smppclient.pdu;

import com.mycompany.smppclient.pdu.tlv.OptionalParameter;

import java.util.ArrayList;
import java.util.List;

public class SubmitSmReq extends Pdu {
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

    public SubmitSmReq() {
        super(CommandId.SUBMIT_SM);
    }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public byte getSourceAddrTon() { return sourceAddrTon; }
    public void setSourceAddrTon(byte sourceAddrTon) { this.sourceAddrTon = sourceAddrTon; }

    public byte getSourceAddrNpi() { return sourceAddrNpi; }
    public void setSourceAddrNpi(byte sourceAddrNpi) { this.sourceAddrNpi = sourceAddrNpi; }

    public String getSourceAddr() { return sourceAddr; }
    public void setSourceAddr(String sourceAddr) { this.sourceAddr = sourceAddr; }

    public byte getDestAddrTon() { return destAddrTon; }
    public void setDestAddrTon(byte destAddrTon) { this.destAddrTon = destAddrTon; }

    public byte getDestAddrNpi() { return destAddrNpi; }
    public void setDestAddrNpi(byte destAddrNpi) { this.destAddrNpi = destAddrNpi; }

    public String getDestinationAddr() { return destinationAddr; }
    public void setDestinationAddr(String destinationAddr) { this.destinationAddr = destinationAddr; }

    public byte getEsmClass() { return esmClass; }
    public void setEsmClass(byte esmClass) { this.esmClass = esmClass; }

    public byte getProtocolId() { return protocolId; }
    public void setProtocolId(byte protocolId) { this.protocolId = protocolId; }

    public byte getPriorityFlag() { return priorityFlag; }
    public void setPriorityFlag(byte priorityFlag) { this.priorityFlag = priorityFlag; }

    public byte getRegisteredDelivery() { return registeredDelivery; }
    public void setRegisteredDelivery(byte registeredDelivery) { this.registeredDelivery = registeredDelivery; }

    public byte getDataCoding() { return dataCoding; }
    public void setDataCoding(byte dataCoding) { this.dataCoding = dataCoding; }

    public byte[] getShortMessage() { return shortMessage; }
    public void setShortMessage(byte[] shortMessage) { this.shortMessage = shortMessage; }

    public List<OptionalParameter> getOptionalParameters() { return optionalParameters; }

    public void addOptionalParameter(OptionalParameter p) {
        this.optionalParameters.add(p);
    }

    public void addSarTlvs(int refNum, int total, int seq) {
        // 0x020C sar_msg_ref_num -> 2 byte
        byte[] ref = new byte[] { (byte)((refNum >> 8) & 0xFF), (byte)(refNum & 0xFF) };
        addOptionalParameter(new OptionalParameter(0x020C, ref.length, ref));

        // 0x020E sar_total_segments -> 1 byte
        byte[] tot = new byte[] { (byte)(total & 0xFF) };
        addOptionalParameter(new OptionalParameter(0x020E, tot.length, tot));

        // 0x020F sar_segment_seqnum -> 1 byte
        byte[] s = new byte[] { (byte)(seq & 0xFF) };
        addOptionalParameter(new OptionalParameter(0x020F, s.length, s));
    }

    @Override
    public String toString() {
        return super.toString().replace("}", "") +
                ", sourceAddr='" + sourceAddr + '\'' +
                ", destinationAddr='" + destinationAddr + '\'' +
                ", dataCoding=0x" + Integer.toHexString(dataCoding & 0xFF) +
                ", shortMessageLen=" + (shortMessage == null ? 0 : shortMessage.length) +
                ", tlvCount=" + optionalParameters.size() +
                "}";
    }
}
