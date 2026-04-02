-- V2: Add machine_profiles, machine_profile_mappings, machine_import_batches
--     and extend machines table with PLC connection config

-- ============================================================
-- machine_profiles
-- ============================================================
CREATE TABLE machine_profiles (
    id              UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),
    profile_code    VARCHAR(100)     NOT NULL UNIQUE,
    profile_name    NVARCHAR(200)    NOT NULL,
    protocol        VARCHAR(50)      NOT NULL,
    vendor          VARCHAR(50),
    model           VARCHAR(100),
    description     NVARCHAR(500),
    is_active       BIT              NOT NULL DEFAULT 1,
    created_at      DATETIME2(6)     NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2(6)     NOT NULL DEFAULT SYSUTCDATETIME()
);

-- ============================================================
-- machine_profile_mappings
-- ============================================================
CREATE TABLE machine_profile_mappings (
    id              UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),
    profile_id      UNIQUEIDENTIFIER NOT NULL,
    logical_key     VARCHAR(100)     NOT NULL,
    area            VARCHAR(50)      NOT NULL,
    address_start   INT              NOT NULL,
    address_end     INT,
    data_type       VARCHAR(30)      NOT NULL,
    scale_factor    FLOAT            NOT NULL DEFAULT 1,
    unit            VARCHAR(20),
    byte_order      VARCHAR(10)      NOT NULL DEFAULT 'BIG',
    word_order      VARCHAR(10)      NOT NULL DEFAULT 'BIG',
    is_required     BIT              NOT NULL DEFAULT 1,
    description     NVARCHAR(500),
    created_at      DATETIME2(6)     NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2(6)     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_profile_mappings_profile FOREIGN KEY (profile_id) REFERENCES machine_profiles(id) ON DELETE CASCADE,
    CONSTRAINT uq_profile_logical_key UNIQUE (profile_id, logical_key)
);

-- ============================================================
-- machine_import_batches
-- ============================================================
CREATE TABLE machine_import_batches (
    id              UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),
    file_name       NVARCHAR(500)    NOT NULL,
    import_type     VARCHAR(50)      NOT NULL,
    status          VARCHAR(30)      NOT NULL DEFAULT 'PENDING',
    total_rows      INT              NOT NULL DEFAULT 0,
    success_rows    INT              NOT NULL DEFAULT 0,
    failed_rows     INT              NOT NULL DEFAULT 0,
    error_summary   NVARCHAR(MAX),
    created_at      DATETIME2(6)     NOT NULL DEFAULT SYSUTCDATETIME()
);

-- ============================================================
-- Extend machines table with PLC connection config
-- ============================================================
ALTER TABLE machines ADD protocol              VARCHAR(50)      NULL;
ALTER TABLE machines ADD host                  VARCHAR(255)     NULL;
ALTER TABLE machines ADD port                  INT              NULL;
ALTER TABLE machines ADD unit_id               INT              NULL DEFAULT 1;
ALTER TABLE machines ADD poll_interval_ms      INT              NULL DEFAULT 1000;
ALTER TABLE machines ADD connection_mode       VARCHAR(20)      NULL DEFAULT 'MANUAL';
ALTER TABLE machines ADD auto_connect          BIT              NULL DEFAULT 0;
ALTER TABLE machines ADD profile_id            UNIQUEIDENTIFIER NULL;
ALTER TABLE machines ADD last_connection_status VARCHAR(30)     NULL;
ALTER TABLE machines ADD last_connection_reason NVARCHAR(500)   NULL;
ALTER TABLE machines ADD last_connected_at     DATETIME2(6)     NULL;
ALTER TABLE machines ADD last_disconnected_at  DATETIME2(6)     NULL;
ALTER TABLE machines ADD last_data_at          DATETIME2(6)     NULL;

-- FK from machines to machine_profiles
ALTER TABLE machines ADD CONSTRAINT fk_machines_profile FOREIGN KEY (profile_id) REFERENCES machine_profiles(id);

