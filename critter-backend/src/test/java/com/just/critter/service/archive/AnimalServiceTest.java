package com.just.critter.service.archive;

import com.just.critter.domain.archive.AnimalCategory;
import com.just.critter.domain.archive.AnimalCodeGenerator;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.domain.enums.Gender;
import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.AnimalChangeLog;
import com.just.critter.domain.model.BasePoint;
import com.just.critter.mapper.AnimalAliasMapper;
import com.just.critter.mapper.AnimalChangeLogMapper;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.BasePointMapper;
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
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T1.2 验收：建档发号、前端禁传编号、编辑必填原因与字段级留痕、详情按角色裁剪。
 */
@ExtendWith(MockitoExtension.class)
class AnimalServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);

    @Mock
    private AnimalMapper animalMapper;
    @Mock
    private AnimalAliasMapper aliasMapper;
    @Mock
    private BasePointMapper pointMapper;
    @Mock
    private AnimalChangeLogMapper changeLogMapper;

    private AnimalService service;

    @BeforeEach
    void setUp() {
        AnimalCodeGenerator generator = new AnimalCodeGenerator((bizType, year) -> 1, CLOCK);
        service = new AnimalService(animalMapper, aliasMapper, pointMapper, changeLogMapper, generator, CLOCK);
        BasePoint point = new BasePoint();
        point.setId(7L);
        point.setPointName("东区宿舍七号楼");
        lenient().when(pointMapper.selectById(7L)).thenReturn(point);
    }

    @Test
    @DisplayName("建档：服务端发号 CAT-2026-001，默认在校园、未绝育、未认养")
    void create_generates_code_and_defaults() {
        when(animalMapper.insert(any(Animal.class))).thenAnswer(invocation -> {
            invocation.<Animal>getArgument(0).setId(1L);
            return 1;
        });

        CreatedAnimal created = service.create(command(null), 1001L);

        assertThat(created.code()).isEqualTo("CAT-2026-001");
        assertThat(created.status()).isEqualTo(AnimalStatus.ON_CAMPUS);

        ArgumentCaptor<Animal> captor = ArgumentCaptor.forClass(Animal.class);
        verify(animalMapper).insert(captor.capture());
        Animal saved = captor.getValue();
        assertThat(saved.getCategory()).isEqualTo(AnimalCategory.CAT);
        assertThat(saved.getStatus()).isEqualTo(AnimalStatus.ON_CAMPUS);
        assertThat(saved.getNeutered()).isFalse();
        assertThat(saved.getAdopted()).isFalse();
        assertThat(saved.getDeleted()).isZero();
        assertThat(saved.getCoverFileId()).isEqualTo("F2026112100013");
        assertThat(saved.getInternalNote()).isEqualTo("初步体检未见外伤");
        assertThat(saved.getCreatedBy()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("建档：前端传 code 直接拒绝 30411")
    void create_rejects_client_supplied_code() {
        assertThatThrownBy(() -> service.create(command("CAT-2026-001"), 1001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30411));

        verify(animalMapper, never()).insert(any(Animal.class));
    }

    @Test
    @DisplayName("建档：识别特征少于 5 字、照片为 0 或超过 6 张均拒绝")
    void create_validates_features_and_photos() {
        CreateAnimalCommand shortFeatures = new CreateAnimalCommand(
                AnimalCategory.CAT, Gender.FEMALE, "三花", 2, "短", 7L,
                List.of("F1"), null, null);
        assertThatThrownBy(() -> service.create(shortFeatures, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30301));

        CreateAnimalCommand noPhoto = new CreateAnimalCommand(
                AnimalCategory.CAT, Gender.FEMALE, "三花", 2, "右后腿跛行", 7L,
                List.of(), null, null);
        assertThatThrownBy(() -> service.create(noPhoto, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30301));

        CreateAnimalCommand tooMany = new CreateAnimalCommand(
                AnimalCategory.CAT, Gender.FEMALE, "三花", 2, "右后腿跛行", 7L,
                List.of("F1", "F2", "F3", "F4", "F5", "F6", "F7"), null, null);
        assertThatThrownBy(() -> service.create(tooMany, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30302));
    }

    @Test
    @DisplayName("编辑：未填原因（少于 5 字）拒绝 30401")
    void update_requires_reason() {
        UpdateAnimalCommand noReason = new UpdateAnimalCommand(
                null, null, "橘白", null, null, null, null, "改色");
        assertThatThrownBy(() -> service.update("CAT-2026-001", noReason, 1001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30401));

        verify(changeLogMapper, never()).insert(any(AnimalChangeLog.class));
    }

    @Test
    @DisplayName("编辑：每个变化字段写一条前后值日志，未变化不写")
    void update_writes_field_level_logs() {
        when(animalMapper.selectByCode("CAT-2026-001")).thenReturn(existing());
        when(animalMapper.updateById(any(Animal.class))).thenReturn(1);

        UpdateAnimalCommand command = new UpdateAnimalCommand(
                "三花-新", null, "橘白", 3, "右后腿跛行，左耳缺口更明显", null, null,
                "花色记录有误，现场重新核对");

        service.update("CAT-2026-001", command, 1001L);

        ArgumentCaptor<AnimalChangeLog> captor = ArgumentCaptor.forClass(AnimalChangeLog.class);
        verify(changeLogMapper, times(4)).insert(captor.capture());
        List<AnimalChangeLog> logs = captor.getAllValues();

        assertThat(logs).extracting(AnimalChangeLog::getField)
                .containsExactlyInAnyOrder("color", "estimatedAge", "features", "name");
        assertThat(logs).allSatisfy(log -> {
            assertThat(log.getReason()).isEqualTo("花色记录有误，现场重新核对");
            assertThat(log.getOperatorId()).isEqualTo(1001L);
            assertThat(log.getAnimalId()).isEqualTo(1L);
        });
        AnimalChangeLog color = logs.stream().filter(l -> "color".equals(l.getField())).findFirst().orElseThrow();
        assertThat(color.getBeforeValue()).isEqualTo("三花");
        assertThat(color.getAfterValue()).isEqualTo("橘白");
    }

    @Test
    @DisplayName("编辑：字段没有实际变化时不产生日志")
    void update_without_changes_writes_nothing() {
        when(animalMapper.selectByCode("CAT-2026-001")).thenReturn(existing());

        UpdateAnimalCommand same = new UpdateAnimalCommand(
                "三花", null, "三花", 2, "右后腿跛行，左耳有缺口", null, null,
                "复核后确认无变化");

        service.update("CAT-2026-001", same, 1001L);

        verify(changeLogMapper, never()).insert(any(AnimalChangeLog.class));
    }

    @Test
    @DisplayName("详情：公开视图不返回内部备注，管理员视图才返回")
    void detail_hides_internal_note_for_public() {
        when(animalMapper.selectByCode("CAT-2026-001")).thenReturn(existing());

        AnimalDetail publicView = service.detail("CAT-2026-001", false);
        assertThat(publicView.internalNote()).isNull();
        assertThat(publicView.point().name()).isEqualTo("东区宿舍七号楼");
        assertThat(publicView.code()).isEqualTo("CAT-2026-001");

        AnimalDetail adminView = service.detail("CAT-2026-001", true);
        assertThat(adminView.internalNote()).isEqualTo("仅管理员可见：疑似旧伤");
    }

    @Test
    @DisplayName("详情：编号不存在抛 30410")
    void detail_not_found() {
        when(animalMapper.selectByCode("CAT-2026-999")).thenReturn(null);

        assertThatThrownBy(() -> service.detail("CAT-2026-999", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30410));
    }

    private CreateAnimalCommand command(String clientCode) {
        return new CreateAnimalCommand(AnimalCategory.CAT, Gender.FEMALE, "三花", 2,
                "右后腿跛行，左耳有缺口", 7L, List.of("F2026112100013"), "初步体检未见外伤", clientCode);
    }

    private Animal existing() {
        Animal animal = new Animal();
        animal.setId(1L);
        animal.setCode("CAT-2026-001");
        animal.setCategory(AnimalCategory.CAT);
        animal.setName("三花");
        animal.setGender(Gender.FEMALE);
        animal.setColor("三花");
        animal.setEstimatedAge(2);
        animal.setFeatures("右后腿跛行，左耳有缺口");
        animal.setPointId(7L);
        animal.setStatus(AnimalStatus.ON_CAMPUS);
        animal.setNeutered(false);
        animal.setAdopted(false);
        animal.setInternalNote("仅管理员可见：疑似旧伤");
        animal.setDeleted(0);
        animal.setVersion(0);
        animal.setCreatedAt(LocalDateTime.now(CLOCK));
        animal.setUpdatedAt(LocalDateTime.now(CLOCK));
        return animal;
    }
}
