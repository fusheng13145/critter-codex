package com.just.critter.controller.dto;

/**
 * 统计导出出参。
 *
 * <p>同步路径直接回 xlsx 二进制（不走统一信封，由 Controller 写 Content-Disposition）；
 * 超过阈值时回 {@link ExportAccepted}，前端拿 taskId 轮询 {@link ExportTaskView}。</p>
 */
public final class ExportDtos {

    private ExportDtos() {
    }

    public record ExportAccepted(String taskId, String status, long rows, String pollUrl) {
    }

    public record ExportTaskView(String taskId, String status, long rows, String fileName,
                                 String downloadUrl, String error) {
    }
}
