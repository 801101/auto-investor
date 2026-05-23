package com.won.autoinvestor.autobot.service;

import com.won.autoinvestor.autobot.mapper.AutoBotMapper;
import com.won.autoinvestor.common.exception.BizException;
import com.won.autoinvestor.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AutoBotService {

    private static final Logger logger = LoggerFactory.getLogger(AutoBotService.class);
    private final AutoBotMapper mapper;

    public AutoBotService(AutoBotMapper mapper) {
        this.mapper = mapper;
    }

    public String getHelloMessage() {
        List<Map<String, String>> list = mapper.selectHelloMessage();

        if (list == null || list.isEmpty()) {
            logger.warn("hello message not found");
            throw new BizException(ErrorCode.NO_DATA);
        }

        String message = list.getFirst().get("message");
        logger.info("getHelloMessage success: {}", message);

        return message;
    }
}
