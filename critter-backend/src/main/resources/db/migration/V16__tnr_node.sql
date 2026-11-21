-- TNR 节点流水：tnr_task 保留当前节点快照，本表保存每次推进的责任人、时间与附件。
CREATE TABLE IF NOT EXISTS tnr_node (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tnr_no VARCHAR(24) NOT NULL,
    stage VARCHAR(16) NOT NULL,
    handler_id BIGINT NOT NULL,
    occurred_at DATETIME NOT NULL,
    file_ids JSON NULL,
    note VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tnr_node_stage (tnr_no, stage),
    KEY idx_tnr_node_time (tnr_no, occurred_at),
    CONSTRAINT fk_tnr_node_task FOREIGN KEY (tnr_no) REFERENCES tnr_task (tnr_no),
    CONSTRAINT ck_tnr_node_stage CHECK (stage IN ('CATCH', 'TRANSPORT', 'SURGERY', 'RELEASE', 'OBSERVE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'TNR 节点流水';
