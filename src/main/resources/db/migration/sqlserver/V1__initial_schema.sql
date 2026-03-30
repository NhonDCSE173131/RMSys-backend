-- V1: Consolidated SQL Server schema for Manufacturing Monitor
-- (All columns from original V1 + V3 + V4 + V6 merged. No seed data.)

-- ============================================================
-- Master tables
-- ============================================================
CREATE TABLE machines (
    id                          UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),
    code                        VARCHAR(50)      NOT NULL UNIQUE,
    name                        NVARCHAR(200)    NOT NULL,
    type                        VARCHAR(50)      NOT NULL DEFAULT 'CNC_MACHINE',
    vendor                      VARCHAR(50)      NOT NULL DEFAULT 'UNKNOWN',
    model                       NVARCHAR(200),
    line_id                     VARCHAR(50),
    plant_id                    VARCHAR(50),
    status                      VARCHAR(30)      NOT NULL DEFAULT 'OFFLINE',
    is_enabled                  BIT              NOT NULL DEFAULT 1,
    -- Connection health (was V3)
    connection_state            VARCHAR(20)      NOT NULL DEFAULT 'OFFLINE',
    connection_unstable         BIT              NOT NULL DEFAULT 0,
    last_seen_at                DATETIME2(6),
    last_telemetry_source_ts    DATETIME2(6),
    last_telemetry_received_at  DATETIME2(6),
    latest_accepted_source_ts   DATETIME2(6),
    last_payload_fingerprint    VARCHAR(500),
    last_connection_changed_at  DATETIME2(6),
    connection_flap_count       INT              NOT NULL DEFAULT 0,
    -- Connection diagnostics (was V6)
    connection_scope            VARCHAR(30)      NULL,
    connection_reason           VARCHAR(80)      NULL,
    created_at                  DATETIME2(6)     NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at                  DATETIME2(6)     NOT NULL DEFAULT SYSUTCDATETIME()
);

CREATE TABLE tool_catalogs (
    id                      UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),
    machine_id              UNIQUEIDENTIFIER NOT NULL,
    tool_code               VARCHAR(50)      NOT NULL,
    tool_name               NVARCHAR(200)    NOT NULL,
    tool_type               VARCHAR(50),
    life_limit_minutes      INT,
    life_limit_cycles       INT,
    warning_threshold_pct   FLOAT            DEFAULT 20,
    critical_threshold_pct  FLOAT            DEFAULT 10,
    created_at              DATETIME2(6)     NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIME2(6)     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_tool_catalogs_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
);

CREATE TABLE machine_thresholds (
    id              UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),
    machine_id      UNIQUEIDENTIFIER NOT NULL,
    metric_code     VARCHAR(50)      NOT NULL,
    warning_value   FLOAT,
    critical_value  FLOAT,
    unit            NVARCHAR(20),
    created_at      DATETIME2(6)     NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2(6)     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_machine_thresholds_machine FOREIGN KEY (machine_id) REFERENCES machines(id),
    CONSTRAINT uq_machine_threshold UNIQUE (machine_id, metric_code)
);

