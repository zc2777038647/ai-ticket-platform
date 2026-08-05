ALTER TABLE tickets
    ADD COLUMN assignee_user_id BIGINT UNSIGNED NULL
        AFTER creator_user_id,
    ADD INDEX idx_tickets_assignee_user_id
        (assignee_user_id),
    ADD CONSTRAINT fk_tickets_assignee_user
        FOREIGN KEY (assignee_user_id)
        REFERENCES users (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT;
