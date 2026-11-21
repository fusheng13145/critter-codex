package com.just.critter.config;

import com.just.critter.domain.archive.AnimalCodeGenerator;
import com.just.critter.domain.archive.GroupCodeGenerator;
import com.just.critter.domain.archive.SequencePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 统一时钟、编号器与 MyBatis-Plus 拦截器。
 * 时钟固定 Asia/Shanghai，避免审计字段随 JVM 默认时区漂移。
 */
@Configuration
public class AppConfig {

    public static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    public Clock clock() {
        return Clock.system(APP_ZONE);
    }

    @Bean
    public AnimalCodeGenerator animalCodeGenerator(SequencePort sequences, Clock clock) {
        return new AnimalCodeGenerator(sequences, clock);
    }

    @Bean
    public GroupCodeGenerator groupCodeGenerator(SequencePort sequences, Clock clock) {
        return new GroupCodeGenerator(sequences, clock);
    }
}
