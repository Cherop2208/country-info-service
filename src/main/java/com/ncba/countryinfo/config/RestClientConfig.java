package com.ncba.countryinfo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP client used to talk to the SOAP endpoint, with explicit connect/read timeouts. */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient soapRestClient(SoapProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.connectTimeout().toMillis());
        factory.setReadTimeout((int) props.readTimeout().toMillis());
        return RestClient.builder().requestFactory(factory).build();
    }
}
