ALTER TABLE visit
    ADD COLUMN source_note VARCHAR(255) NULL COMMENT '无工单时的来源备注' AFTER file_ids;
