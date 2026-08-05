CREATE TABLE ticket_operation_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT UNSIGNED NOT NULL,
    operator_user_id BIGINT UNSIGNED NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    before_value VARCHAR(64) NULL,
    after_value VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),

    KEY idx_ticket_operation_logs_ticket_time
        (ticket_id, created_at, id),

    KEY idx_ticket_operation_logs_operator_time
        (operator_user_id, created_at, id),

    CONSTRAINT fk_ticket_operation_logs_ticket
        FOREIGN KEY (ticket_id)
        REFERENCES tickets (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_ticket_operation_logs_operator
        FOREIGN KEY (operator_user_id)
        REFERENCES users (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
