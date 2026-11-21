package com.just.critter.service.archive;

import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.AnimalAlias;
import com.just.critter.domain.model.AnimalChangeLog;
import com.just.critter.domain.model.HealthRecord;
import com.just.critter.mapper.AdoptionMapper;
import com.just.critter.mapper.AnimalAliasMapper;
import com.just.critter.mapper.AnimalChangeLogMapper;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.HealthRecordMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.log.AuditLogService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1 档案合并验收：主档保留、副档打 mergedIntoId、副编号进别名表；
 * 有 ACTIVE/PENDING_PAYMENT 认养关系时拒绝并回 30403。
 */
@ExtendWith(MockitoExtension.class)
class AnimalMergeServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, HealthRecord.class);
        TableInfoHelper.initTableInfo(assistant, AnimalChangeLog.class);
    }

    @Mock
    private AnimalMapper animals;
    @Mock
    private AnimalAliasMapper aliases;
    @Mock
    private AdoptionMapper adoptions;
    @Mock
    private HealthRecordMapper healthRecords;
    @Mock
    private AnimalChangeLogMapper changeLogs;
    @Mock
    private AuditLogService auditLog;

    @Test
    @DisplayName("合并成功：副档不删除、写别名、健康记录迁到主档")
    void merge_keeps_source_and_alias() {
        Animal main = animal(1L, "CAT-2026-001");
        Animal source = animal(2L, "CAT-2026-002");
        when(animals.selectByCode("CAT-2026-001")).thenReturn(main);
        when(animals.selectByCode("CAT-2026-002")).thenReturn(source);
        when(adoptions.countBlockingByAnimalId(anyLong())).thenReturn(0L);
        when(aliases.selectByAliasCode("CAT-2026-002")).thenReturn(null);
        when(healthRecords.update(any(), any())).thenReturn(3);
        when(changeLogs.update(any(), any())).thenReturn(1);

        AnimalMergeService service = new AnimalMergeService(animals, aliases, adoptions, healthRecords,
                changeLogs, auditLog, CLOCK);
        AnimalMergeResult result = service.merge(
                new AnimalMergeCommand("CAT-2026-001", List.of("CAT-2026-002"), "耳标重复，确认同一只"), 9001L);

        assertThat(result.mainCode()).isEqualTo("CAT-2026-001");
        assertThat(result.mergedCodes()).containsExactly("CAT-2026-002");
        assertThat(result.movedHealthRecords()).isEqualTo(3);
        assertThat(source.getMergedIntoId()).isEqualTo(1L);
        assertThat(source.getDeleted()).isZero();

        ArgumentCaptor<AnimalAlias> aliasCaptor = ArgumentCaptor.forClass(AnimalAlias.class);
        verify(aliases).insert(aliasCaptor.capture());
        assertThat(aliasCaptor.getValue().getAliasCode()).isEqualTo("CAT-2026-002");
        assertThat(aliasCaptor.getValue().getAnimalId()).isEqualTo(1L);

        ArgumentCaptor<AnimalChangeLog> logCaptor = ArgumentCaptor.forClass(AnimalChangeLog.class);
        verify(changeLogs, org.mockito.Mockito.atLeast(2)).insert(logCaptor.capture());
        assertThat(logCaptor.getAllValues()).extracting(AnimalChangeLog::getField)
                .contains("mergedInto", "mergedFrom");
    }

    @Test
    @DisplayName("存在生效或待缴费认养关系时禁止合并，返回 30403")
    void merge_blocks_active_or_pending_adoption() {
        Animal main = animal(1L, "CAT-2026-001");
        Animal source = animal(2L, "CAT-2026-002");
        when(animals.selectByCode("CAT-2026-001")).thenReturn(main);
        when(animals.selectByCode("CAT-2026-002")).thenReturn(source);
        when(adoptions.countBlockingByAnimalId(1L)).thenReturn(0L);
        when(adoptions.countBlockingByAnimalId(2L)).thenReturn(1L);

        AnimalMergeService service = new AnimalMergeService(animals, aliases, adoptions, healthRecords,
                changeLogs, auditLog, CLOCK);

        assertThatThrownBy(() -> service.merge(
                new AnimalMergeCommand("CAT-2026-001", List.of("CAT-2026-002"), "确认重复档案"), 9001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30403));
    }

    private Animal animal(long id, String code) {
        Animal animal = new Animal();
        animal.setId(id);
        animal.setCode(code);
        animal.setDeleted(0);
        return animal;
    }
}
