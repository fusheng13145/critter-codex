package com.just.critter.service.fund;

import com.just.critter.config.CritterProperties;
import com.just.critter.domain.enums.DonationStatus;
import com.just.critter.domain.model.Donation;
import com.just.critter.domain.model.Project;
import com.just.critter.mapper.DonationMapper;
import com.just.critter.mapper.ProjectMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.CurrentUser;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 捐赠凭证 PDF。凭证只对已入账捐赠签发；实名捐赠人本人或管理员可下载。
 * 字体优先读配置，其次探测 Windows/Linux 常见 CJK 字体；镜像里安装 fonts-noto-cjk 即可。
 */
@Service
public class DonationCertificateService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    private static final float PAGE_WIDTH = PDRectangle.A4.getHeight();

    private final DonationMapper donations;
    private final ProjectMapper projects;
    private final CritterProperties properties;
    private final AuditLogService auditLog;

    public DonationCertificateService(DonationMapper donations, ProjectMapper projects,
                                      CritterProperties properties, AuditLogService auditLog) {
        this.donations = donations;
        this.projects = projects;
        this.properties = properties;
        this.auditLog = auditLog;
    }

    public byte[] pdf(String donationNo, CurrentUser actor) {
        if (actor == null) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN);
        }
        String no = donationNo == null ? null : donationNo.trim();
        if (no == null || no.isEmpty()) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "捐赠单号必填");
        }
        Donation donation = donations.selectByDonationNo(no);
        if (donation == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "捐赠记录不存在");
        }
        boolean admin = actor.hasRole("ANIMAL_ADMIN") || actor.hasRole("SYSTEM_ADMIN");
        if (!admin && (!Objects.equals(donation.getUserId(), actor.id()) || donation.getUserId() == null)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "只能下载本人捐赠的凭证");
        }
        if (donation.getStatus() != DonationStatus.VERIFIED) {
            throw new BusinessException(ErrorCode.PAYMENT_UNVERIFIED, "捐赠尚未入账，不能生成凭证");
        }
        Project project = projects.selectById(donation.getProjectId());
        if (project == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "捐赠关联项目不存在");
        }
        byte[] pdf = render(donation, project, actor);
        auditLog.successAs(actor.id(), actor.primaryRole() == null ? "STUDENT" : actor.primaryRole(),
                "DONATION_CERTIFICATE", "DONATION", donation.getDonationNo(), null,
                java.util.Map.of("status", donation.getStatus().name()));
        return pdf;
    }

    public String fileName(String donationNo) {
        return "捐赠凭证-" + donationNo + ".pdf";
    }

    private byte[] render(Donation donation, Project project, CurrentUser actor) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType0Font font = loadCjkFont(doc);
            PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setStrokingColor(new Color(32, 83, 117));
                cs.setLineWidth(1.5f);
                cs.addRect(28, 28, page.getMediaBox().getWidth() - 56, page.getMediaBox().getHeight() - 56);
                cs.stroke();
                cs.setStrokingColor(new Color(190, 210, 220));
                cs.setLineWidth(0.6f);
                cs.addRect(35, 35, page.getMediaBox().getWidth() - 70, page.getMediaBox().getHeight() - 70);
                cs.stroke();

                float center = page.getMediaBox().getWidth() / 2;
                drawCentered(cs, "捐赠证书", font, 30, 500, new Color(32, 83, 117));
                drawCentered(cs, "CERTIFICATE OF DONATION", font, 9, 478, new Color(110, 125, 135));

                String donor = donation.getDonorDisplay() == null ? "爱心人士" : donation.getDonorDisplay();
                drawCentered(cs, "兹证明 " + donor + " 向", font, 15, 425, Color.DARK_GRAY);
                float y = drawWrappedCentered(cs, "“" + project.getName() + "”", font, 18, 396, Color.BLACK, 30);
                drawCentered(cs, "捐赠人民币 " + yuan(donation.getAmount()) + " 元", font, 20, y - 18, Color.BLACK);
                drawCentered(cs, "用于校园生物档案与救助公益项目", font, 13, y - 48, Color.DARK_GRAY);

                LocalDateTime at = donation.getVerifiedAt() == null
                        ? LocalDateTime.now(ZONE) : donation.getVerifiedAt();
                drawCentered(cs, "捐赠单号：" + donation.getDonationNo(), font, 11, 276, Color.GRAY);
                drawCentered(cs, "证书编号：QYWL-DN-" + donation.getDonationNo(), font, 11, 258, Color.GRAY);
                drawCentered(cs, "入账日期：" + at.toLocalDate().format(DATE), font, 11, 240, Color.GRAY);

                drawCentered(cs, "青檐万灵册 · 校园生物档案与救助平台", font, 12, 112, new Color(32, 83, 117));
                drawCentered(cs, "本证书由平台自动生成，可在捐赠记录中核验", font, 9, 92, Color.GRAY);
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "捐赠凭证生成失败");
        }
    }

    private float drawWrappedCentered(PDPageContentStream cs, String text, PDType0Font font,
                                      float size, float y, Color color, int perLine) throws IOException {
        List<String> lines = wrap(text, perLine);
        float current = y;
        for (String line : lines) {
            drawCentered(cs, line, font, size, current, color);
            current -= size + 6;
        }
        return current + size + 6;
    }

    private void drawCentered(PDPageContentStream cs, String text, PDType0Font font,
                              float size, float y, Color color) throws IOException {
        float width = font.getStringWidth(text) / 1000f * size;
        float x = (PAGE_WIDTH - width) / 2f;
        cs.beginText();
        cs.setNonStrokingColor(color);
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private List<String> wrap(String text, int perLine) {
        List<String> lines = new ArrayList<>();
        String value = text == null ? "" : text;
        for (int i = 0; i < value.length(); i += perLine) {
            lines.add(value.substring(i, Math.min(value.length(), i + perLine)));
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private PDType0Font loadCjkFont(PDDocument doc) {
        List<String> candidates = new ArrayList<>();
        String configured = properties.getCertificate().getFontPath();
        if (configured != null && !configured.isBlank()) {
            candidates.add(configured);
        }
        candidates.add("C:/Windows/Fonts/msyh.ttc");
        candidates.add("C:/Windows/Fonts/simhei.ttf");
        candidates.add("C:/Windows/Fonts/NotoSansSC-VF.ttf");
        candidates.add("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc");
        candidates.add("/usr/share/fonts/opentype/noto/NotoSansCJKsc-Regular.otf");
        candidates.add("/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttf");
        candidates.add("/System/Library/Fonts/PingFang.ttc");

        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                if (candidate.toLowerCase().endsWith(".ttc")) {
                    try (TrueTypeCollection collection = new TrueTypeCollection(path.toFile())) {
                        final PDType0Font[] loaded = new PDType0Font[1];
                        collection.processAllFonts(ttf -> {
                            if (loaded[0] == null) {
                                loaded[0] = PDType0Font.load(doc, ttf, true);
                            }
                        });
                        if (loaded[0] != null) {
                            return loaded[0];
                        }
                    }
                } else {
                    try (FileInputStream in = new FileInputStream(path.toFile())) {
                        return PDType0Font.load(doc, in, true);
                    }
                }
            } catch (Exception ignored) {
                // 继续探测下一个字体；字体不可嵌入时不能静默输出乱码。
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                "缺少中文字体，无法生成捐赠凭证；请安装 fonts-noto-cjk 或配置 critter.certificate.font-path");
    }

    private String yuan(int cents) {
        return String.format(java.util.Locale.ROOT, "%,.2f", cents / 100.0);
    }
}
