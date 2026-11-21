package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.Donation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 捐赠单表 CRUD。资金流水禁止删除，只允许状态修正与冲正。
 */
@Mapper
public interface DonationMapper extends BaseMapper<Donation> {

    @Select("SELECT * FROM donation WHERE donation_no = #{donationNo} LIMIT 1")
    Donation selectByDonationNo(@Param("donationNo") String donationNo);

    @Select("SELECT * FROM donation WHERE donation_no = #{donationNo} FOR UPDATE")
    Donation selectByDonationNoForUpdate(@Param("donationNo") String donationNo);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM donation "
            + "WHERE project_id = #{projectId} AND status = 'VERIFIED'")
    int sumVerifiedByProjectId(@Param("projectId") Long projectId);
}
