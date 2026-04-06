-- V3__machine_import_and_mapping_file_tracking.sql
-- Add fields to track import files and mapping file references on machines

-- Add fields to machine_import_batches table
ALTER TABLE machine_import_batches
ADD COLUMN profile_code VARCHAR(100) NULL,
ADD COLUMN profile_id UUID NULL,
ADD COLUMN uploaded_by VARCHAR(100) DEFAULT 'system';

-- Add mapping_file_id to machines table for traceability
ALTER TABLE machines
ADD COLUMN mapping_file_id UUID NULL;

-- Create index for profile_code lookups
CREATE INDEX idx_import_batches_profile_code ON machine_import_batches(profile_code);
CREATE INDEX idx_import_batches_import_type_status ON machine_import_batches(import_type, status);
CREATE INDEX idx_import_batches_created_at ON machine_import_batches(created_at DESC);
CREATE INDEX idx_machines_mapping_file_id ON machines(mapping_file_id);

