package com.mycompany.smppclient.pdu.encoder;

import com.mycompany.smppclient.pdu.*;
import com.mycompany.smppclient.pdu.exception.InvalidPduException;
import com.mycompany.smppclient.pdu.tlv.OptionalParameter;

import java.util.List;

public class PduEncoder {

    public byte[] encode(Pdu pdu) {
        if (pdu == null) throw new InvalidPduException("PDU is null");

        ByteWriter body = new ByteWriter();

        if (pdu instanceof BindTransceiverReq req) {
            encodeBindTransceiverReq(req, body);

        } else if (pdu instanceof BindTransceiverResp resp) {
            encodeBindTransceiverResp(resp, body);

        } else if (pdu instanceof UnbindReq) {

        } else if (pdu instanceof UnbindResp) {

        } else if (pdu instanceof EnquireLinkReq) {

        } else if (pdu instanceof EnquireLinkResp) {
            //  body yok

        } else if (pdu instanceof SubmitSmReq req) {
            encodeSubmitSmReq(req, body);

        } else if (pdu instanceof SubmitSmResp resp) {
            encodeSubmitSmResp(resp, body);

        } else if (pdu instanceof DeliverSmReq req) {
            encodeDeliverSmReq(req, body);

        } else if (pdu instanceof DeliverSmResp resp) {
            encodeDeliverSmResp(resp, body);

        } else if (pdu instanceof GenericNack) {
            // body yok

        } else {
            throw new InvalidPduException("Encoder not implemented for: " + pdu.getClass().getSimpleName());
        }

        int totalLen = 16 + body.size();

        ByteWriter all = new ByteWriter();
        all.writeInt(totalLen);                 // command_length
        all.writeInt(pdu.getCommandId());       // command_id
        all.writeInt(pdu.getCommandStatus());   // command_status
        all.writeInt(pdu.getSequenceNumber());  // sequence_number
        all.writeBytes(body.toByteArray());

        return all.toByteArray();
    }

    // -------------------- BIND --------------------
    private void encodeBindTransceiverReq(BindTransceiverReq req, ByteWriter w) {
        requireNotNull(req.getSystemId(), "systemId");
        requireNotNull(req.getPassword(), "password");
        requireNotNull(req.getSystemType(), "systemType");
        requireNotNull(req.getAddressRange(), "addressRange");

        w.writeCString(req.getSystemId());
        w.writeCString(req.getPassword());
        w.writeCString(req.getSystemType());
        w.writeByte(req.getInterfaceVersion());
        w.writeByte(req.getAddrTon());
        w.writeByte(req.getAddrNpi());
        w.writeCString(req.getAddressRange());
    }

    private void encodeBindTransceiverResp(BindTransceiverResp resp, ByteWriter w) {
        // bind_resp body: system_id (CString)
        // (SMSC döndürür; biz fake server vs. için encode edebiliriz)
        requireNotNull(resp.getSystemId(), "systemId");
        w.writeCString(resp.getSystemId());
    }

    // -------------------- SUBMIT_SM --------------------
    private void encodeSubmitSmReq(SubmitSmReq req, ByteWriter w) {

        requireNotNull(req.getSourceAddr(), "sourceAddr");
        requireNotNull(req.getDestinationAddr(), "destinationAddr"  );

        w.writeCString(req.getServiceType());
        w.writeByte(req.getSourceAddrTon());
        w.writeByte(req.getSourceAddrNpi());
        w.writeCString(req.getSourceAddr());

        w.writeByte(req.getDestAddrTon());
        w.writeByte(req.getDestAddrNpi());
        w.writeCString(req.getDestinationAddr());

        w.writeByte(req.getEsmClass());
        w.writeByte(req.getProtocolId());
        w.writeByte(req.getPriorityFlag());


        w.writeCString(""); // schedule_delivery_time
        w.writeCString(""); // validity_period

        w.writeByte(req.getRegisteredDelivery()); //REGİTERED DELİVERY
        w.writeByte(0); // replace_if_present_flag
        w.writeByte(req.getDataCoding());
        w.writeByte(0); // sm_default_msg_id

        byte[] sm = (req.getShortMessage() == null) ? new byte[0] : req.getShortMessage();
        if (sm.length > 255) {
            throw new InvalidPduException("short_message too long for sm_length (max 255): " + sm.length);
        }
        w.writeByte(sm.length);     // sm_length
        w.writeOctets(sm);          // short_message


        encodeTlvs(req.getOptionalParameters(), w);
    }

    private void encodeSubmitSmResp(SubmitSmResp resp, ByteWriter w) {
        requireNotNull(resp.getMessageId(), "messageId");
        w.writeCString(resp.getMessageId());
        encodeTlvs(resp.getOptionalParameters(), w);
    }

    // -------------------- DELIVER_SM --------------------
    private void encodeDeliverSmReq(DeliverSmReq req, ByteWriter w) {

        w.writeCString(""); // service_type
        w.writeByte(0);     // source_addr_ton
        w.writeByte(0);     // source_addr_npi
        w.writeCString(""); // source_addr

        w.writeByte(0);     // dest_addr_ton
        w.writeByte(0);     // dest_addr_npi
        w.writeCString(""); // destination_addr

        w.writeByte(0);     // esm_class
        w.writeByte(0);     // protocol_id
        w.writeByte(0);     // priority_flag

        w.writeCString(""); // schedule_delivery_time
        w.writeCString(""); // validity_period

        w.writeByte(0);     // registered_delivery
        w.writeByte(0);     // replace_if_present_flag
        w.writeByte(req.getDataCoding());
        w.writeByte(0);     // sm_default_msg_id

        byte[] sm = (req.getShortMessage() == null) ? new byte[0] : req.getShortMessage();
        if (sm.length > 255) {
            throw new InvalidPduException("short_message too long for sm_length (max 255): " + sm.length);
        }
        w.writeByte(sm.length);
        w.writeOctets(sm);

        encodeTlvs(req.getOptionalParameters(), w);
    }

    private void encodeDeliverSmResp(DeliverSmResp resp, ByteWriter w) {
        requireNotNull(resp.getMessageId(), "messageId");
        w.writeCString(resp.getMessageId());
        encodeTlvs(resp.getOptionalParameters(), w);
    }

    // -------------------- TLV --------------------
    private void encodeTlvs(List<OptionalParameter> tlvs, ByteWriter w) {
        if (tlvs == null || tlvs.isEmpty()) return;

        for (OptionalParameter p : tlvs) {
            if (p == null) continue;

            int tag = p.getTag();
            byte[] val = p.getValue();
            if (val == null) val = new byte[0];
            int len = val.length;

            w.writeShort(tag);
            w.writeShort(len);
            w.writeOctets(val);
        }
    }

    private void requireNotNull(Object v, String name) {
        if (v == null) throw new InvalidPduException(name + " is null");
    }
}