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


ALTER TABLE smpp.pdu_log
    ADD COLUMN IF NOT EXISTS message_id TEXT,
    ADD COLUMN IF NOT EXISTS related_message_id TEXT,
    ADD COLUMN IF NOT EXISTS is_dlr BOOLEAN NOT NULL DEFAULT false;



CREATE TABLE IF NOT EXISTS smpp.message_flow (
                                                 id BIGSERIAL PRIMARY KEY,
                                                 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    session_id TEXT,
    system_id  TEXT,

    -- submit tarafı
    submit_seq INT,
    src_addr   TEXT,
    dst_addr   TEXT,
    data_coding INT,
    esm_class  INT,
    submit_sm_hex TEXT,

    -- submit_sm_resp tarafı
    submit_resp_seq INT,
    submit_resp_status INT,
    message_id TEXT UNIQUE,      -- SMSC message_id (en kritik key)

-- dlr tarafı (deliver_sm receipt)
    dlr_received BOOLEAN NOT NULL DEFAULT false,
    dlr_time TIMESTAMPTZ,
    dlr_stat TEXT,
    dlr_err  TEXT,
    dlr_text TEXT,

    -- linkler (pdu_log satırlarına referans)
    submit_log_id BIGINT REFERENCES smpp.pdu_log(id),
    submit_resp_log_id BIGINT REFERENCES smpp.pdu_log(id),
    dlr_log_id BIGINT REFERENCES smpp.pdu_log(id)
    );

CREATE INDEX IF NOT EXISTS ix_message_flow_created_at ON smpp.message_flow(created_at DESC);
CREATE INDEX IF NOT EXISTS ix_message_flow_submit_seq ON smpp.message_flow(submit_seq);
CREATE INDEX IF NOT EXISTS ix_message_flow_message_id ON smpp.message_flow(message_id);
CREATE INDEX IF NOT EXISTS ix_message_flow_dlr_received ON smpp.message_flow(dlr_received);

CREATE UNIQUE INDEX IF NOT EXISTS ux_message_flow_sess_submitseq
    ON smpp.message_flow(session_id, submit_seq);

CREATE TABLE IF NOT EXISTS smpp.smsc_account (
                                                 id            BIGSERIAL PRIMARY KEY,
                                                 name          TEXT NOT NULL UNIQUE,     -- ör: "primary", "backup"
                                                 host          TEXT NOT NULL,
                                                 port          INT  NOT NULL,
                                                 system_id     TEXT NOT NULL,
                                                 password      TEXT NOT NULL,
                                                 system_type   TEXT NOT NULL DEFAULT '',
                                                 interface_ver INT  NOT NULL DEFAULT 0x34,  -- SMPP 3.4
                                                 addr_ton      INT  NOT NULL DEFAULT 5,
                                                 addr_npi      INT  NOT NULL DEFAULT 0,
                                                 address_range TEXT NOT NULL DEFAULT '',
                                                 is_active     BOOLEAN NOT NULL DEFAULT true,
                                                 created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
    );

