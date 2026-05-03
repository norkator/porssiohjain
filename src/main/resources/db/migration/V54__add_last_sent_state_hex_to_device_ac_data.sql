ALTER TABLE device_ac_data
    ADD COLUMN last_sent_state_hex TEXT;

UPDATE device_ac_data
SET last_sent_state_hex = last_polled_state_hex
WHERE last_sent_state_hex IS NULL
  AND last_polled_state_hex IS NOT NULL;
