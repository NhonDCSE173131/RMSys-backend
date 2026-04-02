-- V2: Add machine_profiles, machine_profile_mappings, machine_import_batches
--     and extend machines table with PLC connection config

-- ============================================================
-- machine_profiles
-- ============================================================
CREATE TABLE machine_profiles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_code    VARCHAR(100)     NOT NULL UNIQUE,
    profile_name    VARCHAR(200)     NOT NULL,
    protocol        VARCHAR(50)      NOT NULL,
    vendor          VARCHAR(50),
    model           VARCHAR(100),
    description     TEXT,
    is_active       BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- ============================================================
-- machine_profile_mappings
-- ============================================================
CREATE TABLE machine_profile_mappings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id      UUID             NOT NULL REFERENCES machine_profiles(id) ON DELETE CASCADE,
    logical_key     VARCHAR(100)     NOT NULL,
    area            VARCHAR(50)      NOT NULL,
    address_start   INT              NOT NULL,
    address_end     INT,
    data_type       VARCHAR(30)      NOT NULL,
    scale_factor    DOUBLE PRECISION NOT NULL DEFAULT 1,
    unit            VARCHAR(20),
    byte_order      VARCHAR(10)      NOT NULL DEFAULT 'BIG',
    word_order      VARCHAR(10)      NOT NULL DEFAULT 'BIG',
    is_required     BOOLEAN          NOT NULL DEFAULT TRUE,
    description     TEXT,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    UNIQUE (profile_id, logical_key)
);

-- ============================================================
-- machine_import_batches
-- ============================================================
CREATE TABLE machine_import_batches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name       VARCHAR(500)     NOT NULL,
    import_type     VARCHAR(50)      NOT NULL,
    status          VARCHAR(30)      NOT NULL DEFAULT 'PENDING',
    total_rows      INT              NOT NULL DEFAULT 0,
    success_rows    INT              NOT NULL DEFAULT 0,
    failed_rows     INT              NOT NULL DEFAULT 0,
    error_summary   TEXT,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- ============================================================
-- Extend machines table with PLC connection config
-- ============================================================
ALTER TABLE machines ADD COLUMN protocol              VARCHAR(50);
ALTER TABLE machines ADD COLUMN host                  VARCHAR(255);
ALTER TABLE machines ADD COLUMN port                  INT;
ALTER TABLE machines ADD COLUMN unit_id               INT DEFAULT 1;
ALTER TABLE machines ADD COLUMN poll_interval_ms      INT DEFAULT 1000;
ALTER TABLE machines ADD COLUMN connection_mode       VARCHAR(20) DEFAULT 'MANUAL';
ALTER TABLE machines ADD COLUMN auto_connect          BOOLEAN DEFAULT FALSE;
ALTER TABLE machines ADD COLUMN profile_id            UUID REFERENCES machine_profiles(id);
ALTER TABLE machines ADD COLUMN last_connection_status VARCHAR(30);
ALTER TABLE machines ADD COLUMN last_connection_reason TEXT;
ALTER TABLE machines ADD COLUMN last_connected_at     TIMESTAMPTZ;
ALTER TABLE machines ADD COLUMN last_disconnected_at  TIMESTAMPTZ;
ALTER TABLE machines ADD COLUMN last_data_at          TIMESTAMPTZ;

