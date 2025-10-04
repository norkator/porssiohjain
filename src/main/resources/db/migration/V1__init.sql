CREATE TABLE nordpool
(
    id             BIGSERIAL PRIMARY KEY,
    delivery_start TIMESTAMPTZ    NOT NULL,
    delivery_end   TIMESTAMPTZ    NOT NULL,
    price_fi       NUMERIC(10, 4) NOT NULL,
    CONSTRAINT uk_delivery UNIQUE (delivery_start, delivery_end)
);
