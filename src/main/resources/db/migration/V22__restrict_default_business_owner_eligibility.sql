-- V22 (M1.1): correct the V21 membership backfill. V21 is never edited (forward-only).
--
-- Problem: V21 made every global ADMIN with account status ACTIVE, INVITED or SUSPENDED an OWNER of the default
-- GrowthPrint business. An INVITED admin has never set a password and a SUSPENDED admin was deliberately blocked;
-- neither should silently hold business ownership.
--
-- Fix: in the default business only, an OWNER membership whose user account is not ACTIVE is downgraded to MEMBER
-- (least privilege). An active OWNER can promote them again once their account is active. Nothing is deleted.
--
-- No eligible owner: if the default business has no ACTIVE global ADMIN at all, it is left WITHOUT an owner rather
-- than inventing one. Business-management actions on it are then impossible until an owner is assigned through the
-- reviewed recovery procedure (docs/architecture/MVP2-M1-1-SECURITY-HARDENING.md, "Ownerless default business").
-- The application logs a warning at startup in that state (DefaultBusinessOwnerCheck).

UPDATE business_memberships m
SET role = 'MEMBER',
    updated_at = now()
FROM users u
WHERE m.user_id = u.id
  AND m.business_id = '6a1e0b7c-0000-4000-8000-00000000b001'
  AND m.role = 'OWNER'
  AND u.account_status <> 'ACTIVE';
