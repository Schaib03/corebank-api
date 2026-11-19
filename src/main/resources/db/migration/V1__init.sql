CREATE TABLE customers (
    id          UUID PRIMARY KEY,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    phone       VARCHAR(30),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_customers_email UNIQUE (email)
);

CREATE TABLE app_users (
    id             UUID PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    customer_id    UUID REFERENCES customers (id),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    account_number  VARCHAR(16)    NOT NULL,
    customer_id     UUID           NOT NULL REFERENCES customers (id),
    type            VARCHAR(20)    NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    balance         NUMERIC(19, 2) NOT NULL DEFAULT 0,
    status          VARCHAR(20)    NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_accounts_number UNIQUE (account_number),
    CONSTRAINT ck_accounts_balance CHECK (balance >= 0),
    CONSTRAINT ck_accounts_type CHECK (type IN ('CURRENT', 'SAVINGS')),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

CREATE INDEX idx_accounts_customer ON accounts (customer_id);

CREATE TABLE account_transactions (
    id                      UUID PRIMARY KEY,
    account_id              UUID           NOT NULL REFERENCES accounts (id),
    type                    VARCHAR(20)    NOT NULL,
    amount                  NUMERIC(19, 2) NOT NULL,
    balance_after           NUMERIC(19, 2) NOT NULL,
    reference               UUID           NOT NULL,
    counterparty_account    VARCHAR(16),
    description             VARCHAR(255),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_tx_amount CHECK (amount > 0),
    CONSTRAINT ck_tx_type CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT'))
);

CREATE INDEX idx_tx_account_created ON account_transactions (account_id, created_at DESC);
CREATE INDEX idx_tx_reference ON account_transactions (reference);
