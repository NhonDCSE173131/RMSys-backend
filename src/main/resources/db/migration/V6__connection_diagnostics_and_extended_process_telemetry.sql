-- V6: Connection diagnostics and extended process telemetry
ALTER TABLE machines
    ADD COLUMN IF NOT EXISTS connection_scope VARCHAR(30),
    ADD COLUMN IF NOT EXISTS connection_reason VARCHAR(80);

ALTER TABLE machine_telemetry
    ADD COLUMN IF NOT EXISTS ideal_cycle_time_sec DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS spindle_load_pct DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS servo_load_pct DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS cutting_speed_m_min DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS depth_of_cut_mm DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS feed_per_tooth_mm DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS width_of_cut_mm DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS material_removal_rate_cm3_min DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS welding_current_a DOUBLE PRECISION;

