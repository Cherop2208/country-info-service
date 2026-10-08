package com.ncba.countryinfo.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "soap.country-info")
public record SoapProperties(String url, Duration connectTimeout, Duration readTimeout) {
}
