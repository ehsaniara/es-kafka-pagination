package com.ehsaniara.espagination.controller;

import com.ehsaniara.espagination.service.KafkaService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Jay Ehsaniara
 * https://github.com/ehsaniara
 */
@AllArgsConstructor
@RestController
@RequestMapping("/test")
public class TestController {

    private final KafkaService kafkaService;

    @GetMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void producer() {
        kafkaService.paginationProcess();
    }

    @GetMapping("init")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void init() {
        kafkaService.initEs();
    }
}
