-- V3: Add import batch metadata and mapping file reference on machines (SQL Server)

IF COL_LENGTH('machine_import_batches', 'profile_code') IS NULL
    ALTER TABLE machine_import_batches ADD profile_code VARCHAR(100) NULL;

IF COL_LENGTH('machine_import_batches', 'profile_id') IS NULL
    ALTER TABLE machine_import_batches ADD profile_id UNIQUEIDENTIFIER NULL;

IF COL_LENGTH('machine_import_batches', 'uploaded_by') IS NULL
    ALTER TABLE machine_import_batches ADD uploaded_by VARCHAR(100) NULL CONSTRAINT df_machine_import_batches_uploaded_by DEFAULT 'system';

IF COL_LENGTH('machines', 'mapping_file_id') IS NULL
    ALTER TABLE machines ADD mapping_file_id UNIQUEIDENTIFIER NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_import_batches_profile_code' AND object_id = OBJECT_ID('machine_import_batches'))
    CREATE INDEX idx_import_batches_profile_code ON machine_import_batches(profile_code);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_import_batches_import_type_status' AND object_id = OBJECT_ID('machine_import_batches'))
    CREATE INDEX idx_import_batches_import_type_status ON machine_import_batches(import_type, status);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_import_batches_created_at' AND object_id = OBJECT_ID('machine_import_batches'))
    CREATE INDEX idx_import_batches_created_at ON machine_import_batches(created_at DESC);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_machines_mapping_file_id' AND object_id = OBJECT_ID('machines'))
    CREATE INDEX idx_machines_mapping_file_id ON machines(mapping_file_id);

