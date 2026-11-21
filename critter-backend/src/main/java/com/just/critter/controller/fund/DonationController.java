package com.just.critter.controller.fund;

import com.just.critter.domain.enums.DonationStatus;
import com.just.critter.service.fund.BatchConfirmResult;
import com.just.critter.service.fund.CreatedDonation;
import com.just.critter.service.fund.DonationCertificateService;
import com.just.critter.service.fund.DonationConfirmation;
import com.just.critter.service.fund.DonationFilter;
import com.just.critter.service.fund.DonationFlagResult;
import com.just.critter.service.fund.DonationService;
import com.just.critter.service.fund.DonationSummary;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.Result;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import com.just.critter.support.security.RequireRoles;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 捐赠接口：登记登录可写；列表与核对仅动物管理员。
 *
 * <p>单笔核对保留 {@code PUT /donations/{no}/confirm}；批量核对按接口文档通用约定
 * 另开 {@code PUT /donations/batch}，请求 {@code {ids:[捐赠单号]}}，逐笔事务、部分成功。</p>
 */
@RestController
@RequestMapping("/api/v1/donations")
public class DonationController {

    private final DonationService service;
    private final DonationCertificateService certificateService;

    public DonationController(DonationService service, DonationCertificateService certificateService) {
        this.service = service;
        this.certificateService = certificateService;
    }

    @PostMapping
    public Result<CreatedDonation> create(@RequestBody(required = false) CreateDonationRequest request) {
        CurrentUser actor = AuthContext.require();
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "请求体不能为空");
        }
        return Result.ok(service.create(request.toCommand(), actor));
    }

    @GetMapping
    @RequireRoles({"ANIMAL_ADMIN"})
    public Result<PageResult<DonationSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String projectNo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        DonationFilter filter = new DonationFilter(parseStatuses(status), projectNo);
        return Result.ok(service.search(filter, new PageQuery(page, size)));
    }

    @PutMapping("/{donationNo}/confirm")
    @RequireRoles({"ANIMAL_ADMIN"})
    public Result<DonationConfirmation> confirm(@PathVariable String donationNo,
                                                @RequestBody(required = false) ConfirmDonationRequest request) {
        CurrentUser actor = AuthContext.require();
        return Result.ok(service.confirm(donationNo, request == null ? null : request.toCommand(), actor));
    }

    @PutMapping("/batch")
    @RequireRoles({"ANIMAL_ADMIN"})
    public Result<BatchConfirmResult> confirmBatch(@RequestBody(required = false) BatchDonationRequest request) {
        CurrentUser actor = AuthContext.require();
        if (request == null || request.ids() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "请选择待核对捐赠");
        }
        return Result.ok(service.confirmBatch(request.ids(), actor));
    }

    @PutMapping("/{donationNo}/flag")
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<DonationFlagResult> flag(@PathVariable String donationNo,
                                           @RequestBody(required = false) FlagDonationRequest request) {
        CurrentUser actor = AuthContext.require();
        return Result.ok(service.flag(donationNo, request == null ? null : request.toCommand(), actor));
    }

    @GetMapping("/{donationNo}/certificate")
    public ResponseEntity<byte[]> certificate(@PathVariable String donationNo) {
        CurrentUser actor = AuthContext.require();
        byte[] pdf = certificateService.pdf(donationNo, actor);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(certificateService.fileName(donationNo), StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private Set<DonationStatus> parseStatuses(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Set<DonationStatus> statuses = new LinkedHashSet<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(value -> {
                    try {
                        statuses.add(DonationStatus.valueOf(value.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ex) {
                        throw new BusinessException(ErrorCode.ENUM_INVALID.getCode(), "捐赠状态非法：" + value);
                    }
                });
        return statuses;
    }
}
