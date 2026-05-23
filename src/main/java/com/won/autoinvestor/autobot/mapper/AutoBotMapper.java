package com.won.autoinvestor.autobot.mapper;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface AutoBotMapper {

    List<Map<String, String>> selectHelloMessage();

}
