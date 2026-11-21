package com.just.critter.tnr.service;

import com.just.critter.care.service.ReferenceAnimal;
import com.just.critter.domain.archive.SequencePort;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.tnr.repository.TnrRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * TNR 五节点推进。任务头保存当前节点，tnr_node 保存每次推进的完整流水。
 */
@Service
public class TnrService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_TEXT = 255;

    private final TnrRepository tnr;
    private final SequencePort sequences;
    private final Clock clock;

    public TnrService(TnrRepository tnr, SequencePort sequences, Clock clock) {
        this.tnr = tnr;
        this.sequences = sequences;
        this.clock = clock;
    }

    @Transactional
    public TnrTaskView create(TnrCommand command, long actorId) {
        validateCreate(command);
        long animalId = command.animalId();
        long handlerId = command.handlerId() == null ? actorId : command.handlerId();

        if (tnr.hasOpenTask(animalId)) {
            throw new BusinessException(ErrorCode.ACTIVE_RELATION_EXISTS,
                    Map.of("animalId", animalId, "reason", "该动物已有未完成的 TNR 任务"));
        }
        ReferenceAnimal animal = tnr.findAnimal(animalId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECORD_NOT_FOUND, Map.of("animalId", animalId)));
        String handlerName = handlerName(handlerId);

        LocalDateTime occurredAt = toLocal(command.occurredAt());
        String tnrNo = "TNR" + occurredAt.toLocalDate().format(NO_DATE)
                + String.format("%03d", sequences.next("TNR", occurredAt.getYear()));
        LocalDateTime now = LocalDateTime.now(clock);
        List<String> fileIds = copyFiles(command.fileIds());

        TnrWrite write = new TnrWrite(tnrNo, animalId, TnrStage.CATCH, handlerId,
                occurredAt, fileIds, trimToNull(command.note()), now, actorId);
        TnrNodeWrite node = new TnrNodeWrite(tnrNo, TnrStage.CATCH, handlerId,
                occurredAt, fileIds, trimToNull(command.note()), now, actorId);

        if (tnr.insertTask(write) != 1 || tnr.insertNode(node) != 1) {
            throw new BusinessException(50002, "TNR 任务写入失败");
        }
        return toView(tnrNo, animal, TnrStage.CATCH, handlerId, handlerName,
                occurredAt, trimToNull(command.note()), fileIds, now, List.of(nodeView(node, handlerName)));
    }

    @Transactional
    public TnrTaskView advance(String tnrNo, TnrNodeCommand command, long actorId) {
        validateAdvance(tnrNo, command);
        TnrStage target = TnrStage.parse(command.stage())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENUM_INVALID, Map.of("field", "stage")));
        String no = tnrNo.trim();

        TnrTaskRecord task = tnr.findTask(no)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECORD_NOT_FOUND, Map.of("tnrNo", no)));
        TnrStage expected = task.stage().next()
                .orElseThrow(() -> new BusinessException(ErrorCode.STATE_TRANSITION_INVALID,
                        Map.of("tnrNo", no, "reason", "该 TNR 任务已完成")));
        if (target != expected) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID,
                    Map.of("tnrNo", no, "expected", expected.name(), "actual", target.name()));
        }
        if (target == TnrStage.SURGERY
                && (command.fileIds() == null || command.fileIds().isEmpty())) {
            throw new BusinessException(ErrorCode.SURGERY_PHOTO_MISSING,
                    Map.of("tnrNo", no, "reason", "手术节点必须上传术前照片"));
        }

        LocalDateTime occurredAt = toLocal(command.occurredAt());
        if (!occurredAt.isAfter(task.occurredAt())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    Map.of("field", "occurredAt", "reason", "节点时间必须晚于当前节点"));
        }
        long handlerId = command.handlerId() == null ? actorId : command.handlerId();
        String handlerName = handlerName(handlerId);
        List<String> fileIds = copyFiles(command.fileIds());
        String note = trimToNull(command.note());
        LocalDateTime now = LocalDateTime.now(clock);

        TnrNodeWrite node = new TnrNodeWrite(no, target, handlerId, occurredAt,
                fileIds, note, now, actorId);
        if (tnr.updateTask(no, node) != 1 || tnr.insertNode(node) != 1) {
            throw new BusinessException(50002, "TNR 节点写入失败");
        }
        if (target == TnrStage.OBSERVE
                && tnr.markNeutered(task.animalId(), occurredAt.toLocalDate(), now) != 1) {
            throw new BusinessException(50002, "动物绝育标记更新失败");
        }

        List<TnrNodeView> nodes = tnr.findNodes(no).stream().map(this::toNodeView).toList();
        return toView(no, task.animalId(), task.animalCode(), task.animalName(), target,
                handlerId, handlerName, occurredAt, note, fileIds, task.createdAt(), nodes);
    }

    public PageResult<TnrTaskView> list(String stage, String animalCode, PageQuery query) {
        PageQuery safe = query == null ? new PageQuery() : query.normalized();
        TnrStage filter = null;
        if (stage != null && !stage.isBlank()) {
            filter = TnrStage.parse(stage)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.ENUM_INVALID, Map.of("field", "stage")));
        }
        String code = animalCode == null || animalCode.isBlank() ? null : animalCode.trim();

        long total = tnr.countTasks(filter, code);
        if (total == 0) {
            return PageResult.of(List.of(), safe.getPage(), safe.getSize(), 0);
        }
        List<TnrTaskView> list = tnr.findTasks(filter, code, safe.offset(), safe.getSize()).stream()
                .map(task -> toView(task.tnrNo(), task.animalId(), task.animalCode(), task.animalName(),
                        task.stage(), task.handlerId(), task.handlerName(), task.occurredAt(),
                        task.note(), task.fileIds(), task.createdAt(),
                        tnr.findNodes(task.tnrNo()).stream().map(this::toNodeView).toList()))
                .toList();
        return PageResult.of(list, safe.getPage(), safe.getSize(), total);
    }

    private void validateCreate(TnrCommand command) {
        if (command == null) {
            throw invalid("command");
        }
        if (command.animalId() == null || command.animalId() <= 0) {
            throw invalid("animalId");
        }
        positiveHandler(command.handlerId());
        checkTime(command.occurredAt());
        checkText("note", command.note());
        checkFiles(command.fileIds());
    }

    private void validateAdvance(String tnrNo, TnrNodeCommand command) {
        if (tnrNo == null || tnrNo.isBlank()) {
            throw invalid("tnrNo");
        }
        if (command == null) {
            throw invalid("command");
        }
        if (TnrStage.parse(command.stage()).isEmpty()) {
            throw new BusinessException(ErrorCode.ENUM_INVALID, Map.of("field", "stage"));
        }
        positiveHandler(command.handlerId());
        checkTime(command.occurredAt());
        checkText("note", command.note());
        checkFiles(command.fileIds());
    }

    private void positiveHandler(Long handlerId) {
        if (handlerId != null && handlerId <= 0) {
            throw invalid("handlerId");
        }
    }

    private void checkTime(OffsetDateTime occurredAt) {
        if (occurredAt == null) {
            throw invalid("occurredAt");
        }
        if (occurredAt.isAfter(OffsetDateTime.now(clock))) {
            throw invalid("occurredAt");
        }
    }

    private void checkText(String field, String value) {
        if (value != null && value.trim().length() > MAX_TEXT) {
            throw invalid(field);
        }
    }

    private void checkFiles(List<String> fileIds) {
        if (fileIds != null && fileIds.stream().anyMatch(TnrService::isBlank)) {
            throw invalid("fileIds");
        }
    }

    private String handlerName(long handlerId) {
        return tnr.findUserName(handlerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECORD_NOT_FOUND, Map.of("handlerId", handlerId)));
    }

    private TnrTaskView toView(String tnrNo, ReferenceAnimal animal, TnrStage stage,
                               long handlerId, String handlerName, LocalDateTime occurredAt,
                               String note, List<String> fileIds, LocalDateTime createdAt,
                               List<TnrNodeView> nodes) {
        return toView(tnrNo, animal.id(), animal.code(), animal.name(), stage, handlerId,
                handlerName, occurredAt, note, fileIds, createdAt, nodes);
    }

    private TnrTaskView toView(String tnrNo, long animalId, String animalCode, String animalName,
                               TnrStage stage, long handlerId, String handlerName,
                               LocalDateTime occurredAt, String note, List<String> fileIds,
                               LocalDateTime createdAt, List<TnrNodeView> nodes) {
        return new TnrTaskView(tnrNo, animalId, animalCode, animalName, stage, handlerId,
                handlerName, occurredAt, note, fileIds, createdAt, nodes);
    }

    private TnrNodeView toNodeView(TnrNodeRecord record) {
        return new TnrNodeView(record.stage(), record.handlerId(), record.handlerName(),
                record.occurredAt(), record.note(), record.fileIds());
    }

    private TnrNodeView nodeView(TnrNodeWrite write, String handlerName) {
        return new TnrNodeView(write.stage(), write.handlerId(), handlerName,
                write.occurredAt(), write.note(), write.fileIds());
    }

    private LocalDateTime toLocal(OffsetDateTime value) {
        return value.atZoneSameInstant(clock.getZone()).toLocalDateTime();
    }

    private static List<String> copyFiles(List<String> fileIds) {
        return fileIds == null ? List.of() : List.copyOf(fileIds);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private BusinessException invalid(String field) {
        return new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", field));
    }
}
