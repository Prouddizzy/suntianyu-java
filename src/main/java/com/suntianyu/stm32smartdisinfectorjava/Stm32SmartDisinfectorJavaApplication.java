package com.suntianyu.stm32smartdisinfectorjava;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Stm32SmartDisinfectorJavaApplication {

	public static void main(String[] args) {
		SpringApplication.run(Stm32SmartDisinfectorJavaApplication.class, args);
	}

}
