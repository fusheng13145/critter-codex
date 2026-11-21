package com.just.critter.service.support;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.just.critter.controller.dto.LogDtos;
import com.just.critter.domain.model.SysLog;
import com.just.critter.mapper.SysLogMapper;
import com.just.critter.support.api.PageQuery;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 操作日志检索（FR-SYS-02 的查询面）。只做单表条件查询，
 * 排序固定 id 倒序：日志表按时间递增插入，用主键排序比 created_at 更稳。
 */
@Service
public class LogService {

    private final SysLogMapper logMapper;

    public LogService(SysLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    public LogDtos.LogPage query(Long actorId, String action, String bizType, String bizId,
                                 LocalDate from, LocalDate to, String result, PageQuery query) {
        PageQuery safe = query.normalized();
        Page<SysLog> page = new Page<>(safe.getPage(), safe.getSize());
        IPage<SysLog> rows = logMapper.selectPage(page, Wrappers.<SysLog>lambdaQuery()
                .eq(actorId != null, SysLog::getActorId, actorId)
                .eq(action != null && !action.isBlank(), SysLog::getAction, action)
                .eq(bizType != null && !bizType.isBlank(), SysLog::getBizType, bizType)
                .eq(bizId != null && !bizId.isBlank(), SysLog::getBizId, bizId)
                .eq(result != null && !result.isBlank(), SysLog::getResult, result)
                .ge(from != null, SysLog::getCreatedAt, from == null ? null : from.atStartOfDay())
                .lt(to != null, SysLog::getCreatedAt, to == null ? null : to.plusDays(1).atStartOfDay())
                .orderByDesc(SysLog::getId));

        List<LogDtos.LogItem> items = rows.getRecords().stream()
                .map(item -> new LogDtos.LogItem(item.getId(), item.getActorId(), item.getActorRole(),
                        item.getAction(), item.getBizType(), item.getBizId(), item.getBeforeJson(),
                        item.getAfterJson(), item.getIp(), item.getResult(), item.getFailCode(),
                        item.getRequestId(), item.getCreatedAt()))
                .toList();
        return new LogDtos.LogPage(items, new LogDtos.PageMeta(safe.getPage(), safe.getSize(),
                rows.getTotal(), rows.getPages()));
    }
}
