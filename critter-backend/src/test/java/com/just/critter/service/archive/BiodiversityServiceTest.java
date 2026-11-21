package com.just.critter.service.archive;

import com.just.critter.domain.enums.OccurrenceScale;
import com.just.critter.domain.enums.Season;
import com.just.critter.domain.model.BasePoint;
import com.just.critter.domain.model.BaseSpecies;
import com.just.critter.domain.model.Occurrence;
import com.just.critter.mapper.BasePointMapper;
import com.just.critter.mapper.BaseSpeciesMapper;
import com.just.critter.mapper.OccurrenceMapper;
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

import java.math.BigDecimal;
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
 * T1.5 验收：物种/点位只读、出现记录枚举与照片必填、季节查询映射。
 */
@ExtendWith(MockitoExtension.class)
class BiodiversityServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);

    @Mock
    private BaseSpeciesMapper species;
    @Mock
    private BasePointMapper points;
    @Mock
    private OccurrenceMapper occurrences;

    private BiodiversityService service;

    @BeforeEach
    void setUp() {
        service = new BiodiversityService(species, points, occurrences, CLOCK);
        lenient().when(species.selectById(1L)).thenReturn(speciesRow());
        lenient().when(points.selectById(7L)).thenReturn(point());
        lenient().when(points.selectBatchIds(anyCollection())).thenReturn(List.of(point()));
    }

    @Test
    @DisplayName("物种名录：按类别与关键字返回常见名、学名、保护级别")
    void list_species_maps_catalog() {
        when(species.selectList(any())).thenReturn(List.of(speciesRow()));

        List<SpeciesView> result = service.listSpecies(new SpeciesFilter("黄鼬", "哺乳纲"));

        assertThat(result).hasSize(1);
        SpeciesView row = result.getFirst();
        assertThat(row.commonName()).isEqualTo("黄鼬");
        assertThat(row.scientificName()).isEqualTo("Mustela sibirica");
        assertThat(row.category()).isEqualTo("哺乳纲");
        assertThat(row.protectionLevel()).isEqualTo("待核");
    }

    @Test
    @DisplayName("点位字典：返回坐标与启用状态")
    void list_points_maps_coordinates() {
        when(points.selectList(any())).thenReturn(List.of(point()));

        List<PointView> result = service.listPoints();

        assertThat(result).hasSize(1);
        PointView row = result.getFirst();
        assertThat(row.id()).isEqualTo(7L);
        assertThat(row.name()).isEqualTo("东区绿化带");
        assertThat(row.x()).isEqualByComparingTo("63.50");
        assertThat(row.enabled()).isTrue();
    }

    @Test
    @DisplayName("出现记录：物种、点位、照片、数量枚举齐全才落库")
    void create_occurrence_persists() {
        when(occurrences.insert(any(Occurrence.class))).thenAnswer(invocation -> {
            invocation.<Occurrence>getArgument(0).setId(55L);
            return 1;
        });

        CreatedOccurrence created = service.createOccurrence(new CreateOccurrenceCommand(
                1L, 7L, OffsetDateTime.parse("2026-11-21T08:30:00+08:00"),
                OccurrenceScale.SMALL_HERD, "灌木丛", List.of("F2026112100013")), 1001L);

        assertThat(created.occurrenceId()).isEqualTo(55L);

        ArgumentCaptor<Occurrence> captor = ArgumentCaptor.forClass(Occurrence.class);
        verify(occurrences).insert(captor.capture());
        Occurrence saved = captor.getValue();
        assertThat(saved.getSpeciesId()).isEqualTo(1L);
        assertThat(saved.getPointId()).isEqualTo(7L);
        assertThat(saved.getScale()).isEqualTo(OccurrenceScale.SMALL_HERD);
        assertThat(saved.getFileIds()).containsExactly("F2026112100013");
        assertThat(saved.getRecorderId()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("出现记录：照片为空、时间空、物种/点位不存在分别拒绝")
    void create_occurrence_validation() {
        assertThatThrownBy(() -> service.createOccurrence(new CreateOccurrenceCommand(
                1L, 7L, OffsetDateTime.parse("2026-11-21T08:30:00+08:00"),
                OccurrenceScale.FEW, "灌木丛", List.of()), 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30301));

        assertThatThrownBy(() -> service.createOccurrence(new CreateOccurrenceCommand(
                1L, 7L, null, OccurrenceScale.FEW, "灌木丛", List.of("F1")), 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30301));

        when(species.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.createOccurrence(new CreateOccurrenceCommand(
                99L, 7L, OffsetDateTime.parse("2026-11-21T08:30:00+08:00"),
                OccurrenceScale.FEW, "灌木丛", List.of("F1")), 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30410));

        when(points.selectById(8L)).thenReturn(null);
        assertThatThrownBy(() -> service.createOccurrence(new CreateOccurrenceCommand(
                1L, 8L, OffsetDateTime.parse("2026-11-21T08:30:00+08:00"),
                OccurrenceScale.FEW, "灌木丛", List.of("F1")), 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30410));

        verify(occurrences, never()).insert(any(Occurrence.class));
    }

    @Test
    @DisplayName("出现记录列表：返回量级中文名、点位与物种名，分页壳保留")
    void search_occurrences_maps_labels() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Occurrence> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        Occurrence row = occurrence();
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(occurrences.selectPage(any(), any())).thenReturn(page);

        PageResult<OccurrenceView> result = service.searchOccurrences(
                new OccurrenceFilter(1L, 7L, Season.SPRING, null), new PageQuery(1, 20));

        assertThat(result.getList()).hasSize(1);
        OccurrenceView view = result.getList().getFirst();
        assertThat(view.scale()).isEqualTo(OccurrenceScale.SMALL_HERD);
        assertThat(view.scaleLabel()).isEqualTo("小群");
        assertThat(view.speciesName()).isEqualTo("黄鼬");
        assertThat(view.point().name()).isEqualTo("东区绿化带");
        assertThat(result.getPage().getTotal()).isEqualTo(1);
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

    private BasePoint point() {
        BasePoint point = new BasePoint();
        point.setId(7L);
        point.setPointName("东区绿化带");
        point.setArea("东区");
        point.setX(new BigDecimal("63.50"));
        point.setY(new BigDecimal("41.20"));
        point.setEnabled(true);
        return point;
    }

    private Occurrence occurrence() {
        Occurrence row = new Occurrence();
        row.setId(55L);
        row.setSpeciesId(1L);
        row.setPointId(7L);
        row.setOccurredAt(LocalDateTime.of(2026, 4, 18, 8, 30));
        row.setScale(OccurrenceScale.SMALL_HERD);
        row.setHabitat("灌木丛");
        row.setFileIds(List.of("F1"));
        row.setRecorderId(1001L);
        row.setCreatedAt(LocalDateTime.of(2026, 4, 18, 9, 0));
        return row;
    }
}
