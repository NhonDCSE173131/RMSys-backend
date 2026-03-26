-- V4: Quality metrics and audit columns for telemetry
ALTER TABLE machine_telemetry
    ADD COLUMN quality_score DOUBLE PRECISION,
    ADD COLUMN is_late_arrival BOOLEAN DEFAULT FALSE,
    ADD COLUMN source_sequence BIGINT;

CREATE INDEX idx_telemetry_quality ON machine_telemetry(machine_id, is_late_arrival);

