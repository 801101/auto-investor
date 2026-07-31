package com.won.autoinvestor.common.trade;

import com.won.autoinvestor.common.kis.BrokerClient;
import com.won.autoinvestor.pilot.PilotMapper;
import com.won.autoinvestor.common.config.RuntimeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderExecutorConfig {

    private static final Logger logger = LoggerFactory.getLogger(OrderExecutorConfig.class);

    @Bean
    public OrderExecutor orderExecutor(RuntimeProperties runtimeProperties,
                                       BrokerClient brokerClient,
                                       PilotMapper tradingMapper) {
        logger.info("trading enabled: {}", runtimeProperties.isTradingEnabled());
        if (runtimeProperties.isTradingEnabled()) {
            return new LiveOrderExecutor(brokerClient, tradingMapper);
        }
        return new DryRunOrderExecutor(tradingMapper);
    }
}
