# Data Ingestion and Query Platform

Microservices platform: fetches news articles from NewsAPI, stores in PostgreSQL, provides REST API with ChatGPT summaries.

**Services:**
- `data-ingestion-service` (8081): Fetches articles from NewsAPI, stores to PostgreSQL
- `query-service` (8082): Generates summaries via OpenAI, Redis caching

**Features:** Scheduled ingestion (6h), circuit breaker/retry, rate limiting, Redis cache (24h TTL)

## Tech Stack

Java 21, Spring Boot 3.5.9, PostgreSQL 16, Redis 7, Liquibase, Resilience4j

## Prerequisites

- JDK 21, Maven 3.9+, Docker & Docker Compose
- API Keys: [NewsAPI](https://newsapi.org/register), [OpenAI](https://platform.openai.com/api-keys)

## Quick Start

```bash
# Set environment variables
export NEWS_API_KEY=your_key
export OPENAI_API_KEY=your_key
export INTERNAL_API_TOKEN=your_token

# Start all services
docker compose up -d --build

# Check health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

**Services:**
- Data Ingestion: http://localhost:8081
- Query Service: http://localhost:8082
- Swagger UI: http://localhost:8081/swagger-ui.html

**Docker commands:**
```bash
docker compose up -d --build    # Start
docker compose down              # Stop
docker compose logs -f <service> # Logs
docker compose ps                # Status
```

**Local run (requires PostgreSQL & Redis):**
```bash
mvn clean install
mvn spring-boot:run -pl data-ingestion-service
mvn spring-boot:run -pl query-service
```

## API Examples

```bash
# Get all articles
curl http://localhost:8081/api/articles

# Search with filters
curl "http://localhost:8081/api/articles?category=technology&sort=publishedAt,desc"

# Get article by UUID
curl http://localhost:8081/api/articles/550e8400-e29b-41d4-a716-446655440000

# Get summary
curl http://localhost:8081/api/articles/550e8400-e29b-41d4-a716-446655440000/summary
```

**Swagger UI:** http://localhost:8081/swagger-ui.html

## Environment Variables

**Required:**
- `INTERNAL_API_TOKEN` - Service-to-service auth token (required, else 500 error)

**Recommended:**
- `NEWS_API_KEY` - NewsAPI key (required for ingestion, else startup fails)
- `OPENAI_API_KEY` - OpenAI key (optional, returns mock summaries if missing)

**Optional (defaults in docker-compose.yml):**
- `OPENAI_MODEL` - Model name (default: `gpt-4o-mini` Docker, `gpt-3.5-turbo` local)
- `OPENAI_TIMEOUT` - Timeout seconds (default: `60`)
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` - PostgreSQL config
- `REDIS_HOST`, `REDIS_PORT` - Redis config
- `QUERY_SERVICE_URL` - Query service URL
- `RATE_LIMITER_*` - Rate limiter settings

See `docker-compose.yml` and `application.yaml` for full list.

## Testing

```bash
mvn test                    # All tests
mvn test -pl data-ingestion-service
mvn test -pl query-service
```

## Troubleshooting

**Services won't start:**
```bash
docker compose logs -f
docker compose ps
```

**Database/Redis errors:**
```bash
docker compose exec postgres psql -U postgres -d ingestion_db
docker compose exec redis redis-cli ping
```

**API key errors:**
```bash
docker compose config  # Verify env vars
docker compose logs -f <service>
```

**Health checks:**
```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```
