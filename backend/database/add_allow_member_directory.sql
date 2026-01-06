-- Add allowMemberDirectory field to team table
ALTER TABLE team
    ADD COLUMN allowMemberDirectory TINYINT(1) DEFAULT 1 COMMENT 'Allow members to view member directory';

