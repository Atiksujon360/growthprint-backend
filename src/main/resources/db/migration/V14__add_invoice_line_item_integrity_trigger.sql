-- V14: Add invoice line-item integrity trigger
-- This migration adds a database trigger to enforce that line-item amounts match quantity × unit_price
--
-- BUSINESS RULE:
-- amount = ROUND(quantity × unit_price, 2) using HALF_UP rounding
--
-- ENFORCEMENT:
-- Trigger validates the calculation on INSERT and UPDATE
-- Service-layer validation remains in place for defense-in-depth
--
-- COMPATIBILITY:
-- Existing data should be consistent (validated by service layer on creation)
-- Trigger only rejects invalid future data

-- Trigger function to validate line item amount calculation
CREATE OR REPLACE FUNCTION validate_line_item_amount()
RETURNS TRIGGER AS $$
DECLARE
    calculated_amount DECIMAL(12,2);
BEGIN
    -- Calculate: amount = ROUND(quantity × unit_price, 2)
    -- This matches the application's RoundingMode.HALF_UP behavior
    calculated_amount = ROUND(NEW.quantity::NUMERIC * NEW.unit_price, 2);
    
    -- Validate that the provided amount matches the calculation
    IF NEW.amount != calculated_amount THEN
        RAISE EXCEPTION 'Line item amount % does not match quantity % × unit price % = % (rounded to 2 decimals)',
            NEW.amount, NEW.quantity, NEW.unit_price, calculated_amount;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for INSERT operations
DROP TRIGGER IF EXISTS trigger_validate_line_item_amount_insert ON invoice_line_items;
CREATE TRIGGER trigger_validate_line_item_amount_insert
    BEFORE INSERT ON invoice_line_items
    FOR EACH ROW
    EXECUTE FUNCTION validate_line_item_amount();

-- Create trigger for UPDATE operations
DROP TRIGGER IF EXISTS trigger_validate_line_item_amount_update ON invoice_line_items;
CREATE TRIGGER trigger_validate_line_item_amount_update
    BEFORE UPDATE ON invoice_line_items
    FOR EACH ROW
    EXECUTE FUNCTION validate_line_item_amount();

-- Comment
COMMENT ON FUNCTION validate_line_item_amount() IS 'Validates that line item amount equals quantity × unit price (rounded to 2 decimals)';
COMMENT ON TRIGGER trigger_validate_line_item_amount_insert ON invoice_line_items IS 'Validates line item amount on INSERT';
COMMENT ON TRIGGER trigger_validate_line_item_amount_update ON invoice_line_items IS 'Validates line item amount on UPDATE';
