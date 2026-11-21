package com.just.critter.controller.identity;

import com.just.critter.support.api.Result;
import com.just.critter.support.cache.KeyValueStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：前端启动时用它判断后端是否在线（离线则提示"后端未启动"而不是白屏）。
 * 匿名可访问，不返回任何业务数据。
 */
@RestController
@RequestMapping("/api/v1/ping")
public class PingController {

    private final KeyValueStore store;

    public PingController(KeyValueStore store) {
        this.store = store;
    }

    @GetMapping
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("app", "critter-backend");
        data.put("cache", store.mode());
        data.put("time", OffsetDateTime.now());
        return Result.ok(data);
    }
}
