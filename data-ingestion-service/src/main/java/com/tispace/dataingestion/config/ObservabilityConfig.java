package com.tispace.dataingestion.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for observability metrics.
 */
@Configuration
@Slf4j
public class ObservabilityConfig {
	
	@Bean
	public Counter newsApiClientRequests(MeterRegistry registry) {
		return Counter.builder("newsapi.requests")
			.description("Number of NewsAPI requests")
			.tag("client", "newsapi")
			.register(registry);
	}
	
	@Bean
	public Counter newsApiClientErrors(MeterRegistry registry) {
		return Counter.builder("newsapi.errors")
			.description("Number of NewsAPI errors")
			.tag("client", "newsapi")
			.register(registry);
	}
	
	@Bean
	public Timer newsApiClientLatency(MeterRegistry registry) {
		return Timer.builder("newsapi.latency")
			.description("NewsAPI request latency")
			.tag("client", "newsapi")
			.register(registry);
	}
	
	@Bean
	public Counter articleControllerRequests(MeterRegistry registry) {
		return Counter.builder("article.controller.requests")
			.description("Number of article controller requests")
			.tag("controller", "article")
			.register(registry);
	}
	
	@Bean
	public Counter articleControllerErrors(MeterRegistry registry) {
		return Counter.builder("article.controller.errors")
			.description("Number of article controller errors")
			.tag("controller", "article")
			.register(registry);
	}
	
	@Bean
	public Timer articleControllerLatency(MeterRegistry registry) {
		return Timer.builder("article.controller.latency")
			.description("Article controller request latency")
			.tag("controller", "article")
			.register(registry);
	}
}

