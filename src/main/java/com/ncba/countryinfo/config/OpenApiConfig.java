package com.ncba.countryinfo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI countryInfoOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Country Info Service API")
                .version("1.0.0")
                .description("REST facade over the CountryInfoService SOAP API with MySQL persistence."));
    }
}
