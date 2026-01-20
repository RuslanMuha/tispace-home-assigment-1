package com.tispace.dataingestion.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI dataIngestionServiceOpenAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("Data Ingestion Service API")
				.description("API for ingesting and querying news articles from external sources")
				.version("v1.0")
				.contact(new Contact()
					.name("TISpace")
					.email("support@tispace.com"))
				.license(new License()
					.name("Apache 2.0")
					.url("https://www.apache.org/licenses/LICENSE-2.0.html")));
	}
}


