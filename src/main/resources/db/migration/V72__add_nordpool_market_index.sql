ALTER TABLE account
    ADD COLUMN market_index_name VARCHAR(10) NOT NULL DEFAULT 'FI';

ALTER TABLE nordpool
    ADD COLUMN market_index_name VARCHAR(10) NOT NULL DEFAULT 'FI';

ALTER TABLE nordpool
    DROP CONSTRAINT IF EXISTS uk_delivery;

ALTER TABLE nordpool
    ADD CONSTRAINT uk_nordpool_market_delivery UNIQUE (market_index_name, delivery_start, delivery_end);

CREATE INDEX idx_nordpool_market_delivery_start
    ON nordpool (market_index_name, delivery_start);
