create table price_prediction
(
    id          bigserial primary key,
    timestamp   timestamptz    not null,
    price_cents numeric(10, 4) not null,
    created_at  timestamptz    not null default now()
);

create index idx_price_prediction_timestamp
    on price_prediction (timestamp);
