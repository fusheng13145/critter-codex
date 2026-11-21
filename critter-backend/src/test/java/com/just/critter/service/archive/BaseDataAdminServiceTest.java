package com.just.critter.service.archive;

import com.just.critter.domain.model.BaseSpecies;
import com.just.critter.mapper.BasePointMapper;
import com.just.critter.mapper.BaseSpeciesMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.log.AuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 基础数据维护：点位停用不删除、坐标 0–100、名称/俗名唯一。
 */
@ExtendWith(MockitoExtension.class)
class BaseDataAdminServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T02:31:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private BasePointMapper points;
    @Mock
    private BaseSpeciesMapper species;
    @Mock
    private AuditLogService auditLog;

    @Test
    @DisplayName("新增点位：enabled=false 也落库，坐标与区域正常保存")
    void create_point_can_be_disabled() {
        when(points.selectCount(any())).thenReturn(0L);
        when(points.insert(any(com.just.critter.domain.model.BasePoint.class))).thenAnswer(invocation -> {
            invocation.<com.just.critter.domain.model.BasePoint>getArgument(0).setId(7L);
            return 1;
        });

        PointView view = service().createPoint(new SavePointCommand("北区实验楼后草坪", "北区",
                new BigDecimal("31.50"), new BigDecimal("62.25"), false, "临时封闭"), 9001L);

        assertThat(view.name()).isEqualTo("北区实验楼后草坪");
        assertThat(view.enabled()).isFalse();
        assertThat(view.x()).isEqualByComparingTo("31.50");
        assertThat(view.y()).isEqualByComparingTo("62.25");
    }

    @Test
    @DisplayName("点位：重名 30411，坐标越界 30301")
    void create_point_validates_unique_and_coordinate() {
        when(points.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service().createPoint(new SavePointCommand("东区宿舍七号楼", null,
                null, null, null, null), 9001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30411));

        when(points.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service().createPoint(new SavePointCommand("越界点位", null,
                new BigDecimal("100.01"), new BigDecimal("50"), null, null), 9001L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0–100");
    }

    @Test
    @DisplayName("新增物种：俗名唯一，学名与保护级别保存")
    void create_species_saves_fields() {
        when(species.selectCount(any())).thenReturn(0L);
        when(species.insert(any(BaseSpecies.class))).thenAnswer(invocation -> {
            invocation.<BaseSpecies>getArgument(0).setId(11L);
            return 1;
        });

        SpeciesView view = service().createSpecies(new SaveSpeciesCommand("白鹭", "Egretta garzetta",
                "鸟类", "国家二级", "湿地常见"), 9001L);

        assertThat(view.commonName()).isEqualTo("白鹭");
        assertThat(view.scientificName()).isEqualTo("Egretta garzetta");
        assertThat(view.protectionLevel()).isEqualTo("国家二级");
    }

    private BaseDataAdminService service() {
        return new BaseDataAdminService(points, species, auditLog, CLOCK);
    }
}
