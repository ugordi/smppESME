package com.mycompany.smppclient.db;

import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.Map;

public final class SmppDao {

    private final Db db;

    public SmppDao(Db db) {
        this.db = db;
    }

    public enum Direction { IN, OUT }


    public long insertPduLog(
            Direction direction,
            String pduType,
            int commandId,
            int commandStatus,
            int sequenceNumber,
            String rawHex,
            Map<String, Object> decodedFields
    ) throws SQLException {

        String sql = """
            INSERT INTO smpp.pdu_log
              (direction, pdu_type, command_id, command_status, sequence_number, raw_hex, decoded_json)
            VALUES
              (?::smpp.direction, ?, ?, ?, ?, ?, ?)
            RETURNING id
        """;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, direction.name());
            ps.setString(2, pduType);
            ps.setInt(3, commandId);
            ps.setInt(4, commandStatus);
            ps.setInt(5, sequenceNumber);

            // ✅ RAW HEX DB’ye burada gidiyor
            ps.setString(6, rawHex);

            // decoded_json (JSONB)
            ps.setObject(7, toJsonb(decodedFields));

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static PGobject toJsonb(Map<String, Object> map) {
        if (map == null) return null;
        try {
            String json = SimpleJson.toJson(map);
            PGobject obj = new PGobject();
            obj.setType("jsonb");
            obj.setValue(json);
            return obj;
        } catch (Exception e) {
            throw new RuntimeException("json serialize failed", e);
        }
    }

    // Jackson yoksa diye minimal JSON. Projede Jackson varsa bunu kaldır, ObjectMapper kullan.
    static final class SimpleJson {
        static String toJson(Map<String, Object> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var e : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(esc(e.getKey())).append("\":");
                sb.append(val(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        static String val(Object v) {
            if (v == null) return "null";
            if (v instanceof Number || v instanceof Boolean) return v.toString();
            return "\"" + esc(String.valueOf(v)) + "\"";
        }
        static String esc(String s) {
            return s.replace("\\","\\\\").replace("\"","\\\"");
        }
    }

    public long insertMessageFlowOnSubmit(
            String sessionId,
            String systemId,
            int submitSeq,
            String srcAddr,
            String dstAddr,
            int dataCoding,
            int esmClass,
            String submitSmHex,
            long submitLogId
    ) throws SQLException {
        String sql = """
        INSERT INTO smpp.message_flow
          (session_id, system_id, submit_seq, src_addr, dst_addr, data_coding, esm_class, submit_sm_hex, submit_log_id)
        VALUES
          (?, ?, ?, ?, ?, ?, ?, ?, ?)
        RETURNING id
    """;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, sessionId);
            ps.setString(2, systemId);
            ps.setInt(3, submitSeq);
            ps.setString(4, srcAddr);
            ps.setString(5, dstAddr);
            ps.setInt(6, dataCoding);
            ps.setInt(7, esmClass);
            ps.setString(8, submitSmHex);
            ps.setLong(9, submitLogId);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public int updateMessageFlowOnSubmitResp(
            String sessionId,
            int submitSeq,
            int respSeq,
            int respStatus,
            String messageId,
            long submitRespLogId
    ) throws SQLException {
        String sql = """
        UPDATE smpp.message_flow
           SET submit_resp_seq = ?,
               submit_resp_status = ?,
               message_id = ?,
               submit_resp_log_id = ?,
               updated_at = now()
         WHERE session_id = ?
           AND submit_seq = ?
    """;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, respSeq);
            ps.setInt(2, respStatus);
            ps.setString(3, messageId);
            ps.setLong(4, submitRespLogId);
            ps.setString(5, sessionId);
            ps.setInt(6, submitSeq);


            return ps.executeUpdate();
        }
    }

    public int updateMessageFlowOnDlr(
            String messageId,
            String dlrStat,
            String dlrErr,
            String dlrText,
            long dlrLogId
    ) throws SQLException {
        String sql = """
        UPDATE smpp.message_flow
           SET dlr_received = true,
               dlr_time = now(),
               dlr_stat = ?,
               dlr_err = ?,
               dlr_text = ?,
               dlr_log_id = ?,
               updated_at = now()
         WHERE message_id = ?
    """;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, dlrStat);
            ps.setString(2, dlrErr);
            ps.setString(3, dlrText);
            ps.setLong(4, dlrLogId);
            ps.setString(5, messageId);

            return ps.executeUpdate();
        }
    }




}
