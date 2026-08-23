ALTER TABLE control
    DROP CONSTRAINT IF EXISTS mode_check;

ALTER TABLE control
    ADD CONSTRAINT mode_check CHECK (
        mode IN (
            'BELOW_MAX_PRICE',
            'CHEAPEST_HOURS',
            'CHEAPEST_HOURS_TOMORROW_AWARE',
            'MANUAL',
            'SCHEDULED'
        )
    );
