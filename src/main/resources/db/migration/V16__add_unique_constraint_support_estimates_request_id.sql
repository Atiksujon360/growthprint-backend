-- V16: Add unique constraint on support_estimates.request_id
-- This migration ensures that each support request can have at most one active estimate.
-- This allows safe re-classification of support requests without creating duplicate estimates.
--
-- CONTEXT:
-- Previously, re-classifying a support request after an estimate existed would reset the
-- request to ESTIMATE_PENDING and allow a second estimate, which broke data integrity
-- (support_estimates had no unique constraint on request_id).
--
-- This constraint enforces the one-active-estimate-per-request rule at the database level.
-- When re-classifying, the application must delete any existing estimate before allowing
-- classification to proceed (handled in SupportRequestService.classify()).

-- Drop the non-unique index and replace with a unique constraint
DROP INDEX IF EXISTS idx_support_estimates_request_id;

-- Add unique constraint on request_id to ensure one estimate per request
ALTER TABLE support_estimates
ADD CONSTRAINT uk_support_estimates_request_id UNIQUE (request_id);

-- Add comment explaining the constraint
COMMENT ON CONSTRAINT uk_support_estimates_request_id ON support_estimates
IS 'Ensures each support request has at most one active estimate; required for safe re-classification';
