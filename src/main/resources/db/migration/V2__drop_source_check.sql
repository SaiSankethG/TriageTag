-- V2: remove the CHECK (source = 'GITHUB') that Hibernate added to the existing database.
-- It would reject Jira rows later. IF EXISTS: a database built from V1 never had it.
ALTER TABLE issue DROP CONSTRAINT IF EXISTS issue_source_check;
