package com.just.critter.service.archive;

import com.just.critter.domain.archive.GroupCodeGenerator;
import com.just.critter.domain.enums.GroupStatus;
import com.just.critter.domain.model.AnimalGroup;
import com.just.critter.domain.model.BasePoint;
import com.just.critter.domain.model.BaseSpecies;
import com.just.critter.domain.model.Sighting;
import com.just.critter.mapper.AnimalGroupMapper;
import com.just.critter.mapper.BasePointMapper;
import com.just.critter.mapper.BaseSpeciesMapper;
import com.just.critter.mapper.SightingMapper;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T1.5 验收：群体发号、规模区间 30504、点位与物种校验、目击挂靠与最近目击时间。
 */
@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);

    @Mock
    private AnimalGroupMapper groups;
    @Mock
    private BaseSpeciesMapper species;
    @Mock
    private BasePointMapper points;
    @Mock
    private SightingMapper sightings;

    private GroupService service;

    @BeforeEach
    void setUp() {
        GroupCodeGenerator generator = new GroupCodeGenerator((bizType, year) -> 1, CLOCK);
        service = new GroupService(groups, species, points, sightings, generator, CLOCK);
        lenient().when(species.selectById(1L)).thenReturn(speciesRow());
        lenient().when(points.selectBatchIds(anyCollection())).thenReturn(List.of(point(7L), point(8L)));
    }

    @Test
    @DisplayName("建档：发号 GRP-2026-001，默认活跃，点位集合与规模区间落库")
    void create_generates_group_code_and_persists() {
        when(groups.insert(any(AnimalGroup.class))).thenAnswer(invocation -> {
            invocation.<AnimalGroup>getArgument(0).setId(10L);
            return 1;
        });

        CreatedGroup created = service.create(new CreateGroupCommand(
                1L, "黄褐色，尾部蓬松", List.of(7L, 8L), 3, 5, "两次目击取中位数"), 1001L);

        assertThat(created.groupCode()).isEqualTo("GRP-2026-001");
        assertThat(created.status()).isEqualTo(GroupStatus.ACTIVE);

        ArgumentCaptor<AnimalGroup> captor = ArgumentCaptor.forClass(AnimalGroup.class);
        verify(groups).insert(captor.capture());
        AnimalGroup saved = captor.getValue();
        assertThat(saved.getSpeciesId()).isEqualTo(1L);
        assertThat(saved.getPointIds()).containsExactly(7L, 8L);
        assertThat(saved.getMinSize()).isEqualTo(3);
        assertThat(saved.getMaxSize()).isEqualTo(5);
        assertThat(saved.getSizeBasis()).isEqualTo("两次目击取中位数");
        assertThat(saved.getStatus()).isEqualTo(GroupStatus.ACTIVE);
        assertThat(saved.getCreatedBy()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("建档：规模区间非法 30504，且不查物种")
    void create_rejects_invalid_size_range() {
        assertThatThrownBy(() -> service.create(new CreateGroupCommand(
                1L, "黄褐色，尾部蓬松", List.of(7L), 6, 5, "目测"), 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30504));

        assertThatThrownBy(() -> service.create(new CreateGroupCommand(
                1L, "黄褐色，尾部蓬松", List.of(7L), 0, 5, "目测"), 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30504));

        verify(species, never()).selectById(any());
        verify(groups, never()).insert(any(AnimalGroup.class));
    }

    @Test
    @DisplayName("建档：物种或点位不存在 30410，空点位 30301")
    void create_rejects_unknown_species_or_point() {
        when(species.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.create(new CreateGroupCommand(
                99L, "黄褐色，尾部蓬松", List.of(7L), 1, 2, "目测"), 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30410));

        assertThatThrownBy(() -> service.create(new CreateGroupCommand(
                1L, "黄褐色，尾部蓬松", List.of(), 1, 2, "目测"), 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30301));

        when(points.selectBatchIds(anyCollection())).thenReturn(List.of(point(7L)));
        assertThatThrownBy(() -> service.create(new CreateGroupCommand(
                1L, "黄褐色，尾部蓬松", List.of(7L, 8L), 1, 2, "目测"), 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30410));
    }

    @Test
    @DisplayName("列表：按物种与状态筛选，返回规模区间与点位名")
    void search_returns_group_summary() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AnimalGroup> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(List.of(group()));
        page.setTotal(1);
        when(groups.selectPage(any(), any())).thenReturn(page);

        PageResult<GroupSummary> result = service.search(
                new GroupFilter("黄褐", 1L, GroupStatus.ACTIVE, 7L), new PageQuery(1, 20));

        assertThat(result.getList()).hasSize(1);
        GroupSummary row = result.getList().getFirst();
        assertThat(row.groupCode()).isEqualTo("GRP-2026-001");
        assertThat(row.species().commonName()).isEqualTo("黄鼬");
        assertThat(row.minSize()).isEqualTo(3);
        assertThat(row.maxSize()).isEqualTo(5);
        assertThat(row.points()).extracting(PointRef::name).containsExactly("东区绿化带", "西区草坪");
        assertThat(result.getPage().getTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("详情：编号不存在 30410")
    void detail_not_found() {
        when(groups.selectByCode("GRP-2026-999")).thenReturn(null);
        assertThatThrownBy(() -> service.detail("GRP-2026-999"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30410));
    }

    @Test
    @DisplayName("编辑：规模区间仍校验，点位集合可整体替换")
    void update_validates_range_and_replaces_points() {
        when(groups.selectByCode("GRP-2026-001")).thenReturn(group());
        when(groups.updateById(any(AnimalGroup.class))).thenReturn(1);
        when(points.selectBatchIds(anyCollection())).thenReturn(List.of(point(8L)));

        GroupDetail updated = service.update("GRP-2026-001", new UpdateGroupCommand(
                "毛色偏灰，尾部蓬松", List.of(8L), 2, 2, "合并后重新计数"), 1002L);

        assertThat(updated.minSize()).isEqualTo(2);
        assertThat(updated.maxSize()).isEqualTo(2);
        assertThat(updated.points()).extracting(PointRef::id).containsExactly(8L);

        ArgumentCaptor<AnimalGroup> captor = ArgumentCaptor.forClass(AnimalGroup.class);
        verify(groups).updateById(captor.capture());
        assertThat(captor.getValue().getFeatures()).isEqualTo("毛色偏灰，尾部蓬松");
    }

    @Test
    @DisplayName("编辑：新规模区间非法 30504，不写库")
    void update_rejects_invalid_range() {
        when(groups.selectByCode("GRP-2026-001")).thenReturn(group());

        assertThatThrownBy(() -> service.update("GRP-2026-001", new UpdateGroupCommand(
                null, null, 6, 4, null), 1002L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30504));

        verify(groups, never()).updateById(any(AnimalGroup.class));
    }

    @Test
    @DisplayName("目击：挂靠群体、标记已归属、刷新最近目击时间")
    void record_sighting_attaches_and_touches_last_seen() {
        when(groups.selectByCode("GRP-2026-001")).thenReturn(group());
        when(points.selectById(7L)).thenReturn(point(7L));
        when(sightings.insert(any(Sighting.class))).thenAnswer(invocation -> {
            invocation.<Sighting>getArgument(0).setId(100L);
            return 1;
        });
        when(groups.updateById(any(AnimalGroup.class))).thenReturn(1);

        CreatedSighting created = service.recordSighting("GRP-2026-001", new CreateSightingCommand(
                7L, OffsetDateTime.parse("2026-11-21T09:00:00+08:00"), 4, "沿围墙活动",
                List.of("F2026112100013")), 1003L);

        assertThat(created.sightingId()).isEqualTo(100L);

        ArgumentCaptor<Sighting> sighting = ArgumentCaptor.forClass(Sighting.class);
        verify(sightings).insert(sighting.capture());
        assertThat(sighting.getValue().getGroupId()).isEqualTo(10L);
        assertThat(sighting.getValue().getAssignStatus()).isEqualTo("ASSIGNED");
        assertThat(sighting.getValue().getRecorderId()).isEqualTo(1003L);
        assertThat(sighting.getValue().getFileIds()).containsExactly("F2026112100013");

        ArgumentCaptor<AnimalGroup> groupCaptor = ArgumentCaptor.forClass(AnimalGroup.class);
        verify(groups).updateById(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getLastSeenAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 21, 9, 0));
    }

    private BaseSpecies speciesRow() {
        BaseSpecies row = new BaseSpecies();
        row.setId(1L);
        row.setCommonName("黄鼬");
        row.setScientificName("Mustela sibirica");
        row.setCategory("哺乳纲");
        row.setProtectionLevel("待核");
        return row;
    }

    private BasePoint point(Long id) {
        BasePoint point = new BasePoint();
        point.setId(id);
        point.setPointName(id == 7L ? "东区绿化带" : "西区草坪");
        point.setArea("东区");
        point.setEnabled(true);
        return point;
    }

    private AnimalGroup group() {
        AnimalGroup group = new AnimalGroup();
        group.setId(10L);
        group.setGroupCode("GRP-2026-001");
        group.setSpeciesId(1L);
        group.setFeatures("黄褐色，尾部蓬松");
        group.setPointIds(List.of(7L, 8L));
        group.setMinSize(3);
        group.setMaxSize(5);
        group.setSizeBasis("两次目击取中位数");
        group.setStatus(GroupStatus.ACTIVE);
        group.setLastSeenAt(LocalDateTime.of(2026, 11, 20, 18, 0));
        group.setCreatedAt(LocalDateTime.of(2026, 11, 20, 9, 0));
        group.setUpdatedAt(LocalDateTime.of(2026, 11, 20, 9, 0));
        return group;
    }
}
