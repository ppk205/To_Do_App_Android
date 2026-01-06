-- Expand role column to fit values like 'co-manager'
ALTER TABLE teammember
    MODIFY COLUMN role VARCHAR(32) NOT NULL DEFAULT 'member';

