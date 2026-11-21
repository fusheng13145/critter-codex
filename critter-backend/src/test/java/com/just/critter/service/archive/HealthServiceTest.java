package com.just.critter.service.archive;

import com.just.critter.domain.archive.AnimalCategory;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.domain.enums.Gender;
import com.just.critter.domain.enums.HealthType;
import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.HealthRecord;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.HealthRecordMapper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T1.4 验收：健康记录落库、日期不得早于建档日、疫苗到期前三天提醒、编号不存在 30410。
 */
@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);

    @Mock
    private HealthRecordMapper healthRecords;
    @Mock
    private AnimalMapper animals;

    private HealthService service;

    @BeforeEach
    void setUp() {
        service = new HealthService(healthRecords, animals, CLOCK);
    }

    @Test
    @DisplayName("新增：记录挂到动物、保存操作人与计划日期，返回记录 id")
    void create_persists_record_and_returns_id() {
        when(animals.selectByCode("CAT-2026-001")).thenReturn(existing());
        when(healthRecords.insert(any(HealthRecord.class))).thenAnswer(invocation -> {
            invocation.<HealthRecord>getArgument(0).setId(9L);
            return 1;
        });

        CreatedHealthRecord created = service.create("CAT-2026-001", command(
                HealthType.VACCINE, "2026-11-21T09:00:00+08:00", "猫三联第一针", LocalDate.of(2026, 11, 24)), 1001L);

        assertThat(created.healthRecordId()).isEqualTo(9L);

        ArgumentCaptor<HealthRecord> captor = ArgumentCaptor.forClass(HealthRecord.class);
        verify(healthRecords).insert(captor.capture());
        HealthRecord saved = captor.getValue();
        assertThat(saved.getAnimalId()).isEqualTo(1L);
        assertThat(saved.getType()).isEqualTo(HealthType.VACCINE);
        assertThat(saved.getRecordedAt()).isEqualTo(LocalDateTime.of(2026, 11, 21, 9, 0));
        assertThat(saved.getResult()).isEqualTo("猫三联第一针");
        assertThat(saved.getNextDate()).isEqualTo(LocalDate.of(2026, 11, 24));
        assertThat(saved.getOperatorId()).isEqualTo(1001L);
        assertThat(saved.getCreatedAt()).isEqualTo(LocalDateTime.now(CLOCK));
    }

    @Test
    @DisplayName("新增：记录日期早于建档日拒绝 30301，且不写库")
    void create_rejects_record_before_animal_created_date() {
        when(animals.selectByCode("CAT-2026-001")).thenReturn(existing());

        assertThatThrownBy(() -> service.create("CAT-2026-001", command(
                HealthType.HISTORY, "2026-11-20T20:00:00+08:00", "入校前旧伤", null), 1001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30301));

        verify(healthRecords, never()).insert(any(HealthRecord.class));
    }

    @Test
    @DisplayName("新增：下次计划日期早于记录日期拒绝 30301")
    void create_rejects_next_date_before_recorded_date() {
        assertThatThrownBy(() -> service.create("CAT-2026-001", command(
                HealthType.VACCINE, "2026-11-21T09:00:00+08:00", "猫三联第一针", LocalDate.of(2026, 11, 20)), 1001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30301));
    }

    @Test
    @DisplayName("新增：类型、记录时间、结果为空均拒绝 30301")
    void create_requires_type_time_and_result() {
        assertThatThrownBy(() -> service.create("CAT-2026-001", new AddHealthRecordCommand(
                null, OffsetDateTime.parse("2026-11-21T09:00:00+08:00"), "猫三联第一针", null), 1001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30301));

        assertThatThrownBy(() -> service.create("CAT-2026-001", new AddHealthRecordCommand(
                HealthType.VACCINE, null, "猫三联第一针", null), 1001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30301));

        assertThatThrownBy(() -> service.create("CAT-2026-001", new AddHealthRecordCommand(
                HealthType.VACCINE, OffsetDateTime.parse("2026-11-21T09:00:00+08:00"), "  ", null), 1001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30301));
    }

    @Test
    @DisplayName("查询：按记录时间倒序，疫苗三天内到期标记提醒，第四天不提醒")
    void list_marks_vaccine_reminder_within_three_days() {
        when(animals.selectByCode("CAT-2026-001")).thenReturn(existing());
        when(healthRecords.selectList(any())).thenReturn(List.of(
                record(3L, HealthType.WEIGHT, LocalDateTime.of(2026, 11, 21, 10, 0), "4.2kg", null),
                record(2L, HealthType.VACCINE, LocalDateTime.of(2026, 11, 21, 9, 0), "猫三联第一针",
                        LocalDate.of(2026, 11, 25)),
                record(1L, HealthType.VACCINE, LocalDateTime.of(2026, 11, 20, 9, 0), "狂犬疫苗",
                        LocalDate.of(2026, 11, 24))));

        List<HealthRecordView> views = service.list("CAT-2026-001");

        assertThat(views).extracting(HealthRecordView::id).containsExactly(3L, 2L, 1L);
        assertThat(views.get(0).reminderDue()).isFalse();
        assertThat(views.get(1).reminderDue()).isFalse();
        assertThat(views.get(1).daysUntilNext()).isEqualTo(4);
        assertThat(views.get(2).reminderDue()).isTrue();
        assertThat(views.get(2).daysUntilNext()).isEqualTo(3);
        assertThat(views.get(2).typeLabel()).isEqualTo("疫苗");
    }

    @Test
    @DisplayName("查询：编号不存在 30410；空记录返回空数组")
    void list_handles_missing_animal_and_empty_records() {
        when(animals.selectByCode("CAT-2026-999")).thenReturn(null);
        assertThatThrownBy(() -> service.list("CAT-2026-999"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30410));

        when(animals.selectByCode("CAT-2026-001")).thenReturn(existing());
        when(healthRecords.selectList(any())).thenReturn(List.of());
        assertThat(service.list("CAT-2026-001")).isEmpty();
    }

    private AddHealthRecordCommand command(HealthType type, String recordedAt, String result, LocalDate nextDate) {
        return new AddHealthRecordCommand(type, OffsetDateTime.parse(recordedAt), result, nextDate);
    }

    private Animal existing() {
        Animal animal = new Animal();
        animal.setId(1L);
        animal.setCode("CAT-2026-001");
        animal.setCategory(AnimalCategory.CAT);
        animal.setGender(Gender.FEMALE);
        animal.setStatus(AnimalStatus.ON_CAMPUS);
        animal.setCreatedAt(LocalDateTime.of(2026, 11, 21, 10, 31));
        animal.setDeleted(0);
        return animal;
    }

    private HealthRecord record(Long id, HealthType type, LocalDateTime recordedAt, String result, LocalDate nextDate) {
        HealthRecord row = new HealthRecord();
        row.setId(id);
        row.setAnimalId(1L);
        row.setType(type);
        row.setRecordedAt(recordedAt);
        row.setResult(result);
        row.setNextDate(nextDate);
        row.setOperatorId(1001L);
        row.setCreatedAt(recordedAt);
        return row;
    }
}