-- ============================================================
-- Time-series tables
-- ============================================================
CREATE TABLE machine_telemetry (
    id                              BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    machine_id                      UNIQUEIDENTIFIER     NOT NULL,
    ts                              DATETIME2(6)         NOT NULL DEFAULT SYSUTCDATETIME(),
    connection_status               VARCHAR(20),
    machine_state                   VARCHAR(30),
    operation_mode                  VARCHAR(30),
    alarm_active                    BIT                  DEFAULT 0,
    program_name                    NVARCHAR(200),
    cycle_running                   BIT                  DEFAULT 0,
    current_job                     NVARCHAR(200),
    power_kw                        FLOAT,
    temperature_c                   FLOAT,
    vibration_mm_s                  FLOAT,
    runtime_hours                   FLOAT,
    cycle_time_sec                  FLOAT,
    output_count                    INT                  DEFAULT 0,
    good_count                      INT                  DEFAULT 0,
    reject_count                    INT                  DEFAULT 0,
    spindle_speed_rpm               FLOAT,
    feed_rate_mm_min                FLOAT,
    axis_load_pct                   FLOAT,
    -- Quality metrics (was V4)
    quality_score                   FLOAT,
    is_late_arrival                 BIT                  DEFAULT 0,
    source_sequence                 BIGINT,
    -- Extended process telemetry (was V6)
    ideal_cycle_time_sec            FLOAT,
    spindle_load_pct                FLOAT,
    servo_load_pct                  FLOAT,
    cutting_speed_m_min             FLOAT,
    depth_of_cut_mm                 FLOAT,
    feed_per_tooth_mm               FLOAT,
    width_of_cut_mm                 FLOAT,
    material_removal_rate_cm3_min   FLOAT,
    welding_current_a               FLOAT,
    metadata_json                   NVARCHAR(MAX),
    CONSTRAINT fk_machine_telemetry_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
);
CREATE INDEX idx_telemetry_machine_ts ON machine_telemetry(machine_id, ts DESC);
CREATE INDEX idx_telemetry_quality    ON machine_telemetry(machine_id, is_late_arrival);

CREATE TABLE energy_telemetry (
    id                  BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    machine_id          UNIQUEIDENTIFIER     NOT NULL,
    ts                  DATETIME2(6)         NOT NULL DEFAULT SYSUTCDATETIME(),
    voltage_v           FLOAT,
    current_a           FLOAT,
    power_kw            FLOAT,
    power_factor        FLOAT,
    frequency_hz        FLOAT,
    energy_kwh_shift    FLOAT,
    energy_kwh_day      FLOAT,
    energy_kwh_month    FLOAT,
    CONSTRAINT fk_energy_telemetry_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
);
CREATE INDEX idx_energy_machine_ts ON energy_telemetry(machine_id, ts DESC);

CREATE TABLE tool_usage_telemetry (
    id                      BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    machine_id              UNIQUEIDENTIFIER     NOT NULL,
    ts                      DATETIME2(6)         NOT NULL DEFAULT SYSUTCDATETIME(),
    tool_code               VARCHAR(50),
    tool_number             INT,
    usage_minutes           FLOAT,
    usage_cycles            INT,
    spindle_load_pct        FLOAT,
    tool_temperature_c      FLOAT,
    remaining_life_pct      FLOAT,
    wear_level              VARCHAR(20),
    CONSTRAINT fk_tool_usage_telemetry_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
);
CREATE INDEX idx_tool_usage_machine_ts ON tool_usage_telemetry(machine_id, ts DESC);

CREATE TABLE maintenance_telemetry (
    id                      BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    machine_id              UNIQUEIDENTIFIER     NOT NULL,
    ts                      DATETIME2(6)         NOT NULL DEFAULT SYSUTCDATETIME(),
    motor_temperature_c     FLOAT,
    bearing_temperature_c   FLOAT,
    cabinet_temperature_c   FLOAT,
    vibration_mm_s          FLOAT,
    runtime_hours           FLOAT,
    servo_on_hours          FLOAT,
    start_stop_count        INT,
    lubrication_level_pct   FLOAT,
    battery_low             BIT                  DEFAULT 0,
    CONSTRAINT fk_maintenance_telemetry_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
);
CREATE INDEX idx_maint_telemetry_machine_ts ON maintenance_telemetry(machine_id, ts DESC);

