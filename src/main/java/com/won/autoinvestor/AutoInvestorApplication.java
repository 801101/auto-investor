package com.won.autoinvestor;

import com.won.autoinvestor.common.kis.KisProperties;
import com.won.autoinvestor.common.config.InvestmentProperties;
import com.won.autoinvestor.common.config.RuntimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({
        InvestmentProperties.class,
        KisProperties.class,
        RuntimeProperties.class
})
@SpringBootApplication
public class AutoInvestorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoInvestorApplication.class, args);
    }
}
