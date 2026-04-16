ALTER TABLE load_shedding_link
    ADD COLUMN reverse_on_clear BOOLEAN NOT NULL DEFAULT FALSE;
