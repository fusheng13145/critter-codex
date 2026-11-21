package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.Expense;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 支出单表 CRUD；余额计算必须在事务内锁项目行，再按状态汇总。
 */
@Mapper
public interface ExpenseMapper extends BaseMapper<Expense> {

    @Select("SELECT * FROM expense WHERE expense_no = #{expenseNo} LIMIT 1")
    Expense selectByExpenseNo(@Param("expenseNo") String expenseNo);

    @Select("SELECT * FROM expense WHERE expense_no = #{expenseNo} FOR UPDATE")
    Expense selectByExpenseNoForUpdate(@Param("expenseNo") String expenseNo);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM expense "
            + "WHERE project_id = #{projectId} AND status = 'APPROVED'")
    int sumApprovedByProjectId(@Param("projectId") Long projectId);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM expense "
            + "WHERE project_id = #{projectId} AND status IN ('PENDING_REVIEW', 'FIRST_APPROVED')")
    int sumPendingByProjectId(@Param("projectId") Long projectId);

    @Select("SELECT COUNT(*) FROM expense "
            + "WHERE project_id = #{projectId} AND status IN ('PENDING_REVIEW', 'FIRST_APPROVED')")
    int countPendingByProjectId(@Param("projectId") Long projectId);
}
