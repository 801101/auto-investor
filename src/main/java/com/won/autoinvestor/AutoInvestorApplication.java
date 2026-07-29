package com.won.autoinvestor;

import com.won.autoinvestor.kis.config.KisProperties;
import com.won.autoinvestor.trading.config.InvestmentProperties;
import com.won.autoinvestor.trading.config.MarketProperties;
import com.won.autoinvestor.trading.config.RuntimeProperties;
import com.won.autoinvestor.trading.config.SafetyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({
        InvestmentProperties.class,
        KisProperties.class,
        MarketProperties.class,
        SafetyProperties.class,
        RuntimeProperties.class
})
@SpringBootApplication
public class AutoInvestorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoInvestorApplication.class, args);
    }
}
