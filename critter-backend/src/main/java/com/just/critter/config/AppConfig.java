package com.just.critter.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.just.critter.domain.archive.AnimalCodeGenerator;
import com.just.critter.domain.archive.GroupCodeGenerator;
import com.just.critter.domain.archive.SequencePort;
import com.just.critter.domain.rescue.OrderCodeGenerator;
import com.just.critter.domain.rescue.ReportCodeGenerator;
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

    @Bean
    public ReportCodeGenerator reportCodeGenerator(SequencePort sequences, Clock clock) {
        return new ReportCodeGenerator(sequences, clock);
    }

    @Bean
    public OrderCodeGenerator orderCodeGenerator(SequencePort sequences, Clock clock) {
        return new OrderCodeGenerator(sequences, clock);
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
