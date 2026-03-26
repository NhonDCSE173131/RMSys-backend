-- V3: Connection health and ingest guard columns
ALTER TABLE machines
    ADD COLUMN connection_state VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    ADD COLUMN connection_unstable BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN last_seen_at TIMESTAMPTZ,
    ADD COLUMN last_telemetry_source_ts TIMESTAMPTZ,
    ADD COLUMN last_telemetry_received_at TIMESTAMPTZ,
    ADD COLUMN latest_accepted_source_ts TIMESTAMPTZ,
    ADD COLUMN last_payload_fingerprint VARCHAR(500),
    ADD COLUMN last_connection_changed_at TIMESTAMPTZ,
    ADD COLUMN connection_flap_count INT NOT NULL DEFAULT 0;
