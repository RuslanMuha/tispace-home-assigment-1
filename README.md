# Data Ingestion and Query Platform

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![Redis](https://img.shields.io/badge/Redis-7-red)
![Docker](https://img.shields.io/badge/Docker-Compose-blue)

A microservices platform that gets news articles from external APIs, saves them to a database, and provides REST API with ChatGPT summaries.

## Table of Contents

- [Project Overview](#project-overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [Run Locally](#run-locally)
- [Run with Docker](#run-with-docker)
- [API Examples](#api-examples)
- [Services & Ports](#services--ports)
- [Environment Variables](#environment-variables)
- [Documentation](#documentation)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)
- [Quick Health Check](#quick-health-check)

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

## Architecture

```
┌─────────────────┐         ┌──────────────────┐
│  NewsAPI        │         │  OpenAI API      │
│  (External)     │         │  (External)      │
└────────┬────────┘         └────────┬─────────┘
         │                          │
         │                          │
┌────────▼──────────────────────────▼────────┐
│   data-ingestion-service (8081)            │
│   - Fetches articles                       │
│   - Stores to PostgreSQL                   │
└────────┬───────────────────────────────────┘
         │
         │ Internal API
         │ (with auth)
         │
┌────────▼───────────────────────────────────┐
│   query-service (8082)                      │
│   - REST API for articles                   │
│   - Generates summaries                     │
│   - Redis cache                            │
└────────────────────────────────────────────┘
         │
         │
┌────────▼────────┐    ┌──────────────┐
│  PostgreSQL     │    │    Redis     │
│  (Database)     │    │    (Cache)   │
└─────────────────┘    └──────────────┘
```

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

## Prerequisites

Install the following tools:

- **JDK 21** - [Download](https://www.oracle.com/java/technologies/downloads/#java21) or [OpenJDK](https://adoptium.net/)
- **Maven 3.9+** - [Download](https://maven.apache.org/download.cgi) | [Install Guide](https://maven.apache.org/install.html)
- **Docker** - [Download Docker Desktop](https://www.docker.com/products/docker-desktop/) | [Install Guide](https://docs.docker.com/get-docker/)
- **Docker Compose** - Usually included with Docker Desktop | [Standalone Install](https://docs.docker.com/compose/install/)

**API Keys:**
- **NewsAPI** - [Get free API key](https://newsapi.org/register)
- **OpenAI** - [Get API key](https://platform.openai.com/api-keys)

## Quick Start

**Fastest way to run the project:**

```bash
# 1. Clone repository
git clone <repository-url>
cd ingestion-service

# 2. Set environment variables (create .env file or export)
export NEWS_API_KEY=your_key
export OPENAI_API_KEY=your_key
export INTERNAL_API_TOKEN=your_token

# 3. Start all services
docker compose up -d --build

# 4. Check services are running
docker compose ps
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

**Services will be available at:**
- Data Ingestion Service: http://localhost:8081
- Query Service: http://localhost:8082
- Swagger UI: http://localhost:8081/swagger-ui.html

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

## API Examples

**Get all articles:**
```bash
curl http://localhost:8081/api/articles
```

**Search articles with filters:**
```bash
curl "http://localhost:8081/api/articles?category=technology&sort=publishedAt,desc"
```

**Get article by ID (UUID):**
```bash
curl http://localhost:8081/api/articles/550e8400-e29b-41d4-a716-446655440000
```

**Get article summary:**
```bash
curl http://localhost:8081/api/articles/550e8400-e29b-41d4-a716-446655440000/summary
```

**Note:** Article ID is a UUID, not a number. Get actual IDs from the articles list endpoint.

**Try in Swagger UI:** http://localhost:8081/swagger-ui.html

## Services & Ports

| Service | Port | Health Check | Documentation |
|---------|------|--------------|---------------|
| data-ingestion-service | 8081 | `/actuator/health` | `/swagger-ui.html` |
| query-service | 8082 | `/actuator/health` | Internal only |
| PostgreSQL | 5432 | - | - |
| Redis | 6379 | - | - |

## Environment Variables

### Required Variables

These variables must be set for the services to work:

- `INTERNAL_API_TOKEN` - Token for service-to-service authentication
  - **What happens if not set:** Internal API endpoints will return 500 error "Internal API token is not configured". Service-to-service communication will fail.

### Optional Variables (with fallbacks)

These variables are optional but recommended:

- `NEWS_API_KEY` - NewsAPI API key
  - **What happens if not set:** Data ingestion service will not start (application startup fails with IllegalStateException). If set but invalid or API is unavailable, scheduled jobs will return empty results via fallback without errors.
  
- `OPENAI_API_KEY` - OpenAI API key
  - **What happens if not set:** Query service will return mock summaries instead of real ChatGPT summaries. Summary endpoints will work but return mock data.

### Optional Variables

These variables have default values and can be omitted:

**OpenAI Configuration:**
- `OPENAI_MODEL` - OpenAI model name
  - **Default:** `gpt-4o-mini` (Docker) or `gpt-3.5-turbo` (local)
  - **What happens if not set:** Uses default model

- `OPENAI_TIMEOUT` - OpenAI API timeout in seconds
  - **Default:** `60`
  - **What happens if not set:** Uses 60 seconds timeout

**Database Configuration (for local run):**
- `DB_HOST` - PostgreSQL host
  - **Default:** `localhost` (local) or `postgres` (Docker)
  - **What happens if not set:** Uses default host

- `DB_PORT` - PostgreSQL port
  - **Default:** `5432`
  - **What happens if not set:** Uses default port

- `DB_NAME` - Database name
  - **Default:** `ingestion_db`
  - **What happens if not set:** Uses default database name

- `DB_USER` - Database user
  - **Default:** `postgres`
  - **What happens if not set:** Uses default user

- `DB_PASSWORD` - Database password
  - **Default:** `postgres`
  - **What happens if not set:** Uses default password

**Redis Configuration (for local run):**
- `REDIS_HOST` - Redis host
  - **Default:** `localhost` (local) or `redis` (Docker)
  - **What happens if not set:** Uses default host

- `REDIS_PORT` - Redis port
  - **Default:** `6379`
  - **What happens if not set:** Uses default port

**Service Configuration:**
- `QUERY_SERVICE_URL` - Query service URL
  - **Default:** `http://query-service:8082` (Docker) or `http://localhost:8082` (local)
  - **What happens if not set:** Uses default URL

**Rate Limiter Configuration:**
- `RATE_LIMITER_LIMIT` - Rate limiter limit per period
  - **Default:** `10`
  - **What happens if not set:** Allows 10 requests per period

- `RATE_LIMITER_PERIOD` - Rate limiter period
  - **Default:** `1s`
  - **What happens if not set:** Uses 1 second period

- `RATE_LIMITER_TIMEOUT` - Rate limiter timeout
  - **Default:** `5s`
  - **What happens if not set:** Uses 5 seconds timeout

- `ARTICLE_RATE_LIMITER_LIMIT` - Article endpoint rate limiter limit
  - **Default:** `100`
  - **What happens if not set:** Allows 100 requests per period

- `ARTICLE_RATE_LIMITER_PERIOD` - Article endpoint rate limiter period
  - **Default:** `1m`
  - **What happens if not set:** Uses 1 minute period

- `ARTICLE_RATE_LIMITER_TIMEOUT` - Article endpoint rate limiter timeout
  - **Default:** `0`
  - **What happens if not set:** No timeout

For full list of variables, see `docker-compose.yml` and `application.yaml` files in services.

## Documentation

**Swagger UI** (data-ingestion-service only):
- http://localhost:8081/swagger-ui.html
- API docs: http://localhost:8081/v3/api-docs

Query-service does not provide public documentation (internal service).

## Testing

**Run all tests:**
```bash
mvn test
```

**Run tests for specific module:**
```bash
mvn test -pl data-ingestion-service
mvn test -pl query-service
```

## Troubleshooting

**Services won't start:**
```bash
# Check logs
docker compose logs -f

# Check if ports are available
netstat -an | grep 8081
netstat -an | grep 8082
```

**Database connection errors:**
- Ensure PostgreSQL container is healthy: `docker compose ps`
- Check DB credentials in `docker-compose.yml`
- Verify database is accessible: `docker compose exec postgres psql -U postgres -d ingestion_db`

**Redis connection errors:**
- Ensure Redis container is healthy: `docker compose ps`
- Check Redis connection: `docker compose exec redis redis-cli ping`

**API key errors:**
- Verify environment variables are set: `docker compose config`
- Check service logs for authentication errors
- Ensure API keys are valid and have proper permissions

**Service communication errors:**
- Check if INTERNAL_API_TOKEN is set correctly
- Verify both services are running: `docker compose ps`
- Check network connectivity: `docker compose exec data-ingestion-service ping query-service`

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
