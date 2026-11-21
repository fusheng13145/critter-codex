-- T2.1 上报接口字段补齐：接口允许可选 animalCode 与 contact，原 report 表无对应列。
-- animal_id 外键关联个体档案；contact 仅管理员可见，不进入公开响应。
ALTER TABLE report
    ADD COLUMN animal_id BIGINT NULL COMMENT '关联个体档案，可选' AFTER reporter_id,
    ADD COLUMN contact VARCHAR(64) NULL COMMENT '上报人联系方式，仅管理员可见' AFTER close_reason,
    ADD CONSTRAINT fk_report_animal FOREIGN KEY (animal_id) REFERENCES animal (id),
    ADD KEY idx_report_animal (animal_id);
