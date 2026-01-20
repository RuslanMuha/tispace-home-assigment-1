package com.tispace.dataingestion.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class QueryServiceRestTemplateConfig {

    @Bean
    public ClientHttpRequestInterceptor internalTokenInterceptor(
            @Value("${security.internal.header:X-Internal-Token}") String headerName,
            @Value("${query-service.internal-token:}") String token
    ) {
        return (request, body, execution) -> {
            if (StringUtils.hasText(token)) {
                request.getHeaders().set(headerName, token);
            }
            return execution.execute(request, body);
        };
    }

    @Bean
    public RestTemplate queryServiceRestTemplate(
            RestTemplateBuilder builder,
            ClientHttpRequestInterceptor internalTokenInterceptor,
            ClientHttpRequestFactory factory
    ) {
        return builder.additionalInterceptors(internalTokenInterceptor)
                .requestFactory(() -> factory)
                .build();
    }
}
