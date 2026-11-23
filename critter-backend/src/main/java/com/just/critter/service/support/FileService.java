package com.just.critter.service.support;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.just.critter.config.CritterProperties;
import com.just.critter.controller.dto.FileDtos;
import com.just.critter.domain.model.SysFile;
import com.just.critter.mapper.SysFileMapper;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 附件服务：两步上传（先拿 fileId 再挂业务，接口设计 §5）。
 *
 * <p>校验与处理：
 * 扩展名 + MIME 双校验；文件名服务端重写为 UUID（防路径穿越）；
 * 图片重编码去掉 EXIF（避免泄露拍摄位置）；
 * ANIMAL_PUBLIC / PROJECT_COVER 需审核，其余业务类型直接可用（审核状态 PASSED）。
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp", "pdf");
    private static final Map<String, String> MIME_BY_EXT = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png",
            "webp", "image/webp", "pdf", "application/pdf");
    private static final Set<String> ALLOWED_BIZ_TYPES = Set.of(
            "AVATAR", "ANIMAL_PUBLIC", "PROJECT_COVER", "PROJECT_SETTLEMENT", "EXPENSE_VOUCHER",
            "ADOPTION_VOUCHER", "REPORT_PHOTO", "VISIT_PHOTO", "GENERAL");
    /** 这几类要审核通过才能在公开页出现 */
    private static final Set<String> AUDIT_REQUIRED = Set.of("ANIMAL_PUBLIC", "PROJECT_COVER");

    private final SysFileMapper fileMapper;
    private final AuditLogService auditLog;
    private final CritterProperties properties;

    public FileService(SysFileMapper fileMapper, AuditLogService auditLog, CritterProperties properties) {
        this.fileMapper = fileMapper;
        this.auditLog = auditLog;
        this.properties = properties;
    }

    @Transactional
    public FileDtos.UploadResponse upload(MultipartFile file, String bizType) {
        String type = bizType == null ? "GENERAL" : bizType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_BIZ_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.ENUM_INVALID.getCode(), "bizType 非法：" + bizType);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_INVALID.getCode(), "请选择要上传的文件");
        }

        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = extensionOf(original);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.FILE_INVALID.getCode(),
                    "仅支持 jpg / png / webp / pdf 格式");
        }
        long max = properties.getFile().getMaxSizeBytes();
        if (file.getSize() > max) {
            throw new BusinessException(ErrorCode.FILE_INVALID.getCode(),
                    "文件大小不能超过 " + (max / 1024 / 1024) + "MB");
        }
        String declaredMime = file.getContentType();
        String expectedMime = MIME_BY_EXT.get(ext);
        if (declaredMime != null && !declaredMime.isBlank()
                && !declaredMime.equalsIgnoreCase(expectedMime)
                && !"application/octet-stream".equalsIgnoreCase(declaredMime)) {
            throw new BusinessException(ErrorCode.FILE_INVALID.getCode(), "文件类型与扩展名不一致");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }
        byte[] stored = stripMetadata(content, ext);

        LocalDate today = LocalDate.now();
        String relativeDir = today.format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(properties.getFile().getStorageDir(), relativeDir).toAbsolutePath().normalize();
        Path target = dir.resolve(storedName);
        try {
            Files.createDirectories(dir);
            Files.write(target, stored);
        } catch (IOException ex) {
            log.error("附件落盘失败 dir={}", dir, ex);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }

        SysFile entity = new SysFile();
        entity.setFileId(nextFileId());
        entity.setBizType(type);
        entity.setName(original);
        entity.setPath(relativeDir + "/" + storedName);
        entity.setMime(expectedMime);
        entity.setSize((long) stored.length);
        entity.setUploaderId(AuthContext.get() == null ? null : AuthContext.get().id());
        entity.setAuditStatus(AUDIT_REQUIRED.contains(type) ? "PENDING" : "PASSED");
        fileMapper.insert(entity);

        auditLog.success("FILE_UPLOAD", "sys_file", entity.getFileId(), null,
                Map.of("bizType", type, "name", original, "size", entity.getSize()));

        return new FileDtos.UploadResponse(entity.getFileId(), metaUrl(entity.getFileId()),
                contentUrl(entity.getFileId()), entity.getName(), entity.getSize(),
                entity.getMime(), entity.getAuditStatus());
    }

    public FileDtos.FileMeta meta(String fileId) {
        SysFile row = requireFile(fileId);
        return new FileDtos.FileMeta(row.getFileId(), row.getBizType(), row.getBizId(), row.getName(),
                row.getMime(), row.getSize() == null ? 0 : row.getSize(), row.getAuditStatus(),
                metaUrl(row.getFileId()), contentUrl(row.getFileId()), row.getCreatedAt());
    }

    /**
     * 读取文件内容。公开页照片（ANIMAL_PUBLIC 且已审核）与结项公示附件允许匿名访问，
     * 票据、上报照片等必须登录——content 接口在拦截器白名单里，这里补上第二道判断。
     */
    public Loaded load(String fileId) {
        SysFile row = requireFile(fileId);
        boolean publicVisible = ("ANIMAL_PUBLIC".equals(row.getBizType()) && "PASSED".equals(row.getAuditStatus()))
                || "PROJECT_SETTLEMENT".equals(row.getBizType());
        if (!publicVisible && AuthContext.get() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN);
        }
        Path path = Paths.get(properties.getFile().getStorageDir(), row.getPath()).toAbsolutePath().normalize();
        Resource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return new Loaded(row, resource);
    }

    /** 审核公开照片：PASSED 才在公开页出现，REJECTED 立即下架。 */
    @Transactional
    public FileDtos.FileMeta audit(String fileId, String decision) {
        SysFile row = requireFile(fileId);
        String target = "PASS".equalsIgnoreCase(decision) || "PASSED".equalsIgnoreCase(decision)
                ? "PASSED" : "REJECTED";
        SysFile patch = new SysFile();
        patch.setId(row.getId());
        patch.setAuditStatus(target);
        fileMapper.updateById(patch);
        auditLog.success("FILE_AUDIT", "sys_file", fileId,
                Map.of("auditStatus", row.getAuditStatus()), Map.of("auditStatus", target));
        return meta(fileId);
    }

    /**
     * 附件审核工作台列表：按审核状态与业务类型筛选，管理员可见。
     * 默认按创建时间倒序——待审照片总是最新的先处理。
     */
    public FileDtos.FilePage list(String auditStatus, String bizType, PageQuery query) {
        String status = normalizeEnum(auditStatus, Set.of("PENDING", "PASSED", "REJECTED"), "auditStatus");
        String type = bizType == null || bizType.isBlank() ? null
                : normalizeEnum(bizType, ALLOWED_BIZ_TYPES, "bizType");

        PageQuery safe = query.normalized();
        Page<SysFile> page = new Page<>(safe.getPage(), safe.getSize());
        IPage<SysFile> rows = fileMapper.selectPage(page, Wrappers.<SysFile>lambdaQuery()
                .eq(status != null, SysFile::getAuditStatus, status)
                .eq(type != null, SysFile::getBizType, type)
                .orderByDesc(SysFile::getId));

        List<FileDtos.FileMeta> items = rows.getRecords().stream()
                .map(row -> new FileDtos.FileMeta(row.getFileId(), row.getBizType(), row.getBizId(),
                        row.getName(), row.getMime(), row.getSize() == null ? 0 : row.getSize(),
                        row.getAuditStatus(), metaUrl(row.getFileId()), contentUrl(row.getFileId()),
                        row.getCreatedAt()))
                .toList();
        return new FileDtos.FilePage(items, new FileDtos.PageMeta(safe.getPage(), safe.getSize(),
                rows.getTotal(), rows.getPages()));
    }

    /** 枚举入参归一：空按"不过滤"处理，非法值 30303，而不是静默查全表。 */
    private static String normalizeEnum(String raw, Set<String> allowed, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) {
            throw new BusinessException(ErrorCode.ENUM_INVALID.getCode(), field + " 非法：" + raw);
        }
        return value;
    }

    private SysFile requireFile(String fileId) {        SysFile row = fileMapper.selectOne(Wrappers.<SysFile>lambdaQuery()
                .eq(SysFile::getFileId, fileId).last("limit 1"));
        if (row == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return row;
    }

    private String nextFileId() {
        for (int i = 0; i < 10; i++) {
            String candidate = "F" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                    + String.format("%05d", ThreadLocalRandom.current().nextInt(100000));
            long exists = fileMapper.selectCount(Wrappers.<SysFile>lambdaQuery().eq(SysFile::getFileId, candidate));
            if (exists == 0) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
    }

    /** 图片重编码去 EXIF；webp 交给原字节（ImageIO 默认不支持写 webp）。 */
    private byte[] stripMetadata(byte[] raw, String ext) {
        if (!IMAGE_EXT.contains(ext) || "webp".equals(ext)) {
            return raw;
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(raw)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                return raw;
            }
            String format = "jpeg".equals(ext) ? "jpg" : ext;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            return ImageIO.write(image, format, output) ? output.toByteArray() : raw;
        } catch (IOException ex) {
            log.warn("图片重编码失败，按原字节保存", ex);
            return raw;
        }
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String metaUrl(String fileId) {
        return "/api/v1/files/" + fileId;
    }

    private static String contentUrl(String fileId) {
        return "/api/v1/files/" + fileId + "/content";
    }

    public record Loaded(SysFile meta, Resource resource) {
    }
}
