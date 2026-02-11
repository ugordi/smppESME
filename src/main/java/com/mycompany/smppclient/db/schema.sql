CREATE SCHEMA IF NOT EXISTS smpp;

DO $$ BEGIN
CREATE TYPE smpp.direction AS ENUM ('IN', 'OUT');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS smpp.pdu_log (
                                            id              BIGSERIAL PRIMARY KEY,
                                            created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    direction       smpp.direction NOT NULL,   -- IN = gelen, OUT = giden
    pdu_type        TEXT NOT NULL,             -- submit_sm, deliver_sm, bind_transceiver, ...
    sequence_number INT  NOT NULL,
    command_id      INT  NOT NULL,
    command_status  INT  NOT NULL,

    -- RAW PDU HEX BURADA TUTULUYOR
    raw_hex         TEXT NOT NULL,

    --  Decode edilmiş alanların tamamı burada (JSON)
    decoded_json    JSONB
    );

CREATE INDEX IF NOT EXISTS ix_pdu_log_created_at ON smpp.pdu_log(created_at DESC);
CREATE INDEX IF NOT EXISTS ix_pdu_log_type ON smpp.pdu_log(pdu_type);
CREATE INDEX IF NOT EXISTS ix_pdu_log_seq ON smpp.pdu_log(sequence_number);
