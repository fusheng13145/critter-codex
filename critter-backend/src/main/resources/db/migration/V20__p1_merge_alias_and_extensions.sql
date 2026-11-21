-- P1 补齐：档案合并别名、群体合并来源、上报物种、捐赠异常原因、结项追加说明。
ALTER TABLE animal
    ADD COLUMN merged_into_id BIGINT NULL COMMENT '并入的主档案 id；为空表示正常档案' AFTER adopted,
    ADD COLUMN merged_at DATETIME NULL COMMENT '合并时间' AFTER merged_into_id,
    ADD KEY idx_animal_merged_into (merged_into_id);

CREATE TABLE IF NOT EXISTS animal_alias (
    alias_code VARCHAR(20) NOT NULL COMMENT '被合并的副编号',
    animal_id BIGINT NOT NULL COMMENT '指向主档案',
    source_animal_id BIGINT NULL COMMENT '原子档案 id，保留审计',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (alias_code),
    KEY idx_animal_alias_animal (animal_id),
    CONSTRAINT fk_animal_alias_animal FOREIGN KEY (animal_id) REFERENCES animal (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '档案合并后的副编号别名';

ALTER TABLE animal_group
    ADD COLUMN merged_into_code VARCHAR(20) NULL COMMENT '被并入的主群体编号' AFTER split_from,
    ADD KEY idx_group_merged_into (merged_into_code);

ALTER TABLE report
    ADD COLUMN species_id BIGINT NULL COMMENT '上报物种，野猪等大型野生动物据此拦截' AFTER animal_id,
    ADD CONSTRAINT fk_report_species FOREIGN KEY (species_id) REFERENCES base_species (id),
    ADD KEY idx_report_species (species_id);

ALTER TABLE donation
    ADD COLUMN abnormal_reason VARCHAR(255) NULL COMMENT '凭证异常原因，仅管理员可见' AFTER verified_at;

CREATE TABLE IF NOT EXISTS project_settlement_note (
    id BIGINT NOT NULL AUTO_INCREMENT,
    settlement_id BIGINT NOT NULL,
    note TEXT NOT NULL,
    operator_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_settlement_note_settlement (settlement_id, created_at),
    CONSTRAINT fk_settlement_note_settlement FOREIGN KEY (settlement_id)
        REFERENCES project_settlement (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '结项公示追加说明';
