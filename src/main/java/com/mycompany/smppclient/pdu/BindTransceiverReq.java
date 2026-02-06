package com.mycompany.smppclient.pdu;

public class BindTransceiverReq extends Pdu {

    private String systemId;
    private String password;
    private String systemType;
    private byte interfaceVersion = 0x34; // SMPP 3.4
    private byte addrTon = 0;
    private byte addrNpi = 0;
    private String addressRange = "";

    public BindTransceiverReq() {
        super(CommandId.BIND_TRANSCEIVER);
    }

    public String getSystemId() { return systemId; }
    public void setSystemId(String systemId) { this.systemId = systemId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getSystemType() { return systemType; }
    public void setSystemType(String systemType) { this.systemType = systemType; }

    public byte getInterfaceVersion() { return interfaceVersion; }
    public void setInterfaceVersion(byte interfaceVersion) { this.interfaceVersion = interfaceVersion; }

    public byte getAddrTon() { return addrTon; }
    public void setAddrTon(byte addrTon) { this.addrTon = addrTon; }

    public byte getAddrNpi() { return addrNpi; }
    public void setAddrNpi(byte addrNpi) { this.addrNpi = addrNpi; }

    public String getAddressRange() { return addressRange; }
    public void setAddressRange(String addressRange) { this.addressRange = addressRange; }

    @Override
    public String toString() {
        return super.toString().replace("}", "") +
                ", systemId='" + systemId + '\'' +
                ", systemType='" + systemType + '\'' +
                ", interfaceVersion=0x" + Integer.toHexString(interfaceVersion & 0xFF) +
                ", addrTon=" + (addrTon & 0xFF) +
                ", addrNpi=" + (addrNpi & 0xFF) +
                ", addressRange='" + addressRange + '\'' +
                "}";
    }
}