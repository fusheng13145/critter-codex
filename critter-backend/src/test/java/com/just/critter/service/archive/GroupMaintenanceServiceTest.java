package com.just.critter.service.archive;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.just.critter.domain.archive.GroupCodeGenerator;
import com.just.critter.domain.enums.GroupStatus;
import com.just.critter.domain.model.AnimalGroup;
import com.just.critter.domain.model.Sighting;
import com.just.critter.mapper.AnimalGroupMapper;
import com.just.critter.mapper.BasePointMapper;
import com.just.critter.mapper.BaseSpeciesMapper;
import com.just.critter.mapper.SightingMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.log.AuditLogService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * P1 群体维护：合并同物种群体、拆分新编号、目击重新归属。
 */
@ExtendWith(MockitoExtension.class)
class GroupMaintenanceServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Sighting.class);
    }

    @Mock
    private AnimalGroupMapper groups;
    @Mock
    private BaseSpeciesMapper species;
    @Mock
    private BasePointMapper points;
    @Mock
    private SightingMapper sightings;
    @Mock
    private AuditLogService auditLog;

    @Test
    @DisplayName("合并：同物种副群体置 GONE，规模相加、点位并集、目击迁入主群体")
    void merge_same_species() {
        AnimalGroup main = group(1L, "GRP-2026-001", 3, 5, List.of(7L));
        AnimalGroup source = group(2L, "GRP-2026-002", 2, 4, List.of(8L));
        when(groups.selectByCode("GRP-2026-001")).thenReturn(main);
        when(groups.selectByCode("GRP-2026-002")).thenReturn(source);
        when(sightings.update(any(), any())).thenReturn(2);

        GroupMaintenanceService service = service();
        GroupMergeResult result = service.merge(
                new GroupMergeCommand("GRP-2026-001", List.of("GRP-2026-002"), "同一群分两次建档"), 9001L);

        assertThat(result.minSize()).isEqualTo(5);
        assertThat(result.maxSize()).isEqualTo(9);
        assertThat(result.pointIds()).containsExactlyInAnyOrder(7L, 8L);
        assertThat(result.movedSightings()).isEqualTo(2);
        assertThat(main.getMergedFrom()).containsExactly("GRP-2026-002");
        assertThat(source.getStatus()).isEqualTo(GroupStatus.GONE);
        assertThat(source.getMergedIntoCode()).isEqualTo("GRP-2026-001");
    }

    @Test
    @DisplayName("不同物种群体禁止合并，返回 30301")
    void merge_rejects_different_species() {
        AnimalGroup main = group(1L, "GRP-2026-001", 3, 5, List.of(7L));
        main.setSpeciesId(11L);
        AnimalGroup source = group(2L, "GRP-2026-002", 2, 4, List.of(8L));
        source.setSpeciesId(12L);
        when(groups.selectByCode("GRP-2026-001")).thenReturn(main);
        when(groups.selectByCode("GRP-2026-002")).thenReturn(source);

        GroupMaintenanceService service = service();
        assertThatThrownBy(() -> service.merge(
                new GroupMergeCommand("GRP-2026-001", List.of("GRP-2026-002"), "误合并测试"), 9001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30301));
    }

    @Test
    @DisplayName("拆分：生成新编号，源群体缩小规模，指定目击迁入新群体")
    void split_creates_new_group_and_moves_sightings() {
        AnimalGroup source = group(1L, "GRP-2026-001", 3, 5, List.of(7L));
        source.setFeatures("棕灰色，尾短");
        source.setSizeBasis("个体计数");
        when(groups.selectByCode("GRP-2026-001")).thenReturn(source);
        when(groups.insert(any(AnimalGroup.class))).thenAnswer(invocation -> {
            invocation.<AnimalGroup>getArgument(0).setId(3L);
            return 1;
        });
        Sighting sighting = new Sighting();
        sighting.setId(9L);
        sighting.setGroupId(1L);
        sighting.setAssignStatus("ASSIGNED");
        when(sightings.selectById(9L)).thenReturn(sighting);

        GroupMaintenanceService service = service();
        GroupSplitResult result = service.split(new GroupSplitCommand(
                "GRP-2026-001", null, 1, 2, "足迹计数", null,
                3, 4, "个体计数", List.of(9L), "夏季幼体单独活动"), 9001L);

        assertThat(result.newCode()).isEqualTo("GRP-2026-002");
        assertThat(result.sourceMinSize()).isEqualTo(3);
        assertThat(result.sourceMaxSize()).isEqualTo(4);
        assertThat(result.movedSightings()).isEqualTo(1);
        assertThat(source.getMinSize()).isEqualTo(3);
        assertThat(source.getMaxSize()).isEqualTo(4);
        assertThat(sighting.getGroupId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("目击归属修正：pending=true 清空群体，指定 groupCode 重新归属")
    void assign_sighting_pending_and_group() {
        Sighting sighting = new Sighting();
        sighting.setId(9L);
        sighting.setGroupId(1L);
        sighting.setPointId(7L);
        sighting.setAssignStatus("ASSIGNED");
        when(sightings.selectById(9L)).thenReturn(sighting);
        AnimalGroup target = group(2L, "GRP-2026-002", 3, 5, List.of(7L));
        when(groups.selectByCode("GRP-2026-002")).thenReturn(target);

        GroupMaintenanceService service = service();
        SightingView pending = service.assignSighting(9L,
                new SightingAssignmentCommand(null, null, true, "照片无法确认群体"), 9001L);
        assertThat(pending.groupCode()).isNull();
        assertThat(pending.assignStatus()).isEqualTo("UNASSIGNED");

        SightingView assigned = service.assignSighting(9L,
                new SightingAssignmentCommand(null, "GRP-2026-002", false, "核对后确认"), 9001L);
        assertThat(assigned.groupCode()).isEqualTo("GRP-2026-002");
        assertThat(assigned.assignStatus()).isEqualTo("ASSIGNED");
    }

    private GroupMaintenanceService service() {
        return new GroupMaintenanceService(groups, species, points, sightings,
                new GroupCodeGenerator((bizType, year) -> 2, CLOCK), auditLog, CLOCK);
    }

    private AnimalGroup group(long id, String code, int lowSize, int highSize, List<Long> pointIds) {
        AnimalGroup group = new AnimalGroup();
        group.setId(id);
        group.setGroupCode(code);
        group.setSpeciesId(11L);
        group.setFeatures("校园常见群体");
        group.setPointIds(pointIds);
        group.setMinSize(lowSize);
        group.setMaxSize(highSize);
        group.setSizeBasis("个体计数");
        group.setStatus(GroupStatus.ACTIVE);
        group.setLastSeenAt(LocalDateTime.parse("2026-11-20T10:00:00"));
        return group;
    }
}
