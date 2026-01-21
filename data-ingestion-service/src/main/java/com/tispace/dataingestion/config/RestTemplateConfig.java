package com.tispace.dataingestion.config;

import com.tispace.dataingestion.constants.ApiConstants;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(ClientHttpRequestFactory factory, RestTemplateBuilder builder) {
        return builder
                .requestFactory(() -> factory)
                .build();
    }
	
	@Bean
	public ClientHttpRequestFactory clientHttpRequestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(ApiConstants.CONNECT_TIMEOUT_MS);
		factory.setReadTimeout(ApiConstants.READ_TIMEOUT_MS);
		return factory;
	}
}

