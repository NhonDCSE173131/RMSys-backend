-- V4: Version mappings by imported file id (SQL Server)

IF COL_LENGTH('machine_profile_mappings', 'mapping_file_id') IS NULL
    ALTER TABLE machine_profile_mappings ADD mapping_file_id UNIQUEIDENTIFIER NULL;

IF EXISTS (
    SELECT 1 FROM sys.key_constraints
    WHERE [type] = 'UQ'
      AND [name] = 'uq_profile_logical_key'
      AND [parent_object_id] = OBJECT_ID('machine_profile_mappings')
)
BEGIN
    ALTER TABLE machine_profile_mappings DROP CONSTRAINT uq_profile_logical_key;
END

IF NOT EXISTS (
    SELECT 1 FROM sys.key_constraints
    WHERE [type] = 'UQ'
      AND [name] = 'uq_profile_mapping_file_logical_key'
      AND [parent_object_id] = OBJECT_ID('machine_profile_mappings')
)
BEGIN
    ALTER TABLE machine_profile_mappings
        ADD CONSTRAINT uq_profile_mapping_file_logical_key
        UNIQUE (profile_id, mapping_file_id, logical_key);
END

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'idx_profile_mappings_profile_file'
      AND object_id = OBJECT_ID('machine_profile_mappings')
)
    CREATE INDEX idx_profile_mappings_profile_file
        ON machine_profile_mappings(profile_id, mapping_file_id);

