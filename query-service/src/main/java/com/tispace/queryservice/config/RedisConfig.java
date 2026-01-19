package com.tispace.queryservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisConfig {
	
	@Value("${spring.data.redis.host:localhost}")
	private String host;
	
	@Value("${spring.data.redis.port:6379}")
	private int port;
	
	@Value("${spring.data.redis.timeout:2000}")
	private long connectionTimeoutMs;
	
	@Value("${spring.data.redis.command-timeout:1000}")
	private long commandTimeoutMs;
	
	@Bean
	public RedisConnectionFactory redisConnectionFactory() {
		RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
		config.setHostName(host);
		config.setPort(port);

		SocketOptions socketOptions = SocketOptions.builder()
			.connectTimeout(Duration.ofMillis(connectionTimeoutMs))
			.build();

		TimeoutOptions timeoutOptions = TimeoutOptions.builder()
			.fixedTimeout(Duration.ofMillis(commandTimeoutMs))
			.build();
		
		ClientOptions clientOptions = ClientOptions.builder()
			.socketOptions(socketOptions)
			.timeoutOptions(timeoutOptions)
			.build();
		
		LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
			.clientOptions(clientOptions)
			.commandTimeout(Duration.ofMillis(commandTimeoutMs))
			.build();
		
		return new LettuceConnectionFactory(config, clientConfig);
	}
	
	@Bean
	public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, String> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(new StringRedisSerializer());
		template.setHashKeySerializer(new StringRedisSerializer());
		template.setHashValueSerializer(new StringRedisSerializer());
		template.afterPropertiesSet();
		return template;
	}
	
	@Bean
	public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
		return new StringRedisTemplate(connectionFactory);
	}
	
	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return mapper;
	}
}
