package com.just.critter.service.archive;

import com.just.critter.domain.enums.GroupStatus;
import com.just.critter.domain.model.AnimalGroup;
import com.just.critter.domain.model.BasePoint;
import com.just.critter.domain.model.PatrolRecord;
import com.just.critter.domain.model.Sighting;
import com.just.critter.mapper.AnimalGroupMapper;
import com.just.critter.mapper.BasePointMapper;
import com.just.critter.mapper.PatrolRecordMapper;
import com.just.critter.mapper.SightingMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.log.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 巡查记录：登记校验、转目击幂等、待归属与目标群体点位/最近目击刷新。
 */
@ExtendWith(MockitoExtension.class)
class PatrolServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);

    @Mock
    private PatrolRecordMapper patrols;
    @Mock
    private BasePointMapper points;
    @Mock
    private AnimalGroupMapper groups;
    @Mock
    private SightingMapper sightings;
    @Mock
    private AuditLogService auditLog;

    private PatrolService service;

    @BeforeEach
    void setUp() {
        service = new PatrolService(patrols, points, groups, sightings, auditLog, CLOCK);
        BasePoint point = new BasePoint();
        point.setId(7L);
        point.setPointName("东区宿舍七号楼");
        when(points.selectById(7L)).thenReturn(point);
    }

    @Test
    @DisplayName("登记巡查：落点位、时间、数量、异常说明与照片并写审计")
    void create_saves_record_and_audit() {
        when(patrols.insert(any(PatrolRecord.class))).thenAnswer(invocation -> {
            invocation.<PatrolRecord>getArgument(0).setId(12L);
            return 1;
        });

        PatrolView view = service.create(new CreatePatrolCommand(
                7L,
                OffsetDateTime.parse("2026-11-21T08:30:00+08:00"),
                3,
                true,
                "右耳有伤口",
                List.of("F1", "F2")), 9001L);

        assertThat(view.id()).isEqualTo(12L);
        assertThat(view.point().name()).isEqualTo("东区宿舍七号楼");
        assertThat(view.patrolAt()).isEqualTo(OffsetDateTime.parse("2026-11-21T08:30:00+08:00"));
        assertThat(view.countEstimate()).isEqualTo(3);
        assertThat(view.hasAnomaly()).isTrue();
        assertThat(view.anomalyNote()).isEqualTo("右耳有伤口");
        assertThat(view.fileIds()).containsExactly("F1", "F2");
        verify(auditLog).successAs(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("登记巡查：异常必须写说明，照片最多 9 张")
    void create_validates_anomaly_note_and_files() {
        assertCode(() -> service.create(new CreatePatrolCommand(
                7L, OffsetDateTime.parse("2026-11-21T08:30:00+08:00"), null, true, " ", List.of()), 9001L), 30301);

        List<String> tooMany = List.of("F1", "F2", "F3", "F4", "F5", "F6", "F7");
        assertCode(() -> service.create(new CreatePatrolCommand(
                7L, OffsetDateTime.parse("2026-11-21T08:30:00+08:00"), null, false, null, tooMany), 9001L), 30301);
        verify(patrols, never()).insert(any(PatrolRecord.class));
    }

    @Test
    @DisplayName("转目击：不指定群体时生成 UNASSIGNED，且同一条只能转一次")
    void convert_without_group_creates_unassigned_sighting() {
        PatrolRecord patrol = patrol(12L, null);
        when(patrols.selectById(12L)).thenReturn(patrol);
        when(sightings.insert(any(Sighting.class))).thenAnswer(invocation -> {
            invocation.<Sighting>getArgument(0).setId(88L);
            return 1;
        });

        SightingView view = service.convert(12L, new ConvertPatrolCommand(null), 9001L);

        assertThat(view.id()).isEqualTo(88L);
        assertThat(view.groupCode()).isNull();
        assertThat(view.assignStatus()).isEqualTo("UNASSIGNED");
        assertThat(view.point().id()).isEqualTo(7L);
        assertThat(patrol.getConvertedSightingId()).isEqualTo(88L);
        verify(patrols).updateById(patrol);

        patrol.setConvertedSightingId(88L);
        assertCode(() -> service.convert(12L, new ConvertPatrolCommand(null), 9001L), 30402);
        verify(sightings, times(1)).insert(any(Sighting.class));
    }

    @Test
    @DisplayName("转目击：指定群体时迁移点位并刷新最近目击时间")
    void convert_with_group_updates_points_and_last_seen() {
        PatrolRecord patrol = patrol(12L, null);
        patrol.setPatrolAt(LocalDateTime.parse("2026-11-21T08:30:00"));
        when(patrols.selectById(12L)).thenReturn(patrol);
        AnimalGroup group = group("GRP-2026-001", GroupStatus.ACTIVE, List.of(8L),
                LocalDateTime.parse("2026-11-20T09:00:00"));
        when(groups.selectByCode("GRP-2026-001")).thenReturn(group);
        when(sightings.insert(any(Sighting.class))).thenAnswer(invocation -> {
            invocation.<Sighting>getArgument(0).setId(88L);
            return 1;
        });

        SightingView view = service.convert(12L, new ConvertPatrolCommand("GRP-2026-001"), 9001L);

        assertThat(view.groupCode()).isEqualTo("GRP-2026-001");
        assertThat(view.assignStatus()).isEqualTo("ASSIGNED");
        assertThat(group.getPointIds()).containsExactly(8L, 7L);
        assertThat(group.getLastSeenAt()).isEqualTo(LocalDateTime.parse("2026-11-21T08:30:00"));
        verify(groups).updateById(group);
    }

    @Test
    @DisplayName("转目击：已消失/已合并群体返回 30505")
    void convert_rejects_gone_group() {
        PatrolRecord patrol = patrol(12L, null);
        when(patrols.selectById(12L)).thenReturn(patrol);
        AnimalGroup gone = group("GRP-2026-001", GroupStatus.GONE, List.of(8L), null);
        when(groups.selectByCode("GRP-2026-001")).thenReturn(gone);

        assertCode(() -> service.convert(12L, new ConvertPatrolCommand("GRP-2026-001"), 9001L), 30505);
        verify(sightings, never()).insert(any(Sighting.class));
    }

    private PatrolRecord patrol(Long id, Long convertedSightingId) {
        PatrolRecord record = new PatrolRecord();
        record.setId(id);
        record.setPointId(7L);
        record.setPatrolAt(LocalDateTime.parse("2026-11-21T08:30:00"));
        record.setCountEstimate(3);
        record.setHasAnomaly(true);
        record.setAnomalyNote("右耳有伤口");
        record.setFileIds(List.of("F1"));
        record.setRecorderId(9001L);
        record.setConvertedSightingId(convertedSightingId);
        return record;
    }

    private AnimalGroup group(String code, GroupStatus status, List<Long> pointIds, LocalDateTime lastSeen) {
        AnimalGroup group = new AnimalGroup();
        group.setId(1L);
        group.setGroupCode(code);
        group.setStatus(status);
        group.setPointIds(pointIds);
        group.setLastSeenAt(lastSeen);
        return group;
    }

    private void assertCode(ThrowingRunnable action, int expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(expected));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
