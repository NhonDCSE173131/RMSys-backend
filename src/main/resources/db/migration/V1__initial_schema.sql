-- V1: Initial schema for Manufacturing Monitor
-- Master tables
CREATE TABLE machines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50)  NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    type            VARCHAR(50)  NOT NULL DEFAULT 'CNC_MACHINE',
    vendor          VARCHAR(50)  NOT NULL DEFAULT 'UNKNOWN',
    model           VARCHAR(200),
    line_id         VARCHAR(50),
    plant_id        VARCHAR(50),
    status          VARCHAR(30)  NOT NULL DEFAULT 'OFFLINE',
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tool_catalogs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    machine_id              UUID         NOT NULL REFERENCES machines(id),
    tool_code               VARCHAR(50)  NOT NULL,
    tool_name               VARCHAR(200) NOT NULL,
    tool_type               VARCHAR(50),
    life_limit_minutes      INT,
    life_limit_cycles       INT,
    warning_threshold_pct   DOUBLE PRECISION DEFAULT 20,
    critical_threshold_pct  DOUBLE PRECISION DEFAULT 10,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE machine_thresholds (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    machine_id      UUID         NOT NULL REFERENCES machines(id),
    metric_code     VARCHAR(50)  NOT NULL,
    warning_value   DOUBLE PRECISION,
    critical_value  DOUBLE PRECISION,
    unit            VARCHAR(20),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (machine_id, metric_code)
);

-- Time-series tables
CREATE TABLE machine_telemetry (
    id                  BIGSERIAL PRIMARY KEY,
    machine_id          UUID         NOT NULL REFERENCES machines(id),
    ts                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    connection_status   VARCHAR(20),
    machine_state       VARCHAR(30),
    operation_mode      VARCHAR(30),
    alarm_active        BOOLEAN DEFAULT FALSE,
    program_name        VARCHAR(200),
    cycle_running       BOOLEAN DEFAULT FALSE,
    current_job         VARCHAR(200),
    power_kw            DOUBLE PRECISION,
    temperature_c       DOUBLE PRECISION,
    vibration_mm_s      DOUBLE PRECISION,
    runtime_hours       DOUBLE PRECISION,
    cycle_time_sec      DOUBLE PRECISION,
    output_count        INT DEFAULT 0,
    good_count          INT DEFAULT 0,
    reject_count        INT DEFAULT 0,
    spindle_speed_rpm   DOUBLE PRECISION,
    feed_rate_mm_min    DOUBLE PRECISION,
    axis_load_pct       DOUBLE PRECISION,
    metadata_json       JSONB
);
CREATE INDEX idx_telemetry_machine_ts ON machine_telemetry(machine_id, ts DESC);

CREATE TABLE energy_telemetry (
    id                  BIGSERIAL PRIMARY KEY,
    machine_id          UUID         NOT NULL REFERENCES machines(id),
    ts                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    voltage_v           DOUBLE PRECISION,
    current_a           DOUBLE PRECISION,
    power_kw            DOUBLE PRECISION,
    power_factor        DOUBLE PRECISION,
    frequency_hz        DOUBLE PRECISION,
    energy_kwh_shift    DOUBLE PRECISION,
    energy_kwh_day      DOUBLE PRECISION,
    energy_kwh_month    DOUBLE PRECISION
);
CREATE INDEX idx_energy_machine_ts ON energy_telemetry(machine_id, ts DESC);

CREATE TABLE tool_usage_telemetry (
    id                      BIGSERIAL PRIMARY KEY,
    machine_id              UUID         NOT NULL REFERENCES machines(id),
    ts                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    tool_code               VARCHAR(50),
    tool_number             INT,
    usage_minutes           DOUBLE PRECISION,
    usage_cycles            INT,
    spindle_load_pct        DOUBLE PRECISION,
    tool_temperature_c      DOUBLE PRECISION,
    remaining_life_pct      DOUBLE PRECISION,
    wear_level              VARCHAR(20)
);
CREATE INDEX idx_tool_usage_machine_ts ON tool_usage_telemetry(machine_id, ts DESC);

