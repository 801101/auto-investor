package com.won.autoinvestor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AutoInvestorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoInvestorApplication.class, args);
    }
}
