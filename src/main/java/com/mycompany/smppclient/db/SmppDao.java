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
}
