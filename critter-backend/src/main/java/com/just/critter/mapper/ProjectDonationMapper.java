package com.just.critter.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 项目进度所需的只读捐赠查询。只读 donation，不写入；
 * donor_display 已在 C 的登记接口按匿名规则落库，公开页直接展示。
 */
@Mapper
public interface ProjectDonationMapper {

    @Select("""
            <script>
            SELECT project_id AS projectId, COUNT(*) AS donationCount
            FROM donation
            WHERE status = 'VERIFIED' AND project_id IN
            <foreach collection="projectIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY project_id
            </script>
            """)
    List<Map<String, Object>> countVerifiedByProjectIds(@Param("projectIds") Collection<Long> projectIds);

    @Select("""
            SELECT donation_no AS donationNo, donor_display AS donorDisplay,
                   amount AS amount, message AS message, verified_at AS verifiedAt
            FROM donation
            WHERE project_id = #{projectId} AND status = 'VERIFIED'
            ORDER BY verified_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> recentVerified(@Param("projectId") Long projectId, @Param("limit") int limit);
}
