-- 留言审核状态：V1 建表时没有 status，P1 留言需要区分公开与待审。
ALTER TABLE comment
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE' AFTER content;

ALTER TABLE comment
    ADD KEY idx_comment_status (status, created_at);
