# Escruta - Core

_"Think, ask, learn"_

**Escruta Core** is the core engine of the Escruta research assistant platform. Built with Java and Spring Boot, it
handles the business logic, document processing, AI orchestration, and persistent storage for your research data.

## Getting Started

### Prerequisites

- Java Development Kit (JDK) 21 or higher.
- PostgreSQL (version 16 or higher).
- Qdrant (for vector search).
- An OpenAI-compatible API.
- Escruta Extractor service running (see [Escruta Extractor](https://github.com/escruta/extractor)).

> [!TIP]
> You can use Docker to quickly spin up both PostgreSQL and Qdrant:
> ```bash
> docker run -d --name escruta-db -p 5432:5432 -e POSTGRES_PASSWORD=1234 -e POSTGRES_DB=escruta postgres
> docker run -d --name escruta-qdrant -p 6333:6333 -p 6334:6334 qdrant/qdrant
> ```

### Installation

- `./gradlew bootRun` - Start the development server

The backend service will be available at [localhost:8080](http://localhost:8080) by default.

## Configuration

### Environment Variables

The application can be configured using environment variables. These can be set in your shell or passed to the
application at runtime.

| Variable                           | Description                          | Default                                    |
|------------------------------------|--------------------------------------|--------------------------------------------|
| `ESCRUTA_PORT`                     | Backend port                         | `8080`                                     |
| `ESCRUTA_DATABASE_URL`             | JDBC URL for the database            | `jdbc:postgresql://localhost:5432/escruta` |
| `ESCRUTA_DATABASE_USER`            | Database username                    | `postgres`                                 |
| `ESCRUTA_DATABASE_PASSWORD`        | Database password                    | `1234`                                     |
| `ESCRUTA_AI_BASE_URL`              | Base URL for the AI provider         | (Required)                                 |
| `ESCRUTA_AI_API_KEY`               | API Key for the AI provider          | (Required)                                 |
| `ESCRUTA_AI_MODEL`                 | AI model to use for chat             | (Required)                                 |
| `ESCRUTA_AI_CHAT_COMPLETIONS_PATH` | Path for chat completions endpoint   | `/v1/chat/completions`                     |
| `ESCRUTA_AI_EMBEDDING_MODEL`       | AI model to use for embeddings       | (Required)                                 |
| `ESCRUTA_AI_EMBEDDING_DIMENSIONS`  | Dimensions of the embedding vectors  | `768`                                      |
| `ESCRUTA_AI_EMBEDDING_PATH`        | Path for embeddings endpoint         | `/v1/embeddings`                           |
| `ESCRUTA_AI_EMBEDDING_BASE_URL`    | Base URL for embeddings (if differs) | `ESCRUTA_AI_BASE_URL`                      |
| `ESCRUTA_AI_EMBEDDING_API_KEY`     | API Key for embeddings (if differs)  | `ESCRUTA_AI_API_KEY`                       |
| `ESCRUTA_QDRANT_HOST`              | Qdrant database host                 | `localhost`                                |
| `ESCRUTA_QDRANT_PORT`              | Qdrant database port                 | `6334`                                     |
| `ESCRUTA_QDRANT_API_KEY`           | API Key for Qdrant (if required)     |                                            |
| `ESCRUTA_QDRANT_COLLECTION`        | Qdrant collection name               | `escruta`                                  |
| `ESCRUTA_CORS_ALLOWED_ORIGINS`     | Allowed origins for CORS             | `http://localhost:5173`                    |
| `ESCRUTA_EXTRACTOR_URL`            | Extractor service URL                | `http://localhost:8000`                    |
| `ESCRUTA_EXTRACTOR_API_KEY`        | Internal API Key for the Extractor   | (Required)                                 |

See [application.yml](./src/main/resources/application.yml) for the full list of configuration options.

### Development Scripts

```bash
./gradlew bootRun       # Start development server
./gradlew build         # Build the application
./gradlew clean         # Clean the build directory
```

## Database Migrations

This project uses [Flyway](https://www.red-gate.com/products/flyway/community/) for database migrations to ensure the
database schema stays in sync with the application code.

When starting the application (e.g., `./gradlew bootRun`), Flyway will automatically apply any pending migrations to the
database.

### Managing Migrations via CLI

You can use the Flyway Gradle plugin to manage the database schema manually. The database credentials will be picked up
from your environment variables (`ESCRUTA_DATABASE_URL`, `ESCRUTA_DATABASE_USER`, `ESCRUTA_DATABASE_PASSWORD`).

```bash
./gradlew flywayInfo        # View migration status
./gradlew flywayMigrate     # Apply pending migrations
./gradlew flywayRepair      # Repair the schema history table
```

Migration scripts are located in `src/main/resources/db/migration/`. All new schema changes should be added as `.sql`
scripts in this directory following the Flyway naming convention (e.g., `V2__add_new_table.sql`).

## Testing

Tests run against a dedicated PostgreSQL database (`escruta_test`) to ensure consistency with production. Jacoco
generates coverage reports automatically after test execution.

### Prerequisites

Ensure you have a PostgreSQL database named `escruta_test`:

```bash
psql -U postgres -c "CREATE DATABASE escruta_test;"
```

### Running Tests

```bash
./gradlew test                                  # Run all tests
./gradlew test --tests "NotebookServiceTest"    # Run specific test class
./gradlew test --tests "*ControllerTest"        # Run all controller tests
```

After running tests, find the coverage report at `build/reports/jacoco/test/html/index.html`.

See [application-test.yml](./src/test/resources/application-test.yml) for the test database configuration.

### Test Organization

Tests are organized by layer:

- `controllers/` - HTTP endpoint tests with mocked security
- `services/` - Business logic unit tests
- `integration/` - End-to-end user journey tests

## Technology Stack

- **Runtime**: Java 21 with Gradle for build management.
- **Framework**: Spring Boot 3.5 for robust backend development.
- **AI Integration**: Spring AI for seamless interaction with Large Language Models.
- **Database**: PostgreSQL for relational data and Qdrant for vector similarity search.
- **Security**: Spring Security (OAuth 2.0 Resource Server Opaque Token) for stateless authentication.
- **Object Mapping**: Project Lombok to reduce boilerplate code.
