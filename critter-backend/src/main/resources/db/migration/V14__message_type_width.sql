-- 四类消息类型中最长的 DONATION_PROGRESS 为 17 字符，V1 的 VARCHAR(16) 会截断。
ALTER TABLE message
    MODIFY COLUMN type VARCHAR(24) NOT NULL COMMENT '消息类型：认养到期/募捐进度/工单进展/审批结果';
