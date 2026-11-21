package com.just.critter.job.archive;

import com.just.critter.domain.enums.HealthType;
import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.HealthRecord;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.HealthRecordMapper;
import com.just.critter.message.repository.MessageRepository;
import com.just.critter.message.service.MessageWrite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T1.4 疫苗提醒投递：三天窗口内按记录 id 去重，只发给录入人。
 */
@ExtendWith(MockitoExtension.class)
class HealthReminderJobTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T02:31:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private HealthRecordMapper healthRecords;
    @Mock
    private AnimalMapper animals;
    @Mock
    private MessageRepository messages;

    @Test
    @DisplayName("三天内到期：给录入人发 HEALTH_REMINDER，biz_id 为健康记录 id")
    void scan_sends_reminder_to_operator() {
        HealthRecord record = record(51L, 42L, 1001L);
        when(healthRecords.selectList(any())).thenReturn(List.of(record));
        Animal animal = new Animal();
        animal.setId(42L);
        animal.setCode("CAT-2026-001");
        when(animals.selectById(42L)).thenReturn(animal);
        when(messages.exists("HEALTH_REMINDER", "51", 1001L)).thenReturn(false);
        when(messages.insert(any(MessageWrite.class))).thenReturn(1);

        HealthReminderResult result = new HealthReminderJob(
                healthRecords, animals, messages, CLOCK).scan();

        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        ArgumentCaptor<MessageWrite> captor = ArgumentCaptor.forClass(MessageWrite.class);
        verify(messages).insert(captor.capture());
        MessageWrite write = captor.getValue();
        assertThat(write.receiverId()).isEqualTo(1001L);
        assertThat(write.type()).isEqualTo("HEALTH_REMINDER");
        assertThat(write.bizId()).isEqualTo("51");
        assertThat(write.title()).contains("CAT-2026-001").contains("2026-11-22");
    }

    @Test
    @DisplayName("已投递过或录入人缺失：跳过且不重复发消息")
    void scan_skips_duplicate_and_missing_operator() {
        HealthRecord delivered = record(51L, 42L, 1001L);
        HealthRecord noOperator = record(52L, 42L, null);
        when(healthRecords.selectList(any())).thenReturn(List.of(delivered, noOperator));
        when(messages.exists("HEALTH_REMINDER", "51", 1001L)).thenReturn(true);

        HealthReminderResult result = new HealthReminderJob(
                healthRecords, animals, messages, CLOCK).scan();

        assertThat(result.candidates()).isEqualTo(2);
        assertThat(result.sent()).isZero();
        assertThat(result.skipped()).isEqualTo(2);
        verify(messages, never()).insert(any(MessageWrite.class));
    }

    private HealthRecord record(Long id, Long animalId, Long operatorId) {
        HealthRecord record = new HealthRecord();
        record.setId(id);
        record.setAnimalId(animalId);
        record.setOperatorId(operatorId);
        record.setType(HealthType.VACCINE);
        record.setNextDate(LocalDate.parse("2026-11-22"));
        return record;
    }
}
