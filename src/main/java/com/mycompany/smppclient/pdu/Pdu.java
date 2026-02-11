package com.mycompany.smppclient.pdu;

import com.mycompany.smppclient.pdu.tlv.OptionalParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class Pdu {
    private int commandLength;     // total PDU length
    private int commandId;         // e.g. bind_transceiver
    private int commandStatus;     // 0 = ESME_ROK
    private int sequenceNumber;    // request/response correlation

    protected Pdu(int commandId) {
        this.commandId = commandId;
    }

    public int getCommandLength() { return commandLength; }
    public void setCommandLength(int commandLength) { this.commandLength = commandLength; }

    public int getCommandId() { return commandId; }
    public void setCommandId(int commandId) { this.commandId = commandId; }

    public int getCommandStatus() { return commandStatus; }
    public void setCommandStatus(int commandStatus) { this.commandStatus = commandStatus; }

    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    private final List<OptionalParameter> optionalParameters = new ArrayList<>();

    public List<OptionalParameter> getOptionalParameters() {
        return optionalParameters;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "{commandLength=" + commandLength +
                ", commandId=0x" + Integer.toHexString(commandId) +
                ", commandStatus=0x" + Integer.toHexString(commandStatus) +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pdu)) return false;
        Pdu pdu = (Pdu) o;
        return commandLength == pdu.commandLength &&
                commandId == pdu.commandId &&
                commandStatus == pdu.commandStatus &&
                sequenceNumber == pdu.sequenceNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(commandLength, commandId, commandStatus, sequenceNumber);
    }
}
