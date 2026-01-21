# Data Ingestion and Query Platform

A microservices platform that gets news articles from external APIs, saves them to a database, and provides REST API with ChatGPT summaries.

## Project Overview

The system has two microservices. **data-ingestion-service** gets articles from NewsAPI and saves them to PostgreSQL. **query-service** provides REST API for searching articles and generating summaries using OpenAI. Services communicate through an internal API with authentication.

**Main features:**
- Scheduled data fetching every 6 hours (configurable)
- NewsAPI integration for technology articles
- REST API for searching and filtering articles
- Summary generation using OpenAI ChatGPT
- Redis caching (24-hour TTL)
- Circuit breaker and retry for external APIs
- Rate limiting on public endpoints
- Structured logging with trace ID

## Tech Stack

- **Java**: 21
- **Spring Boot**: 3.5.9
- **Spring Cloud**: 2025.0.1
- **PostgreSQL**: 16-alpine
- **Redis**: 7-alpine
- **Liquibase**: Database migrations
- **Resilience4j**: Circuit breaker, retry, rate limiting
- **Maven**: Build tool
- **Docker & Docker Compose**: Containerization

## Project Structure

```
ingestion-service/
├── common-module/          # Shared entities, DTOs, exceptions, utilities
├── data-ingestion-service/ # Data ingestion service (port 8081)
│   ├── src/main/resources/
│   │   └── application.yaml
│   └── Dockerfile
├── query-service/          # Query service and ChatGPT (port 8082)
│   ├── src/main/resources/
│   │   └── application.yaml
│   └── Dockerfile
├── docker-compose.yml      # All services configuration
└── pom.xml                 # Parent POM for multi-module project
```

Configuration: `application.yaml` in each service, environment variables via `docker-compose.yml`.

## Prerequisites

- **JDK 21**
- **Maven 3.9+**
- **Docker** and **Docker Compose**
- **NewsAPI API key** (https://newsapi.org/)
- **OpenAI API key** (https://platform.openai.com/)

## Documentation

**Swagger UI** (data-ingestion-service only):
- http://localhost:8081/swagger-ui.html
- API docs: http://localhost:8081/v3/api-docs

Query-service does not provide public documentation (internal service).

## Run Locally

```bash
mvn clean install
mvn spring-boot:run -pl data-ingestion-service
mvn spring-boot:run -pl query-service
```

Requires PostgreSQL and Redis running locally.

## Run with Docker

**Start all services:**
```bash
docker compose up -d --build
```

**Stop services:**
```bash
docker compose down
```

**View logs:**
```bash
docker compose logs -f data-ingestion-service
docker compose logs -f query-service
docker compose logs -f postgres
docker compose logs -f redis
```

**Check status:**
```bash
docker compose ps
```

## Environment Variables

Main environment variables (see `docker-compose.yml`):

- `NEWS_API_KEY` - NewsAPI API key
- `OPENAI_API_KEY` - OpenAI API key
- `OPENAI_MODEL` - OpenAI model (default: `gpt-4o-mini`)
- `INTERNAL_API_TOKEN` - Token for service-to-service authentication

For full list of variables, see `docker-compose.yml` and `application.yaml` files in services.

## Quick Health Check

**Data Ingestion Service:**
```bash
curl http://localhost:8081/actuator/health
```

**Query Service:**
```bash
curl http://localhost:8082/actuator/health
```

**Readiness probes:**
- http://localhost:8081/actuator/health/readiness
- http://localhost:8082/actuator/health/readiness
