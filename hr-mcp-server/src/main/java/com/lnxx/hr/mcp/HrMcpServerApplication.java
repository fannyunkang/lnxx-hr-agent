package com.lnxx.hr.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class HrMcpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(HrMcpServerApplication.class, args);
    }

    @Bean
    ToolCallbackProvider hrTools(HrReadOnlyTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
