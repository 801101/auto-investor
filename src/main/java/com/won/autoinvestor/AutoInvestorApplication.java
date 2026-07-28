package com.won.autoinvestor;

import com.won.autoinvestor.kis.config.KisProperties;
import com.won.autoinvestor.trading.config.InvestmentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({InvestmentProperties.class, KisProperties.class})
@SpringBootApplication
public class AutoInvestorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoInvestorApplication.class, args);
    }
}
