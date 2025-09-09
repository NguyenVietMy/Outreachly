package com.outreachly.outreachly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class OutreachlyApplication {

	public static void main(String[] args) {
		SpringApplication.run(OutreachlyApplication.class, args);
	}

}
