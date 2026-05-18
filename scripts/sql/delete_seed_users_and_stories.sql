-- Delete seeded users and stories only (plus dependent rows for FK safety).
-- Seed user detection mirrors seeding command conventions:
--   - username starts with 'story_user_'
--   - OR email ends with '@grenobleski.local'
--
-- Safety guard:
--   - never delete staff/superuser accounts, even if email matches
--
-- This version is for PostgreSQL.
-- Run inside a transaction. Review with the preview queries first.

BEGIN;

-- Build a stable list of seeded users for this transaction.
DROP TABLE IF EXISTS tmp_seed_user_ids;
CREATE TEMP TABLE tmp_seed_user_ids AS
SELECT id
FROM auth_user
WHERE (
    username LIKE 'story_user_%'
    OR LOWER(COALESCE(email, '')) LIKE '%@grenobleski.local'
)
AND COALESCE(is_staff, FALSE) = FALSE
AND COALESCE(is_superuser, FALSE) = FALSE;

-- Preview counts before deletion.
SELECT COUNT(*) AS seed_users
FROM tmp_seed_user_ids;

SELECT COUNT(*) AS seed_stories
FROM api_skistory s
WHERE s.user_id IN (SELECT id FROM tmp_seed_user_ids);

-- 1) Remove story data authored by seeded users.
DELETE FROM api_skistorylike
WHERE story_id IN (
    SELECT id
    FROM api_skistory
    WHERE user_id IN (SELECT id FROM tmp_seed_user_ids)
)
OR user_id IN (
    SELECT id FROM tmp_seed_user_ids
);

DELETE FROM api_skistorycomment
WHERE story_id IN (
    SELECT id
    FROM api_skistory
    WHERE user_id IN (SELECT id FROM tmp_seed_user_ids)
)
OR user_id IN (
    SELECT id FROM tmp_seed_user_ids
);

DELETE FROM api_skistory
WHERE user_id IN (SELECT id FROM tmp_seed_user_ids);

-- 2) Remove seeded users (with their dependent rows).
DELETE FROM api_message
WHERE sender_id IN (SELECT id FROM tmp_seed_user_ids)
OR recipient_id IN (
    SELECT id FROM tmp_seed_user_ids
);

DELETE FROM api_userfriend
WHERE user_id IN (SELECT id FROM tmp_seed_user_ids)
OR friend_id IN (
    SELECT id FROM tmp_seed_user_ids
);

DELETE FROM authtoken_token
WHERE user_id IN (SELECT id FROM tmp_seed_user_ids);

DELETE FROM django_admin_log
WHERE user_id IN (SELECT id FROM tmp_seed_user_ids);

DELETE FROM api_userprofile
WHERE user_id IN (SELECT id FROM tmp_seed_user_ids);

DELETE FROM auth_user
WHERE id IN (SELECT id FROM tmp_seed_user_ids);

DROP TABLE IF EXISTS tmp_seed_user_ids;

COMMIT;

-- To abort manually before commit, use: ROLLBACK;
