package com.just.critter.controller;

import com.just.critter.controller.dto.FileDtos;
import com.just.critter.service.support.FileService;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.AuthInterceptor;
import com.just.critter.support.security.RequireRoles;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 附件接口：两步上传（先 POST /files 拿 fileId，再挂业务）。
 *
 * <p>契约里 `url` 指向元信息接口，真实字节流在 `{fileId}/content`；
 * 之所以多一个后缀，是因为 <img src> 需要能直接取到二进制，
 * 而元信息接口返回的是 JSON 壳，两者不能混用。
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;
    private final AuthInterceptor authInterceptor;

    public FileController(FileService fileService, AuthInterceptor authInterceptor) {
        this.fileService = fileService;
        this.authInterceptor = authInterceptor;
    }

    @PostMapping
    public Result<FileDtos.UploadResponse> upload(@RequestParam("file") MultipartFile file,
                                                  @RequestParam(value = "bizType", defaultValue = "GENERAL") String bizType) {
        return Result.ok(fileService.upload(file, bizType));
    }

    @GetMapping("/{fileId}")
    public Result<FileDtos.FileMeta> meta(@PathVariable String fileId) {
        return Result.ok(fileService.meta(fileId));
    }

    @PutMapping("/{fileId}/audit")
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<FileDtos.FileMeta> audit(@PathVariable String fileId,
                                           @RequestParam(defaultValue = "PASS") String decision) {
        return Result.ok(fileService.audit(fileId, decision));
    }

    /** 免登录白名单里的唯一入口：公开照片（已审核的 ANIMAL_PUBLIC）可匿名取，其余需登录。 */
    @GetMapping("/{fileId}/content")
    public ResponseEntity<Resource> content(@PathVariable String fileId, HttpServletRequest request) {
        authInterceptor.authenticateIfPresent(request);
        try {
            FileService.Loaded loaded = fileService.load(fileId);
            String filename = loaded.meta().getName() == null ? fileId : loaded.meta().getName();
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            MediaType mediaType;
            try {
                mediaType = MediaType.parseMediaType(loaded.meta().getMime());
            } catch (RuntimeException ex) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(loaded.meta().getSize() == null ? 0 : loaded.meta().getSize())
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename*=UTF-8''" + encoded)
                    .body(loaded.resource());
        } finally {
            com.just.critter.support.security.AuthContext.clear();
        }
    }
}
