-- Preserve existing connection IDs, encrypted sessions, labels and device mappings.
ALTER TABLE tuya_connection DROP CONSTRAINT tuya_connection_account_id_key;
ALTER TABLE tuya_connection
    ADD CONSTRAINT tuya_connection_account_provider_key UNIQUE (account_id, provider_user_id);
