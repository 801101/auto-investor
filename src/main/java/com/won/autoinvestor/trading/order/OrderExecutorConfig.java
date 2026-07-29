package com.won.autoinvestor.trading.order;

import com.won.autoinvestor.broker.BrokerClient;
import com.won.autoinvestor.pilot.mapper.PilotMapper;
import com.won.autoinvestor.trading.config.InvestmentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderExecutorConfig {

    private static final Logger logger = LoggerFactory.getLogger(OrderExecutorConfig.class);

    @Bean
    public OrderExecutor orderExecutor(InvestmentProperties investmentProperties,
                                       BrokerClient brokerClient,
                                       PilotMapper pilotMapper) {
        logger.info("live trading enabled: {}", investmentProperties.isLiveTradingEnabled());
        if (investmentProperties.isLiveTradingEnabled()) {
            return new LiveOrderExecutor(brokerClient, pilotMapper);
        }
        return new DryRunOrderExecutor(pilotMapper);
    }
}
