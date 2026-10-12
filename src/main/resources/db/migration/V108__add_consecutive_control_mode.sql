ALTER TABLE control DROP CONSTRAINT IF EXISTS mode_check;

ALTER TABLE control ADD CONSTRAINT mode_check CHECK (
    mode IN ('BELOW_MAX_PRICE', 'CHEAPEST_HOURS', 'CHEAPEST_HOURS_TOMORROW_AWARE',
             'CHEAPEST_CONSECUTIVE_HOURS', 'MANUAL', 'SCHEDULED')
);

-- Distinguish the daily base block from extra below-minimum-price periods.
ALTER TABLE control_table ADD COLUMN consecutive_base BOOLEAN NOT NULL DEFAULT FALSE;
