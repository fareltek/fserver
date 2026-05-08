-- archive.path is now controlled exclusively via ARCHIVE_PATH env var (application.yml).
-- Runtime path changes are not permitted in safety-critical deployments.
DELETE FROM system_config WHERE config_key = 'archive.path';
