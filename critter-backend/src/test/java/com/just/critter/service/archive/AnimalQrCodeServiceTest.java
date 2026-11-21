package com.just.critter.service.archive;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.just.critter.config.CritterProperties;
import com.just.critter.domain.model.Animal;
import com.just.critter.mapper.AnimalAliasMapper;
import com.just.critter.mapper.AnimalMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 档案二维码：PNG 可解码，内容只包含公开档案 URL。
 */
@ExtendWith(MockitoExtension.class)
class AnimalQrCodeServiceTest {

    @Mock
    private AnimalMapper animals;
    @Mock
    private AnimalAliasMapper aliases;

    @Test
    @DisplayName("生成 PNG 并解码出公开档案 URL")
    void qrcode_decodes_public_url() throws Exception {
        Animal animal = new Animal();
        animal.setId(1L);
        animal.setCode("CAT-2026-001");
        when(animals.selectByCode("CAT-2026-001")).thenReturn(animal);
        CritterProperties properties = new CritterProperties();
        properties.getQrcode().setAnimalUrlPrefix("https://critter.example.edu/animals/");

        AnimalQrCodeService service = new AnimalQrCodeService(animals, aliases, properties);
        byte[] png = service.png("CAT-2026-001");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));

        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(300);
        String decoded = new MultiFormatReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)))).getText();
        assertThat(decoded).isEqualTo("https://critter.example.edu/animals/CAT-2026-001");
    }
}