CREATE TABLE maintenance_telemetry (
    id                      BIGSERIAL PRIMARY KEY,
    machine_id              UUID         NOT NULL REFERENCES machines(id),
    ts                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    motor_temperature_c     DOUBLE PRECISION,
    bearing_temperature_c   DOUBLE PRECISION,
    cabinet_temperature_c   DOUBLE PRECISION,
    vibration_mm_s          DOUBLE PRECISION,
    runtime_hours           DOUBLE PRECISION,
    servo_on_hours          DOUBLE PRECISION,
    start_stop_count        INT,
    lubrication_level_pct   DOUBLE PRECISION,
    battery_low             BOOLEAN DEFAULT FALSE
);
CREATE INDEX idx_maint_telemetry_machine_ts ON maintenance_telemetry(machine_id, ts DESC);

-- Event tables
CREATE TABLE alarm_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    machine_id          UUID         NOT NULL REFERENCES machines(id),
    alarm_code          VARCHAR(50),
    alarm_type          VARCHAR(50),
    severity            VARCHAR(20)  NOT NULL DEFAULT 'WARNING',
    message             TEXT,
    started_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ended_at            TIMESTAMPTZ,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    acknowledged        BOOLEAN      NOT NULL DEFAULT FALSE,
    acknowledged_by     VARCHAR(100),
    acknowledged_at     TIMESTAMPTZ,
    raw_payload_json    JSONB
);
CREATE INDEX idx_alarm_machine_active ON alarm_events(machine_id, is_active);

CREATE TABLE downtime_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    machine_id      UUID         NOT NULL REFERENCES machines(id),
    reason_code     VARCHAR(50),
    reason_group    VARCHAR(50),
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ,
    duration_sec    INT,
    planned_stop    BOOLEAN DEFAULT FALSE,
    abnormal_stop   BOOLEAN DEFAULT FALSE,
    notes           TEXT
);
CREATE INDEX idx_downtime_machine ON downtime_events(machine_id, started_at DESC);

-- Aggregate tables
CREATE TABLE oee_snapshots (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    machine_id              UUID         NOT NULL REFERENCES machines(id),
    bucket_start            TIMESTAMPTZ  NOT NULL,
    bucket_type             VARCHAR(20)  NOT NULL,
    availability            DOUBLE PRECISION,
    performance             DOUBLE PRECISION,
    quality                 DOUBLE PRECISION,
    oee                     DOUBLE PRECISION,
    runtime_sec             INT,
    stop_sec                INT,
    good_count              INT,
    reject_count            INT,
    actual_cycle_time_sec   DOUBLE PRECISION,
    ideal_cycle_time_sec    DOUBLE PRECISION
);
CREATE INDEX idx_oee_machine_bucket ON oee_snapshots(machine_id, bucket_start DESC);

CREATE TABLE machine_health_snapshots (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    machine_id          UUID         NOT NULL REFERENCES machines(id),
    bucket_start        TIMESTAMPTZ  NOT NULL,
    health_score        DOUBLE PRECISION,
    risk_level          VARCHAR(20),
    main_reason         VARCHAR(200),
    temperature_score   DOUBLE PRECISION,
    vibration_score     DOUBLE PRECISION,
    alarm_score         DOUBLE PRECISION,
    runtime_score       DOUBLE PRECISION
);
CREATE INDEX idx_health_machine ON machine_health_snapshots(machine_id, bucket_start DESC);

-- Prediction tables
CREATE TABLE tool_predictions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    machine_id              UUID         NOT NULL REFERENCES machines(id),
    tool_code               VARCHAR(50),
    ts                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    remaining_minutes       DOUBLE PRECISION,
    remaining_cycles        INT,
    risk_level              VARCHAR(20),
    confidence_score        DOUBLE PRECISION,
    recommended_action      TEXT
);

CREATE TABLE maintenance_predictions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    machine_id                  UUID         NOT NULL REFERENCES machines(id),
    ts                          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    remaining_hours_to_service  DOUBLE PRECISION,
    predicted_failure_risk      DOUBLE PRECISION,
    risk_level                  VARCHAR(20),
    recommended_action          TEXT,
    next_maintenance_date       TIMESTAMPTZ
);

