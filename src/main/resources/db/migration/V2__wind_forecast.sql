CREATE TABLE fingrid_data
(
    id         BIGSERIAL PRIMARY KEY,
    dataset_id INTEGER        NOT NULL,
    start_time TIMESTAMPTZ    NOT NULL,
    end_time   TIMESTAMPTZ    NOT NULL,
    value      NUMERIC(14, 4) NOT NULL,
    CONSTRAINT uk_fingrid_data UNIQUE (dataset_id, start_time, end_time)
);
