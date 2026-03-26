-- V3: Connection health and ingest guard columns for SQL Server
ALTER TABLE machines ADD connection_state VARCHAR(20) NOT NULL CONSTRAINT df_machines_connection_state DEFAULT 'OFFLINE';
ALTER TABLE machines ADD connection_unstable BIT NOT NULL CONSTRAINT df_machines_connection_unstable DEFAULT 0;
ALTER TABLE machines ADD last_seen_at DATETIME2(6) NULL;
ALTER TABLE machines ADD last_telemetry_source_ts DATETIME2(6) NULL;
ALTER TABLE machines ADD last_telemetry_received_at DATETIME2(6) NULL;
ALTER TABLE machines ADD latest_accepted_source_ts DATETIME2(6) NULL;
ALTER TABLE machines ADD last_payload_fingerprint VARCHAR(500) NULL;
ALTER TABLE machines ADD last_connection_changed_at DATETIME2(6) NULL;
ALTER TABLE machines ADD connection_flap_count INT NOT NULL CONSTRAINT df_machines_connection_flap_count DEFAULT 0;
