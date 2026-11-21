package com.just.critter.tnr;

import com.just.critter.care.service.ReferenceAnimal;
import com.just.critter.domain.archive.SequencePort;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.tnr.repository.TnrRepository;
import com.just.critter.tnr.service.TnrCommand;
import com.just.critter.tnr.service.TnrNodeCommand;
import com.just.critter.tnr.service.TnrNodeRecord;
import com.just.critter.tnr.service.TnrNodeWrite;
import com.just.critter.tnr.service.TnrService;
import com.just.critter.tnr.service.TnrStage;
import com.just.critter.tnr.service.TnrTaskRecord;
import com.just.critter.tnr.service.TnrTaskView;
import com.just.critter.tnr.service.TnrWrite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TnrServiceTests {

    private static final long ANIMAL_ID = 11L;
    private static final long HANDLER_ID = 9L;
    private static final long ACTOR_ID = 9001L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private TnrRepository tnr;
    private SequencePort sequences;
    private TnrService service;

    @BeforeEach
    void setUp() {
        tnr = mock(TnrRepository.class);
        sequences = mock(SequencePort.class);
        service = new TnrService(tnr, sequences, CLOCK);
    }

    @Test
    @DisplayName("发起 TNR：生成 TNR 编号，写入 CATCH 任务与首节点，返回处理人")
    void createsTnrTask() {
        when(tnr.hasOpenTask(ANIMAL_ID)).thenReturn(false);
        when(tnr.findAnimal(ANIMAL_ID)).thenReturn(Optional.of(
                new ReferenceAnimal(ANIMAL_ID, "CAT-2026-031", "三花")));
        when(tnr.findUserName(HANDLER_ID)).thenReturn(Optional.of("王医生"));
        when(sequences.next("TNR", 2026)).thenReturn(1);
        when(tnr.insertTask(any())).thenReturn(1);
        when(tnr.insertNode(any())).thenReturn(1);

        TnrTaskView result = service.create(new TnrCommand(
                ANIMAL_ID, HANDLER_ID,
                OffsetDateTime.parse("2026-11-20T09:00:00+08:00"),
                "校园东区抓捕，装入诱捕笼", List.of("F20261120001")), ACTOR_ID);

        assertThat(result.tnrNo()).isEqualTo("TNR20261120001");
        assertThat(result.stage()).isEqualTo(TnrStage.CATCH);
        assertThat(result.animalCode()).isEqualTo("CAT-2026-031");
        assertThat(result.handlerName()).isEqualTo("王医生");
        assertThat(result.nodes()).hasSize(1);
        assertThat(result.nodes().get(0).stage()).isEqualTo(TnrStage.CATCH);

        ArgumentCaptor<TnrWrite> task = ArgumentCaptor.forClass(TnrWrite.class);
        verify(tnr).insertTask(task.capture());
        assertThat(task.getValue().tnrNo()).isEqualTo("TNR20261120001");
        assertThat(task.getValue().handlerId()).isEqualTo(HANDLER_ID);
        assertThat(task.getValue().createdBy()).isEqualTo(ACTOR_ID);

        ArgumentCaptor<TnrNodeWrite> node = ArgumentCaptor.forClass(TnrNodeWrite.class);
        verify(tnr).insertNode(node.capture());
        assertThat(node.getValue().stage()).isEqualTo(TnrStage.CATCH);
        assertThat(node.getValue().fileIds()).containsExactly("F20261120001");
    }

    @Test
    @DisplayName("发起 TNR：动物不存在返回 30410")
    void rejectsMissingAnimal() {
        when(tnr.hasOpenTask(ANIMAL_ID)).thenReturn(false);
        when(tnr.findAnimal(ANIMAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command(), ACTOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));

        verify(tnr, never()).insertTask(any());
        verifyNoInteractions(sequences);
    }

    @Test
    @DisplayName("发起 TNR：同一动物已有未观察完成的任务返回 30403")
    void rejectsDuplicateOpenTask() {
        when(tnr.hasOpenTask(ANIMAL_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(command(), ACTOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30403));

        verify(tnr, never()).findAnimal(anyLong());
        verifyNoInteractions(sequences);
    }

    @Test
    @DisplayName("发起 TNR：未来时间、空影像元素与超长备注返回 30301")
    void rejectsInvalidCreateFields() {
        TnrCommand future = new TnrCommand(ANIMAL_ID, HANDLER_ID,
                OffsetDateTime.parse("2026-11-21T15:00:00+08:00"), null, List.of());
        TnrCommand blankFile = new TnrCommand(ANIMAL_ID, HANDLER_ID,
                OffsetDateTime.parse("2026-11-20T09:00:00+08:00"), null, List.of("F1", " "));
        TnrCommand longNote = new TnrCommand(ANIMAL_ID, HANDLER_ID,
                OffsetDateTime.parse("2026-11-20T09:00:00+08:00"), "备".repeat(256), List.of());

        assertThatThrownBy(() -> service.create(future, ACTOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));
        assertThatThrownBy(() -> service.create(blankFile, ACTOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));
        assertThatThrownBy(() -> service.create(longNote, ACTOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));

        verifyNoInteractions(tnr, sequences);
    }

    @Test
    @DisplayName("推进节点：CATCH 只能推进到 TRANSPORT，并追加节点记录")
    void advancesToNextStage() {
        when(tnr.findTask("TNR20261120001")).thenReturn(Optional.of(task(
                TnrStage.CATCH, LocalDateTime.of(2026, 11, 20, 9, 0))));
        when(tnr.findUserName(HANDLER_ID)).thenReturn(Optional.of("王医生"));
        when(tnr.updateTask(any(), any())).thenReturn(1);
        when(tnr.insertNode(any())).thenReturn(1);
        when(tnr.findNodes("TNR20261120001")).thenReturn(List.of(
                node(TnrStage.CATCH, LocalDateTime.of(2026, 11, 20, 9, 0)),
                node(TnrStage.TRANSPORT, LocalDateTime.of(2026, 11, 20, 10, 0))));

        TnrTaskView result = service.advance("TNR20261120001", new TnrNodeCommand(
                "TRANSPORT", HANDLER_ID,
                OffsetDateTime.parse("2026-11-20T10:00:00+08:00"),
                "送往合作医院", List.of()), ACTOR_ID);

        assertThat(result.stage()).isEqualTo(TnrStage.TRANSPORT);
        assertThat(result.nodes()).hasSize(2);

        ArgumentCaptor<TnrNodeWrite> node = ArgumentCaptor.forClass(TnrNodeWrite.class);
        verify(tnr).insertNode(node.capture());
        assertThat(node.getValue().stage()).isEqualTo(TnrStage.TRANSPORT);
        verify(tnr).updateTask(eq("TNR20261120001"), any());
    }

    @Test
    @DisplayName("推进节点：跳节点返回 30402，不写库")
    void rejectsSkippingStage() {
        when(tnr.findTask("TNR20261120001")).thenReturn(Optional.of(task(
                TnrStage.CATCH, LocalDateTime.of(2026, 11, 20, 9, 0))));

        assertThatThrownBy(() -> service.advance("TNR20261120001", new TnrNodeCommand(
                "SURGERY", HANDLER_ID,
                OffsetDateTime.parse("2026-11-20T11:00:00+08:00"),
                null, List.of("F1")), ACTOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30402));

        verify(tnr, never()).updateTask(any(), any());
        verify(tnr, never()).insertNode(any());
    }

    @Test
    @DisplayName("推进节点：手术节点缺少术前照片返回 30601")
    void rejectsSurgeryWithoutPhoto() {
        when(tnr.findTask("TNR20261120001")).thenReturn(Optional.of(task(
                TnrStage.TRANSPORT, LocalDateTime.of(2026, 11, 20, 10, 0))));

        assertThatThrownBy(() -> service.advance("TNR20261120001", new TnrNodeCommand(
                "SURGERY", HANDLER_ID,
                OffsetDateTime.parse("2026-11-20T11:00:00+08:00"),
                "绝育手术", List.of()), ACTOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30601));

        verify(tnr, never()).updateTask(any(), any());
        verify(tnr, never()).insertNode(any());
    }

    @Test
    @DisplayName("推进节点：最后 OBSERVE 完成时同步动物绝育标记")
    void marksAnimalNeuteredOnObserve() {
        LocalDateTime releaseAt = LocalDateTime.of(2026, 11, 20, 16, 0);
        when(tnr.findTask("TNR20261120001")).thenReturn(Optional.of(task(
                TnrStage.RELEASE, releaseAt)));
        when(tnr.findUserName(HANDLER_ID)).thenReturn(Optional.of("王医生"));
        when(tnr.updateTask(any(), any())).thenReturn(1);
        when(tnr.insertNode(any())).thenReturn(1);
        when(tnr.markNeutered(eq(ANIMAL_ID), eq(LocalDate.of(2026, 11, 21)), any()))
                .thenReturn(1);
        when(tnr.findNodes("TNR20261120001")).thenReturn(List.of(
                node(TnrStage.OBSERVE, LocalDateTime.of(2026, 11, 21, 9, 0))));

        TnrTaskView result = service.advance("TNR20261120001", new TnrNodeCommand(
                "OBSERVE", HANDLER_ID,
                OffsetDateTime.parse("2026-11-21T09:00:00+08:00"),
                "观察期结束，恢复放归", List.of("F20261121001")), ACTOR_ID);

        assertThat(result.stage()).isEqualTo(TnrStage.OBSERVE);
        verify(tnr).markNeutered(eq(ANIMAL_ID), eq(LocalDate.of(2026, 11, 21)), any());
    }

    @Test
    @DisplayName("推进节点：任务不存在返回 30410")
    void rejectsMissingTaskOnAdvance() {
        when(tnr.findTask("TNR20269999001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.advance("TNR20269999001", new TnrNodeCommand(
                "TRANSPORT", HANDLER_ID,
                OffsetDateTime.parse("2026-11-20T10:00:00+08:00"),
                null, List.of()), ACTOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));

        verify(tnr, never()).updateTask(any(), any());
    }

    @Test
    @DisplayName("推进节点：节点时间早于当前节点返回 30301")
    void rejectsBackwardOccurredAt() {
        when(tnr.findTask("TNR20261120001")).thenReturn(Optional.of(task(
                TnrStage.CATCH, LocalDateTime.of(2026, 11, 20, 9, 0))));

        assertThatThrownBy(() -> service.advance("TNR20261120001", new TnrNodeCommand(
                "TRANSPORT", HANDLER_ID,
                OffsetDateTime.parse("2026-11-19T10:00:00+08:00"),
                null, List.of()), ACTOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));

        verify(tnr, never()).updateTask(any(), any());
    }

    @Test
    @DisplayName("推进节点：stage 非法返回 30303，已完成任务继续推进返回 30402")
    void rejectsInvalidOrCompletedStage() {
        when(tnr.findTask("TNR20261120001")).thenReturn(Optional.of(task(
                TnrStage.OBSERVE, LocalDateTime.of(2026, 11, 20, 16, 0))));

        assertThatThrownBy(() -> service.advance("TNR20261120001", new TnrNodeCommand(
                "UNKNOWN", HANDLER_ID,
                OffsetDateTime.parse("2026-11-21T09:00:00+08:00"),
                null, List.of()), ACTOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30303));

        assertThatThrownBy(() -> service.advance("TNR20261120001", new TnrNodeCommand(
                "OBSERVE", HANDLER_ID,
                OffsetDateTime.parse("2026-11-21T09:00:00+08:00"),
                null, List.of()), ACTOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30402));
    }

    private TnrCommand command() {
        return new TnrCommand(ANIMAL_ID, HANDLER_ID,
                OffsetDateTime.parse("2026-11-20T09:00:00+08:00"),
                "校园东区抓捕", List.of());
    }

    private TnrTaskRecord task(TnrStage stage, LocalDateTime occurredAt) {
        return new TnrTaskRecord("TNR20261120001", ANIMAL_ID, "CAT-2026-031", "三花",
                stage, HANDLER_ID, "王医生", occurredAt, List.of("F1"),
                "节点备注", LocalDateTime.of(2026, 11, 20, 9, 0));
    }

    private TnrNodeRecord node(TnrStage stage, LocalDateTime occurredAt) {
        return new TnrNodeRecord(stage, HANDLER_ID, "王医生", occurredAt,
                "节点备注", List.of("F1"));
    }
}
