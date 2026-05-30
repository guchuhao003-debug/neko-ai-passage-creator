package com.wenxi.nekoaipassage.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    /**
     * 健康检查接口
     *
     * @return
     */
    @GetMapping("/")
    public String healthCheck() {
        return "ok";
    }
}
