package com.just.critter.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T0.4 验收：03 §10 附录中的枚举必须全部有常量类，且每个值带中文 label。
 * 本测试先于实现编写，红→绿即完成。
 */
class DomainEnumCatalogTest {

    @Test
    @DisplayName("性别：三值，与 ck_animal_gender 一致")
    void gender_matches_check_constraint() {
        assertThat(Gender.values()).extracting(Enum::name)
                .containsExactly("MALE", "FEMALE", "UNKNOWN");
        assertThat(labels(Gender.values())).containsExactly("公", "母", "未知");
    }

    @Test
    @DisplayName("上报类型：五值，与 03 §10 一致")
    void report_type_catalog() {
        assertThat(ReportType.values()).extracting(Enum::name)
                .containsExactly("INJURED", "NEW_ARRIVAL", "ABANDONED", "DISTURB", "DEAD");
        assertThat(labels(ReportType.values()))
                .containsExactly("受伤", "新出现", "被遗弃", "扰民", "死亡");
    }

    @Test
    @DisplayName("捐赠状态：两态 + 异常")
    void donation_status_catalog() {
        assertThat(DonationStatus.values()).extracting(Enum::name)
                .containsExactly("PENDING_VERIFY", "VERIFIED", "ABNORMAL");
        assertThat(labels(DonationStatus.values())).containsExactly("待核对", "已入账", "凭证异常");
    }

    @Test
    @DisplayName("支出状态：四级复核")
    void expense_status_catalog() {
        assertThat(ExpenseStatus.values()).extracting(Enum::name)
                .containsExactly("PENDING_REVIEW", "FIRST_APPROVED", "APPROVED", "REJECTED");
        assertThat(labels(ExpenseStatus.values()))
                .containsExactly("待复核", "初审通过", "已复核", "已驳回");
    }

    @Test
    @DisplayName("出现数量量级：三档")
    void occurrence_scale_catalog() {
        assertThat(OccurrenceScale.values()).extracting(Enum::name)
                .containsExactly("FEW", "SMALL_HERD", "LARGE_HERD");
        assertThat(labels(OccurrenceScale.values())).containsExactly("零星", "小群", "大群");
    }

    @Test
    @DisplayName("健康记录类型：四类")
    void health_type_catalog() {
        assertThat(HealthType.values()).extracting(Enum::name)
                .containsExactly("VACCINE", "DEWORM", "WEIGHT", "HISTORY");
        assertThat(labels(HealthType.values())).containsExactly("疫苗", "驱虫", "体重", "病史");
    }

    private List<String> labels(Labeled[] values) {
        return Arrays.stream(values).map(Labeled::label).toList();
    }
}
