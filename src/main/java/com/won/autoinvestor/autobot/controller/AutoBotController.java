package com.won.autoinvestor.autobot.controller;

import com.won.autoinvestor.autobot.service.AutoBotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AutoBotController {

    private final AutoBotService service;

    public AutoBotController(AutoBotService service) {
        this.service = service;
    }

    @GetMapping("/hello")
    public String hello() {
        return service.getHelloMessage();
    }
}
