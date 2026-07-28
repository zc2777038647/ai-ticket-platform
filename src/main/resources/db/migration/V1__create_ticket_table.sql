CREATE TABLE tickets (
                         id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工单ID',

                         title VARCHAR(120) NOT NULL COMMENT '工单标题',

                         description TEXT NOT NULL COMMENT '工单描述',

                         creator_name VARCHAR(64) NOT NULL COMMENT '创建人名称',

                         priority VARCHAR(16) NOT NULL COMMENT '优先级：LOW、MEDIUM、HIGH',

                         status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                             COMMENT '状态：PENDING、PROCESSING、COMPLETED、CLOSED',

                         created_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

                         updated_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',

                         PRIMARY KEY (id),

                         INDEX idx_tickets_status_created_at (
        status,
        created_at
    ),

                         INDEX idx_tickets_creator_name (
        creator_name
    )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '工单表';