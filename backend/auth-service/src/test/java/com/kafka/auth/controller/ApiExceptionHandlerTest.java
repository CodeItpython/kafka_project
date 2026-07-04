package com.kafka.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kafka.auth.storage.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NotFoundController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void objectNotFoundIsMappedToHttp404() throws Exception {
        mockMvc.perform(get("/test/object"))
                .andExpect(status().isNotFound());
    }

    @RestController
    static class NotFoundController {
        @GetMapping("/test/object")
        String load() {
            throw new ObjectNotFoundException("파일을 찾을 수 없습니다.");
        }
    }
}
