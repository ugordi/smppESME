package com.mycompany.smppclient.pdu.decoder;

import com.mycompany.smppclient.pdu.*;
import com.mycompany.smppclient.pdu.exception.DecodeException;
import com.mycompany.smppclient.pdu.tlv.OptionalParameter;

import java.util.ArrayList;
import java.util.List;

public class PduDecoder {

    public Pdu decode(byte[] data) {
        if (data == null || data.length < 16) {
            throw new DecodeException("PDU too short (min 16 bytes header). len=" + (data == null ? 0 : data.length));
        }

        ByteReader r = new ByteReader(data);

        int commandLength = r.readInt();
        int commandId     = r.readInt();
        int commandStatus = r.readInt();
        int sequence      = r.readInt();

        if (commandLength != data.length) {
            throw new DecodeException("command_length mismatch. header=" + commandLength + " actual=" + data.length);
        }

        Pdu pdu = switch (commandId) {
            case CommandId.BIND_TRANSCEIVER      -> decodeBindTransceiverReq(r);
            case CommandId.BIND_TRANSCEIVER_RESP -> decodeBindTransceiverResp(r);

            case CommandId.UNBIND      -> new UnbindReq();
            case CommandId.UNBIND_RESP -> new UnbindResp();

            case CommandId.ENQUIRE_LINK      -> new EnquireLinkReq();
            case CommandId.ENQUIRE_LINK_RESP -> new EnquireLinkResp();

            case CommandId.SUBMIT_SM      -> decodeSubmitSmReq(r);
            case CommandId.SUBMIT_SM_RESP -> decodeSubmitSmResp(r);

            case CommandId.DELIVER_SM      -> decodeDeliverSmReq(r);
            case CommandId.DELIVER_SM_RESP -> decodeDeliverSmResp(r);

            case CommandId.GENERIC_NACK -> new GenericNack();

            default -> throw new DecodeException("Unsupported command_id: 0x" + Integer.toHexString(commandId));
        };


        pdu.setCommandLength(commandLength);
        pdu.setCommandStatus(commandStatus);
        pdu.setSequenceNumber(sequence);



        return pdu;
    }

    // -------------------- BIND --------------------
    private BindTransceiverReq decodeBindTransceiverReq(ByteReader r) {
        BindTransceiverReq req = new BindTransceiverReq();
        req.setSystemId(r.readCString());
        req.setPassword(r.readCString());
        req.setSystemType(r.readCString());
        req.setInterfaceVersion((byte) r.readByte());
        req.setAddrTon((byte) r.readByte());
        req.setAddrNpi((byte) r.readByte());
        req.setAddressRange(r.readCString());
        return req;
    }

    private BindTransceiverResp decodeBindTransceiverResp(ByteReader r) {
        BindTransceiverResp resp = new BindTransceiverResp();
        // body: system_id (CString)
        resp.setSystemId(r.readCString());
        return resp;
    }

    // -------------------- SUBMIT_SM --------------------
    private SubmitSmReq decodeSubmitSmReq(ByteReader r) {
        SubmitSmReq req = new SubmitSmReq();

        req.setServiceType(r.readCString());
        req.setSourceAddrTon((byte) r.readByte());
        req.setSourceAddrNpi((byte) r.readByte());
        req.setSourceAddr(r.readCString());

        req.setDestAddrTon((byte) r.readByte());
        req.setDestAddrNpi((byte) r.readByte());
        req.setDestinationAddr(r.readCString());

        req.setEsmClass((byte) r.readByte());
        req.setProtocolId((byte) r.readByte());
        req.setPriorityFlag((byte) r.readByte());

        // schedule_delivery_time, validity_period
        r.readCString();
        r.readCString();

        req.setRegisteredDelivery((byte) r.readByte());
        r.readByte(); // replace_if_present_flag (şu an POJO’da yoksa atla)
        req.setDataCoding((byte) r.readByte());
        r.readByte(); // sm_default_msg_id (şu an POJO’da yoksa atla)

        int smLen = r.readByte() & 0xFF;

        req.setShortMessage(r.readBytes(smLen));

        // TLV (kalan her şeyi TLV kabul ediyoruz)
        req.getOptionalParameters().addAll(readTlvs(r));

        return req;
    }

    private SubmitSmResp decodeSubmitSmResp(ByteReader r) {
        SubmitSmResp resp = new SubmitSmResp();
        resp.setMessageId(r.readCString());

        resp.getOptionalParameters().addAll(readTlvs(r));
        return resp;
    }

    // -------------------- DELIVER_SM --------------------
    private DeliverSmReq decodeDeliverSmReq(ByteReader r) {
        DeliverSmReq req = new DeliverSmReq();


        req.setServiceType(r.readCString());
        req.setSourceAddrTon((byte) r.readByte());
        req.setSourceAddrNpi((byte) r.readByte());
        req.setSourceAddr(r.readCString());

        req.setDestAddrTon((byte) r.readByte());
        req.setDestAddrNpi((byte) r.readByte());
        req.setDestinationAddr(r.readCString());

        req.setEsmClass((byte) r.readByte());
        req.setProtocolId((byte) r.readByte());
        req.setPriorityFlag((byte) r.readByte());

        req.setScheduleDeliveryTime(r.readCString());
        req.setValidityPeriod(r.readCString());

        req.setRegisteredDelivery((byte) r.readByte());
        r.readByte(); // replace_if_present_flag
        req.setDataCoding((byte) r.readByte());
        r.readByte(); // sm_default_msg_id

        int smLen = r.readByte() & 0xFF;
        req.setShortMessage(r.readBytes(smLen));

        req.getOptionalParameters().addAll(readTlvs(r));
        return req;
    }

    private DeliverSmResp decodeDeliverSmResp(ByteReader r) {
        DeliverSmResp resp = new DeliverSmResp();
        if (r.remaining() > 0) {
            resp.setMessageId(r.readCString());
        } else {
            resp.setMessageId("");
        }
        resp.getOptionalParameters().addAll(readTlvs(r));
        return resp;
    }

    // -------------------- TLV --------------------
    private List<OptionalParameter> readTlvs(ByteReader r) {
        List<OptionalParameter> list = new ArrayList<>();
        while (r.remaining() >= 4) { // en az tag(2)+len(2)
            int tag = r.readShort();
            int len = r.readShort();
            if (len > r.remaining()) {
                throw new DecodeException("TLV length exceeds remaining. tag=0x" +
                        Integer.toHexString(tag) + " len=" + len + " remaining=" + r.remaining());
            }
            byte[] val = r.readBytes(len);
            list.add(new OptionalParameter(tag, len, val));
        }
        return list;
    }
}
