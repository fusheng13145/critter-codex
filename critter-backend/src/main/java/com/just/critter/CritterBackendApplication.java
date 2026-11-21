package com.just.critter;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.just.critter.mapper")
@EnableScheduling
public class CritterBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(CritterBackendApplication.class, args);
	}

}
