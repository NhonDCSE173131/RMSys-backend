-- V4: Version mappings by imported file id (PostgreSQL)

ALTER TABLE machine_profile_mappings
	ADD COLUMN IF NOT EXISTS mapping_file_id UUID;

DO $$
BEGIN
	IF EXISTS (
		SELECT 1 FROM information_schema.table_constraints
		WHERE table_name = 'machine_profile_mappings'
		  AND constraint_name = 'uq_profile_logical_key'
	) THEN
		ALTER TABLE machine_profile_mappings DROP CONSTRAINT uq_profile_logical_key;
	END IF;

	IF EXISTS (
		SELECT 1 FROM information_schema.table_constraints
		WHERE table_name = 'machine_profile_mappings'
		  AND constraint_name = 'machine_profile_mappings_profile_id_logical_key_key'
	) THEN
		ALTER TABLE machine_profile_mappings DROP CONSTRAINT machine_profile_mappings_profile_id_logical_key_key;
	END IF;
END $$;

DO $$
BEGIN
	IF NOT EXISTS (
		SELECT 1 FROM information_schema.table_constraints
		WHERE table_name = 'machine_profile_mappings'
		  AND constraint_name = 'uq_profile_mapping_file_logical_key'
	) THEN
		ALTER TABLE machine_profile_mappings
			ADD CONSTRAINT uq_profile_mapping_file_logical_key
			UNIQUE (profile_id, mapping_file_id, logical_key);
	END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_profile_mappings_profile_file
	ON machine_profile_mappings(profile_id, mapping_file_id);

