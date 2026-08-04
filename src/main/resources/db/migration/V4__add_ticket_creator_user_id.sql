ALTER TABLE tickets
    ADD COLUMN creator_user_id BIGINT UNSIGNED NULL AFTER creator_name,
    ADD INDEX idx_tickets_creator_user_id (creator_user_id),
    ADD CONSTRAINT fk_tickets_creator_user
        FOREIGN KEY (creator_user_id)
        REFERENCES users (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT;
