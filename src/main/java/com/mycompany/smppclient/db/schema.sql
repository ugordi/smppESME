create extension if not exists pgcrypto;

create table if not exists smpp_pdu_log (
                                            id uuid primary key default gen_random_uuid(),
    created_at timestamptz not null default now(),

    session_id text not null,

    direction text not null check (direction in ('TX','RX')),
    pdu_type text not null check (pdu_type in ('SUBMIT_SM','SUBMIT_SM_RESP','DELIVER_SM','DELIVER_SM_RESP')),

    -- header
    pdu_len int not null,
    command_id int not null,
    command_status int not null,
    seq int not null,

    -- raw PDU (tam paket)
    pdu_hex text not null,

    -- submit/deliver alanları (varsa)
    source_addr text,
    destination_addr text,
    data_coding int,
    esm_class int,

    short_message_len int,
    short_message_hex text,

    -- deliver'da receipt gibi mi
    is_dlr boolean,
    dlr_message_id text,
    dlr_stat text,
    dlr_err text,
    dlr_submit_date text,
    dlr_done_date text,

    decoded_text text,
    decode_path text,          -- "GSM7_PACKED", "UCS2", "LATIN1", "DC00_AS_TEXT_FIRST" vb.
    notes text
    );

create index if not exists ix_smpp_pdu_log_session_time on smpp_pdu_log(session_id, created_at desc);
create index if not exists ix_smpp_pdu_log_type_time on smpp_pdu_log(pdu_type, created_at desc);
create index if not exists ix_smpp_pdu_log_seq on smpp_pdu_log(seq);


create table if not exists smpp_message (
                                            id uuid primary key default gen_random_uuid(),
    created_at timestamptz not null default now(),
    session_id text not null,

    system_id text,

    -- submit
    submit_seq int not null,
    submit_pdu_log_id uuid references smpp_pdu_log(id),
    submit_resp_pdu_log_id uuid references smpp_pdu_log(id),

    source_addr text,
    destination_addr text,
    data_coding int,
    esm_class int,

    -- encoding trace (send tarafı)
    original_text text,            -- kullanıcının yazdığı
    original_text_hex text,        -- UTF-8 / ASCII bytes hex (ne kullanıyorsan)
    gsm7_bytes_hex text,           -- senin codec'in ürettiği bytes (single shift bytes)
    gsm7_packed_hex text,          -- PDU'ya koyduğun packed septet hex (short_message_hex ile aynı olabilir ama özellikle tutuyoruz)

-- submit_resp
    submit_status int,
    submit_message_id text,        -- SMSC message_id

-- dlr
    dlr_received boolean not null default false,
    dlr_pdu_log_id uuid references smpp_pdu_log(id),
    dlr_stat text,
    dlr_err text,
    dlr_submit_date text,
    dlr_done_date text,
    dlr_raw_text text
    );

create unique index if not exists ux_smpp_message_submit_seq_session on smpp_message(session_id, submit_seq);
create index if not exists ix_smpp_message_submit_msgid on smpp_message(submit_message_id);


