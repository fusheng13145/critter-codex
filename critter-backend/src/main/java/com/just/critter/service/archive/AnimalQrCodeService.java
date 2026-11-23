package com.just.critter.service.archive;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.just.critter.config.CritterProperties;
import com.just.critter.domain.model.Animal;
import com.just.critter.mapper.AnimalAliasMapper;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * 档案公开页二维码。只编码公开档案编号，不把联系方式、内部备注等敏感字段带进二维码。
 */
@Service
public class AnimalQrCodeService {

    private static final int SIZE = 300;

    private final AnimalMapper animals;
    private final AnimalAliasMapper aliases;
    private final CritterProperties properties;

    public AnimalQrCodeService(AnimalMapper animals, AnimalAliasMapper aliases,
                               CritterProperties properties) {
        this.animals = animals;
        this.aliases = aliases;
        this.properties = properties;
    }

    public byte[] png(String code) {
        Animal animal = resolve(code);
        String prefix = properties.getQrcode().getAnimalUrlPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "http://localhost:5173/p/";
        }
        String target = prefix.endsWith("/") ? prefix + animal.getCode() : prefix + "/" + animal.getCode();
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(target, BarcodeFormat.QR_CODE, SIZE, SIZE,
                    Map.of(EncodeHintType.CHARACTER_SET, "UTF-8",
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "二维码生成失败");
        }
    }

    public String targetUrl(String code) {
        Animal animal = resolve(code);
        String prefix = properties.getQrcode().getAnimalUrlPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "http://localhost:5173/p/";
        }
        return prefix.endsWith("/") ? prefix + animal.getCode() : prefix + "/" + animal.getCode();
    }

    private Animal resolve(String code) {
        String normalized = code == null ? null : code.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        Animal animal = animals.selectByCode(normalized);
        if (animal == null) {
            var alias = aliases.selectByAliasCode(normalized);
            animal = alias == null ? null : animals.selectById(alias.getAnimalId());
        }
        if (animal != null && animal.getMergedIntoId() != null) {
            Animal main = animals.selectById(animal.getMergedIntoId());
            if (main != null) {
                return main;
            }
        }
        if (animal == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return animal;
    }
}
