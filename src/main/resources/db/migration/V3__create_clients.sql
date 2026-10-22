CREATE TABLE clients (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    legal_name VARCHAR(160) NOT NULL,
    contact_email VARCHAR(254) NOT NULL,
    phone VARCHAR(50),
    website VARCHAR(2048) NOT NULL,
    status VARCHAR(20) NOT NULL,
    retainer_tier VARCHAR(50),
    monthly_retainer_hours INTEGER,
    consumed_hours_current_month INTEGER,
    contract_start_date DATE NOT NULL,
    account_manager VARCHAR(120) NOT NULL,
    billing_currency VARCHAR(3) NOT NULL,
    notes TEXT,
    CONSTRAINT clients_status_valid CHECK (status IN ('ACTIVE', 'INVITED', 'SUSPENDED')),
    CONSTRAINT clients_retainer_tier_valid CHECK (retainer_tier IS NULL OR retainer_tier IN ('ESSENTIAL_MAINTENANCE', 'PRO_GROWTH', 'NONE')),
    CONSTRAINT clients_billing_currency_valid CHECK (billing_currency IN ('USD', 'EUR', 'GBP'))
);

CREATE INDEX idx_clients_status ON clients (status);
CREATE INDEX idx_clients_account_manager ON clients (account_manager);
CREATE INDEX idx_clients_contract_start_date ON clients (contract_start_date);
