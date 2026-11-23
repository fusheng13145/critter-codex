package com.just.critter.service.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.just.critter.config.CritterProperties;
import com.just.critter.controller.dto.FileDtos;
import com.just.critter.domain.model.SysFile;
import com.just.critter.mapper.SysFileMapper;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 附件主链路（成员 A 域此前缺测）：上传双校验、审核状态机、
 * 审核工作台列表（PUT /{fileId}/audit 的前端入口依赖它）。
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private SysFileMapper files;
    @Mock
    private AuditLogService audit;

    private final CritterProperties properties = new CritterProperties();

    private FileService service;
    private Path storageRoot;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysFile.class);
    }

    @BeforeEach
    void setUp() throws IOException {
        storageRoot = Files.createTempDirectory("critter-file-test");
        properties.getFile().setStorageDir(storageRoot.toString());
        properties.getFile().setMaxSizeBytes(5 * 1024 * 1024L);
        service = new FileService(files, audit, properties);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private byte[] png() {
        try {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private MockMultipartFile upload(String bizType) {
        return new MockMultipartFile("file", "campus-cat.png", "image/png", png());
    }

    private CurrentUser uploader() {
        return new CurrentUser(1001L, "2023019001", "青檐一号", "STUDENT",
                Set.of("STUDENT"), "计算机学院", "jti-test", Instant.now().plusSeconds(600));
    }

    @Test
    @DisplayName("bizType 非法：30303，不落盘")
    void upload_rejects_unknown_biz_type() {
        assertThatThrownBy(() -> service.upload(upload("HACK"), "HACK"))
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.ENUM_INVALID.getCode());
    }

    @Test
    @DisplayName("扩展名不在白名单：FILE_INVALID")
    void upload_rejects_bad_extension() {
        MockMultipartFile gif = new MockMultipartFile("file", "cat.gif", "image/gif", png());
        assertThatThrownBy(() -> service.upload(gif, "ANIMAL_PUBLIC"))
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.FILE_INVALID.getCode());
    }

    @Test
    @DisplayName("超出大小限制：FILE_INVALID，且不写库")
    void upload_rejects_oversize() {
        properties.getFile().setMaxSizeBytes(10L);
        assertThatThrownBy(() -> service.upload(upload("ANIMAL_PUBLIC"), "ANIMAL_PUBLIC"))
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.FILE_INVALID.getCode());
        verify(files, never()).insert(any(SysFile.class));
    }

    @Test
    @DisplayName("公开档案照片上传：PENDING 待审、落盘重编码、fileId 规则 F+日期+序号")
    void upload_public_photo_stores_pending() throws IOException {
        AuthContext.set(uploader());
        when(files.selectCount(any())).thenReturn(0L);
        when(files.insert(any(SysFile.class))).thenReturn(1);

        FileDtos.UploadResponse response = service.upload(upload("ANIMAL_PUBLIC"), "ANIMAL_PUBLIC");

        assertThat(response.auditStatus()).isEqualTo("PENDING");
        assertThat(response.fileId()).startsWith("F").hasSize(14);
        assertThat(response.contentUrl()).isEqualTo("/api/v1/files/" + response.fileId() + "/content");

        ArgumentCaptor<SysFile> captor = ArgumentCaptor.forClass(SysFile.class);
        verify(files).insert(captor.capture());
        SysFile saved = captor.getValue();
        assertThat(saved.getUploaderId()).isEqualTo(1001L);
        assertThat(Files.exists(storageRoot.resolve(saved.getPath()))).isTrue();
    }

    @Test
    @DisplayName("票据类附件免审直接 PASSED（不公开但永久留痕）")
    void upload_voucher_passes_immediately() {
        AuthContext.set(uploader());
        when(files.selectCount(any())).thenReturn(0L);
        when(files.insert(any(SysFile.class))).thenReturn(1);

        FileDtos.UploadResponse response = service.upload(upload("EXPENSE_VOUCHER"), "EXPENSE_VOUCHER");

        assertThat(response.auditStatus()).isEqualTo("PASSED");
    }

    @Test
    @DisplayName("审核通过：状态机 PENDING→PASSED 并写审计")
    void audit_passes_pending_photo() {
        SysFile row = new SysFile();
        row.setId(1L);
        row.setFileId("F2026112300001");
        row.setBizType("ANIMAL_PUBLIC");
        row.setAuditStatus("PENDING");
        row.setName("campus-cat.png");
        row.setMime("image/png");
        row.setSize(100L);
        row.setPath("2026/11/x.png");
        when(files.selectOne(any())).thenReturn(row);
        when(files.updateById(any(SysFile.class))).thenReturn(1);

        service.audit("F2026112300001", "PASS");

        ArgumentCaptor<SysFile> captor = ArgumentCaptor.forClass(SysFile.class);
        verify(files).updateById(captor.capture());
        assertThat(captor.getValue().getAuditStatus()).isEqualTo("PASSED");
        verify(audit).success(eq("FILE_AUDIT"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("列表：auditStatus 非法 30303")
    void list_rejects_illegal_audit_status() {
        assertThatThrownBy(() -> service.list("WAITING", null, new PageQuery(1, 20)))
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.ENUM_INVALID.getCode());
    }

    @Test
    @DisplayName("列表：bizType 非法 30303")
    void list_rejects_illegal_biz_type() {
        assertThatThrownBy(() -> service.list("PENDING", "HACK", new PageQuery(1, 20)))
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.ENUM_INVALID.getCode());
    }

    @Test
    @DisplayName("列表：待审优先按时间倒序返回，meta 带完整 URL")
    void list_returns_paged_meta() {
        SysFile row = new SysFile();
        row.setId(1L);
        row.setFileId("F2026112300001");
        row.setBizType("ANIMAL_PUBLIC");
        row.setAuditStatus("PENDING");
        row.setName("campus-cat.png");
        row.setMime("image/png");
        row.setSize(123L);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysFile> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(List.of(row));
        page.setTotal(1);
        page.setPages(1);
        when(files.selectPage(any(), any())).thenReturn(page);

        FileDtos.FilePage result = service.list("PENDING", null, new PageQuery(1, 20));

        assertThat(result.list()).hasSize(1);
        FileDtos.FileMeta meta = result.list().get(0);
        assertThat(meta.fileId()).isEqualTo("F2026112300001");
        assertThat(meta.auditStatus()).isEqualTo("PENDING");
        assertThat(meta.contentUrl()).isEqualTo("/api/v1/files/F2026112300001/content");
        assertThat(result.page().total()).isEqualTo(1);
    }
}