-- ============================================================
-- Event tables
-- ============================================================
CREATE TABLE alarm_events (
    id                  UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),
    machine_id          UNIQUEIDENTIFIER NOT NULL,
    alarm_code          VARCHAR(50),
    alarm_type          VARCHAR(50),
    severity            VARCHAR(20)      NOT NULL DEFAULT 'WARNING',
    message             NVARCHAR(MAX),
    started_at          DATETIME2(6)     NOT NULL DEFAULT SYSUTCDATETIME(),
    ended_at            DATETIME2(6),
    is_active           BIT              NOT NULL DEFAULT 1,
    acknowledged        BIT              NOT NULL DEFAULT 0,
    acknowledged_by     NVARCHAR(100),
    acknowledged_at     DATETIME2(6),
    raw_payload_json    NVARCHAR(MAX),
    CONSTRAINT fk_alarm_events_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
);
CREATE INDEX idx_alarm_machine_active ON alarm_events(machine_id, is_active);

CREATE TABLE downtime_events (
    id              UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),
    machine_id      UNIQUEIDENTIFIER NOT NULL,
    reason_code     VARCHAR(50),
    reason_group    VARCHAR(50),
    started_at      DATETIME2(6)     NOT NULL DEFAULT SYSUTCDATETIME(),
    ended_at        DATETIME2(6),
    duration_sec    INT,
    planned_stop    BIT              DEFAULT 0,
    abnormal_stop   BIT              DEFAULT 0,
    notes           NVARCHAR(MAX),
    CONSTRAINT fk_downtime_events_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
);
CREATE INDEX idx_downtime_machine ON downtime_events(machine_id, started_at DESC);

-- ============================================================
-- Aggregate tables
-- ============================================================
CREATE TABLE oee_snapshots (
    id                      UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),
    machine_id              UNIQUEIDENTIFIER NOT NULL,
    bucket_start            DATETIME2(6)     NOT NULL,
    bucket_type             VARCHAR(20)      NOT NULL,
    availability            FLOAT,
    performance             FLOAT,
    quality                 FLOAT,
    oee                     FLOAT,
    runtime_sec             INT,
    stop_sec                INT,
    good_count              INT,
    reject_count            INT,
    actual_cycle_time_sec   FLOAT,
    ideal_cycle_time_sec    FLOAT,
    CONSTRAINT fk_oee_snapshots_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
);
CREATE INDEX idx_oee_machine_bucket ON oee_snapshots(machine_id, bucket_start DESC);

CREATE TABLE machine_health_snapshots (
    id                  UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),
    machine_id          UNIQUEIDENTIFIER NOT NULL,
    bucket_start        DATETIME2(6)     NOT NULL,
    health_score        FLOAT,
    risk_level          VARCHAR(20),
    main_reason         NVARCHAR(200),
    temperature_score   FLOAT,
    vibration_score     FLOAT,
    alarm_score         FLOAT,
    runtime_score       FLOAT,
    CONSTRAINT fk_machine_health_snapshots_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
);
CREATE INDEX idx_health_machine ON machine_health_snapshots(machine_id, bucket_start DESC);

-- ============================================================
-- Prediction tables
-- ============================================================
CREATE TABLE tool_predictions (
    id                  UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),
    machine_id          UNIQUEIDENTIFIER NOT NULL,
    tool_code           VARCHAR(50),
    ts                  DATETIME2(6)     NOT NULL DEFAULT SYSUTCDATETIME(),
    remaining_minutes   FLOAT,
    remaining_cycles    INT,
    risk_level          VARCHAR(20),
    confidence_score    FLOAT,
    recommended_action  NVARCHAR(MAX),
    CONSTRAINT fk_tool_predictions_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
);

CREATE TABLE maintenance_predictions (
    id                          UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),
    machine_id                  UNIQUEIDENTIFIER NOT NULL,
    ts                          DATETIME2(6)     NOT NULL DEFAULT SYSUTCDATETIME(),
    remaining_hours_to_service  FLOAT,
    predicted_failure_risk      FLOAT,
    risk_level                  VARCHAR(20),
    recommended_action          NVARCHAR(MAX),
    next_maintenance_date       DATETIME2(6),
    CONSTRAINT fk_maintenance_predictions_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
);

