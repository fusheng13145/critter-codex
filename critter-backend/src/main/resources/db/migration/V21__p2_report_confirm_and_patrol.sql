-- P2 补齐：上报结案确认 / 无效标记、巡查记录与转目击。
-- 结案确认：工单关闭后上报人可在 7 天内确认；到期未确认由定时任务自动确认。
-- 无效上报：只允许对未结案上报标记无效，统计口径排除 invalid=1。

ALTER TABLE report
    ADD COLUMN confirmed_at DATETIME NULL COMMENT '上报人确认结案时间；NULL 表示待确认' AFTER close_reason,
    ADD COLUMN confirm_due_at DATETIME NULL COMMENT '结案确认截止时间；关闭时写入 7 天后' AFTER confirmed_at,
    ADD COLUMN auto_confirmed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否由定时任务自动确认' AFTER confirm_due_at,
    ADD COLUMN invalid TINYINT(1) NOT NULL DEFAULT 0 COMMENT '无效上报标记，统计时排除' AFTER auto_confirmed,
    ADD COLUMN invalid_reason VARCHAR(255) NULL COMMENT '判定无效的原因' AFTER invalid,
    ADD KEY idx_report_confirm_due (confirm_due_at, confirmed_at),
    ADD KEY idx_report_invalid (invalid, created_at);

CREATE TABLE IF NOT EXISTS patrol_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    point_id BIGINT NOT NULL COMMENT '巡查点位',
    patrol_at DATETIME NOT NULL COMMENT '巡查时间',
    count_estimate SMALLINT NULL COMMENT '目测数量，可空',
    has_anomaly TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否发现异常',
    anomaly_note VARCHAR(255) NULL COMMENT '异常说明',
    file_ids JSON NULL COMMENT '现场照片 fileId',
    recorder_id BIGINT NOT NULL COMMENT '巡查人',
    converted_sighting_id BIGINT NULL COMMENT '转成的目击记录 id；为空表示尚未转目击',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_patrol_point_time (point_id, patrol_at),
    KEY idx_patrol_converted (converted_sighting_id),
    CONSTRAINT fk_patrol_point FOREIGN KEY (point_id) REFERENCES base_point (id),
    CONSTRAINT fk_patrol_sighting FOREIGN KEY (converted_sighting_id) REFERENCES sighting (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '校园生物巡查记录';
