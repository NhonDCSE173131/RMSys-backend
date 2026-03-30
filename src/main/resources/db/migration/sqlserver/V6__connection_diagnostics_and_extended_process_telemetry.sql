-- V6: Connection diagnostics and extended process telemetry
IF COL_LENGTH('machines', 'connection_scope') IS NULL
BEGIN
	ALTER TABLE machines ADD connection_scope VARCHAR(30) NULL;
END;
GO

IF COL_LENGTH('machines', 'connection_reason') IS NULL
BEGIN
	ALTER TABLE machines ADD connection_reason VARCHAR(80) NULL;
END;
GO

IF COL_LENGTH('machine_telemetry', 'ideal_cycle_time_sec') IS NULL
BEGIN
	ALTER TABLE machine_telemetry ADD ideal_cycle_time_sec FLOAT NULL;
END;
GO

IF COL_LENGTH('machine_telemetry', 'spindle_load_pct') IS NULL
BEGIN
	ALTER TABLE machine_telemetry ADD spindle_load_pct FLOAT NULL;
END;
GO

IF COL_LENGTH('machine_telemetry', 'servo_load_pct') IS NULL
BEGIN
	ALTER TABLE machine_telemetry ADD servo_load_pct FLOAT NULL;
END;
GO

IF COL_LENGTH('machine_telemetry', 'cutting_speed_m_min') IS NULL
BEGIN
	ALTER TABLE machine_telemetry ADD cutting_speed_m_min FLOAT NULL;
END;
GO

IF COL_LENGTH('machine_telemetry', 'depth_of_cut_mm') IS NULL
BEGIN
	ALTER TABLE machine_telemetry ADD depth_of_cut_mm FLOAT NULL;
END;
GO

IF COL_LENGTH('machine_telemetry', 'feed_per_tooth_mm') IS NULL
BEGIN
	ALTER TABLE machine_telemetry ADD feed_per_tooth_mm FLOAT NULL;
END;
GO

IF COL_LENGTH('machine_telemetry', 'width_of_cut_mm') IS NULL
BEGIN
	ALTER TABLE machine_telemetry ADD width_of_cut_mm FLOAT NULL;
END;
GO

IF COL_LENGTH('machine_telemetry', 'material_removal_rate_cm3_min') IS NULL
BEGIN
	ALTER TABLE machine_telemetry ADD material_removal_rate_cm3_min FLOAT NULL;
END;
GO

IF COL_LENGTH('machine_telemetry', 'welding_current_a') IS NULL
BEGIN
	ALTER TABLE machine_telemetry ADD welding_current_a FLOAT NULL;
END;
GO

