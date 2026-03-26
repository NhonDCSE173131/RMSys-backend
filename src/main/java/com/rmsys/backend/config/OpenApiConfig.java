package com.rmsys.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Manufacturing Monitor API")
                .version("1.0")
                .description("Backend API for Manufacturing Monitor System - Real-time machine monitoring, OEE, Energy, Alarms, Tools, Maintenance"));
    }
}

