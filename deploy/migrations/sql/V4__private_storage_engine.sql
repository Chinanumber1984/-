-- Preserve existing LOCAL attachments while new files may use a private MinIO bucket.
ALTER TABLE SRM_ATTACHMENT_SCOPE ADD STORAGE_ENGINE varchar(16) NOT NULL DEFAULT 'LOCAL';
