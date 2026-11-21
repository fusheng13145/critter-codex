-- 支出双签约束只能在“已通过”状态校验。
-- 大额支出登记时两名复核人尚未产生，原约束在 INSERT 阶段就会拒绝，
-- 导致 >500 元支出永远无法登记；改为仅 APPROVED 状态要求双签。
ALTER TABLE expense DROP CHECK ck_expense_double_sign;

ALTER TABLE expense
    ADD CONSTRAINT ck_expense_double_sign CHECK (
        status <> 'APPROVED'
        OR amount <= 50000
        OR (first_reviewer_id IS NOT NULL AND second_reviewer_id IS NOT NULL)
    );
