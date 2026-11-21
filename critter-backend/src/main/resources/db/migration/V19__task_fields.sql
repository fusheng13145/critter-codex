-- 志愿任务补齐发布与时长字段：V1 的 task 只有编号、类型、点位、名额，没有标题/说明/服务时长。
ALTER TABLE task
    ADD COLUMN title VARCHAR(64) NOT NULL DEFAULT '' AFTER task_no,
    ADD COLUMN description VARCHAR(255) NULL AFTER type,
    ADD COLUMN duration_hours DECIMAL(4, 1) NOT NULL DEFAULT 2.0 AFTER quota;

-- 服务时长允许半小时粒度，与任务时长口径一致。
ALTER TABLE volunteer
    MODIFY COLUMN total_hours DECIMAL(6, 1) NOT NULL DEFAULT 0;
