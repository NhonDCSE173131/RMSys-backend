-- V4: Quality metrics and audit columns for SQL Server
ALTER TABLE machine_telemetry ADD quality_score FLOAT;
ALTER TABLE machine_telemetry ADD is_late_arrival BIT DEFAULT 0;
ALTER TABLE machine_telemetry ADD source_sequence BIGINT;
CREATE INDEX idx_telemetry_quality ON machine_telemetry(machine_id, is_late_arrival);
