# Escruta - Core

This is the core engine of the Escruta research assistant platform. Built with Java and Spring Boot, it handles the
business logic, document processing, AI orchestration, and persistent storage for your research data.

Built with Java 25, Spring Boot 4.1, Spring AI, MariaDB, Qdrant, and Lombok.

## Getting Started

### Prerequisites

- Java Development Kit (JDK) 25.
- Docker (for MariaDB, Qdrant, and Redis).
- An OpenAI-compatible API.
- Escruta Extractor service running (see [Escruta Extractor](https://github.com/escruta/extractor)).
- Escruta Search service running (see [Escruta Search](https://github.com/escruta/search)).

> [!NOTE]
> Spin up all infrastructure dependencies with a single command:
> ```bash
> docker compose up -d
> ```

> [!TIP]
> Gradle's toolchain support will auto-provision JDK 25 if not present on your host.

### Installation

- `./gradlew bootRun` - Start the development server.

The backend service will be available at [localhost:8080](http://localhost:8080) by default.

## Configuration

### Environment Variables

The application can be configured using environment variables. These can be set in your shell or passed to the
application at runtime.

| Variable                          | Description                          | Default                                 |
|-----------------------------------|--------------------------------------|-----------------------------------------|
| `ESCRUTA_PORT`                    | Backend port                         | `8080`                                  |
| `ESCRUTA_DB_URL`                  | JDBC URL for the database            | `jdbc:mariadb://localhost:3306/escruta` |
| `ESCRUTA_DB_USER`                 | Database username                    | `root`                                  |
| `ESCRUTA_DB_PASSWORD`             | Database password                    | `1234`                                  |
| `ESCRUTA_KV_HOST`                 | Redis database host                  | `localhost`                             |
| `ESCRUTA_KV_PORT`                 | Redis database port                  | `6379`                                  |
| `ESCRUTA_KV_PASSWORD`             | Redis database password              |                                         |
| `ESCRUTA_AI_BASE_URL`             | Base URL for the AI provider         | (Required)                              |
| `ESCRUTA_AI_API_KEY`              | API Key for the AI provider          | (Required)                              |
| `ESCRUTA_AI_MODEL`                | AI model to use for chat             | (Required)                              |
| `ESCRUTA_AI_EMBEDDING_MODEL`      | AI model to use for embeddings       | (Required)                              |
| `ESCRUTA_AI_EMBEDDING_DIMENSIONS` | Dimensions of the embedding vectors  | `768`                                   |
| `ESCRUTA_AI_EMBEDDING_BASE_URL`   | Base URL for embeddings (if differs) | `ESCRUTA_AI_BASE_URL`                   |
| `ESCRUTA_AI_EMBEDDING_API_KEY`    | API Key for embeddings (if differs)  | `ESCRUTA_AI_API_KEY`                    |
| `ESCRUTA_VDB_HOST`                | Qdrant database host                 | `localhost`                             |
| `ESCRUTA_VDB_PORT`                | Qdrant database port                 | `6334`                                  |
| `ESCRUTA_VDB_API_KEY`             | API Key for Qdrant (if required)     |                                         |
| `ESCRUTA_VDB_COLLECTION`          | Qdrant collection name               | `escruta`                               |
| `ESCRUTA_CORS_ALLOWED_ORIGINS`    | Allowed origins for CORS             | `http://localhost:5173`                 |
| `ESCRUTA_EXTRACTOR_URL`           | Extractor service URL                | `http://localhost:8000`                 |
| `ESCRUTA_EXTRACTOR_API_KEY`       | Internal API Key for the Extractor   | (Required)                              |
| `ESCRUTA_SEARCH_URL`              | Search service URL                   | `http://localhost:8001`                 |
| `ESCRUTA_SEARCH_API_KEY`          | Internal API Key for the Search      | (Required)                              |

See [application.yml](./src/main/resources/application.yml) for the full list of configuration options.

### Development Scripts

```bash
./gradlew bootRun       # Start development server
./gradlew build         # Build the application
./gradlew clean         # Clean the build directory
./gradlew bootJar       # Build the production JAR (uses layered jar format)
```

## Database Migrations

This project uses [Flyway](https://www.red-gate.com/products/flyway/community/) for database migrations to ensure the
database schema stays in sync with the application code.

When starting the application (e.g., `./gradlew bootRun`), Flyway will automatically apply any pending migrations to the
database.

### Managing Migrations

You can use the Flyway Gradle plugin to manage the database schema manually. The database credentials will be picked up
from your environment variables (`ESCRUTA_DB_URL`, `ESCRUTA_DB_USER`, `ESCRUTA_DB_PASSWORD`).

```bash
./gradlew flywayInfo        # View migration status
./gradlew flywayMigrate     # Apply pending migrations
./gradlew flywayRepair      # Repair the schema history table
```

Migration scripts are located in `src/main/resources/db/migration/`. All new schema changes should be added as `.sql`
scripts in this directory following the Flyway naming convention (e.g., `V1__initial_schema.sql`).

## Testing

Tests run against a dedicated MariaDB database (`escruta_test`) to ensure consistency with production. Jacoco
generates coverage reports automatically after test execution.

### Prerequisites

Ensure you have a MariaDB database named `escruta_test`:

```bash
mariadb -u root -p1234 -e "CREATE DATABASE escruta_test;"
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

- `controllers/` - HTTP endpoint tests with mocked security.
- `services/` - Business logic unit tests.
- `integration/` - End-to-end user journey tests.
