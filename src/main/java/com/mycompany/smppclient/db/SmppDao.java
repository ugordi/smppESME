package com.mycompany.smppclient.db;

import java.sql.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Tek DAO: SUBMIT_SM / DELIVER_SM için detaylı kayıt.
 *
 * NOT: EnquireLink gibi spam akışları buraya çağırma.
 *
 * Şema:
 * - smpp_pdu_log
 * - smpp_message
 */
public final class SmppDao {

    private final Db db;

    public SmppDao(Db db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    // ------------------------------------------------------------
    // PDU LOG (smpp_pdu_log) - tek satır log
    // ------------------------------------------------------------
    public String insertPduLog(
            String sessionId,
            String direction,   // "TX" / "RX"
            String pduType,     // "SUBMIT_SM" / "SUBMIT_SM_RESP" / "DELIVER_SM" / "DELIVER_SM_RESP"
            int pduLen,
            int commandId,
            int commandStatus,
            int seq,
            String pduHex,

            String sourceAddr,
            String destinationAddr,
            Integer dataCoding,
            Integer esmClass,

            Integer shortMessageLen,
            String shortMessageHex,

            Boolean isDlr,
            String dlrMessageId,
            String dlrStat,
            String dlrErr,
            String dlrSubmitDate,
            String dlrDoneDate,

            String decodedText,
            String decodePath,
            String notes
    ) {
        final String sql = """
            insert into smpp_pdu_log(
              created_at, session_id,
              direction, pdu_type,
              pdu_len, command_id, command_status, seq,
              pdu_hex,
              source_addr, destination_addr, data_coding, esm_class,
              short_message_len, short_message_hex,
              is_dlr, dlr_message_id, dlr_stat, dlr_err, dlr_submit_date, dlr_done_date,
              decoded_text, decode_path, notes
            ) values (
              ?, ?,
              ?, ?,
              ?, ?, ?, ?,
              ?,
              ?, ?, ?, ?,
              ?, ?,
              ?, ?, ?, ?, ?, ?,
              ?, ?, ?
            )
            returning id::text
        """;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            int i = 1;
            ps.setTimestamp(i++, Timestamp.from(Instant.now()));
            ps.setString(i++, sessionId);

            ps.setString(i++, direction);
            ps.setString(i++, pduType);

            ps.setInt(i++, pduLen);
            ps.setInt(i++, commandId);
            ps.setInt(i++, commandStatus);
            ps.setInt(i++, seq);

            ps.setString(i++, pduHex);

            ps.setString(i++, sourceAddr);
            ps.setString(i++, destinationAddr);

            if (dataCoding == null) ps.setNull(i++, Types.INTEGER); else ps.setInt(i++, dataCoding);
            if (esmClass == null) ps.setNull(i++, Types.INTEGER); else ps.setInt(i++, esmClass);

            if (shortMessageLen == null) ps.setNull(i++, Types.INTEGER); else ps.setInt(i++, shortMessageLen);
            ps.setString(i++, shortMessageHex);

            if (isDlr == null) ps.setNull(i++, Types.BOOLEAN); else ps.setBoolean(i++, isDlr);
            ps.setString(i++, dlrMessageId);
            ps.setString(i++, dlrStat);
            ps.setString(i++, dlrErr);
            ps.setString(i++, dlrSubmitDate);
            ps.setString(i++, dlrDoneDate);

            ps.setString(i++, decodedText);
            ps.setString(i++, decodePath);
            ps.setString(i++, notes);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
            return null;

        } catch (SQLException e) {
            // DB yüzünden SMPP akışı ölmesin istiyorsan burada swallow + log yapabilirsin.
            throw new RuntimeException("insertPduLog failed: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------
    // MESSAGE (smpp_message) - submit anında (resp gelmeden önce)
    // ------------------------------------------------------------
    public String insertMessageOnSubmit(
            String sessionId,
            String systemId,
            int submitSeq,
            String submitPduLogId,

            String sourceAddr,
            String destinationAddr,
            int dataCoding,
            int esmClass,

            String originalText,
            String originalTextHex,
            String gsm7BytesHex,
            String gsm7PackedHex
    ) {
        final String sql = """
            insert into smpp_message(
              created_at, session_id,
              system_id,
              submit_seq, submit_pdu_log_id,
              source_addr, destination_addr, data_coding, esm_class,
              original_text, original_text_hex, gsm7_bytes_hex, gsm7_packed_hex,
              dlr_received
            ) values (
              ?, ?,
              ?,
              ?, cast(? as uuid),
              ?, ?, ?, ?,
              ?, ?, ?, ?,
              false
            )
            returning id::text
        """;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            int i = 1;
            ps.setTimestamp(i++, Timestamp.from(Instant.now()));
            ps.setString(i++, sessionId);

            ps.setString(i++, systemId);

            ps.setInt(i++, submitSeq);
            ps.setString(i++, submitPduLogId);

            ps.setString(i++, sourceAddr);
            ps.setString(i++, destinationAddr);
            ps.setInt(i++, dataCoding);
            ps.setInt(i++, esmClass);

            ps.setString(i++, originalText);
            ps.setString(i++, originalTextHex);
            ps.setString(i++, gsm7BytesHex);
            ps.setString(i++, gsm7PackedHex);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
            return null;

        } catch (SQLException e) {
            throw new RuntimeException("insertMessageOnSubmit failed: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------
    // MESSAGE update: submit_sm_resp geldi (message_id + status)
    // ------------------------------------------------------------
    public void updateMessageOnSubmitResp(
            String sessionId,
            int submitSeq,
            String submitRespPduLogId,
            int submitStatus,
            String submitMessageId
    ) {
        final String sql = """
            update smpp_message
               set submit_resp_pdu_log_id = cast(? as uuid),
                   submit_status = ?,
                   submit_message_id = ?
             where session_id = ?
               and submit_seq = ?
        """;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            int i = 1;
            ps.setString(i++, submitRespPduLogId);
            ps.setInt(i++, submitStatus);
            ps.setString(i++, submitMessageId);
            ps.setString(i++, sessionId);
            ps.setInt(i++, submitSeq);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("updateMessageOnSubmitResp failed: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------
    // MESSAGE update: DLR geldi (submit_message_id üzerinden)
    // ------------------------------------------------------------
    public void applyDlrByMessageId(
            String sessionId,
            String submitMessageId,
            String dlrPduLogId,

            String dlrStat,
            String dlrErr,
            String dlrSubmitDate,
            String dlrDoneDate,
            String rawReceiptTextNullable
    ) {
        final String sql = """
            update smpp_message
               set dlr_received = true,
                   dlr_pdu_log_id = cast(? as uuid),
                   dlr_stat = ?,
                   dlr_err = ?,
                   dlr_submit_date = ?,
                   dlr_done_date = ?,
                   dlr_raw_text = ?
             where session_id = ?
               and submit_message_id = ?
        """;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            int i = 1;
            ps.setString(i++, dlrPduLogId);

            ps.setString(i++, dlrStat);
            ps.setString(i++, dlrErr);
            ps.setString(i++, dlrSubmitDate);
            ps.setString(i++, dlrDoneDate);
            ps.setString(i++, rawReceiptTextNullable);

            ps.setString(i++, sessionId);
            ps.setString(i++, submitMessageId);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("applyDlrByMessageId failed: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------
    public static String toHex(byte[] b) {
        if (b == null) return null;
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }
}
