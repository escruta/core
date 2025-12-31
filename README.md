# Escruta - Core

_"Think, ask, learn"_

**Escruta Core** is the core engine of the Escruta research assistant platform. Built with Java and Spring Boot, it
handles the business logic, document processing, AI orchestration, and persistent storage for your research data.

> [!IMPORTANT]
> This backend service is a central part of the Escruta ecosystem. It requires a PostgreSQL database with the `pgvector`
> extension and access to an OpenAI-compatible API to function correctly.

## Technology Stack

- **Runtime**: Java 21 with Gradle for build management.
- **Framework**: Spring Boot 3.5 for robust backend development.
- **AI Integration**: Spring AI for seamless interaction with Large Language Models.
- **Database**: PostgreSQL with `pgvector` for search and vector similarity.
- **Security**: Spring Security (OAuth 2.0 Resource Server Opaque Token) for stateless authentication.
- **Object Mapping**: Project Lombok to reduce boilerplate code.

## Getting Started

### Prerequisites

- Java Development Kit (JDK) 21 or higher.
- PostgreSQL (version 15 or higher) with `pgvector` extension.
- An OpenAI-compatible API.

### Installation

- `./gradlew bootRun` - Start the development server

The backend service will be available at [localhost:8080](http://localhost:8080) by default.

### Development Scripts

- `./gradlew bootRun` - Start development server
- `./gradlew build` - Build the application
- `./gradlew clean` - Clean the build directory

## Configuration

### Environment Variables

The application can be configured using environment variables. These can be set in your shell or passed to the
application at runtime.

| Variable                          | Description                         | Default                                                                |
|-----------------------------------|-------------------------------------|------------------------------------------------------------------------|
| `ESCRUTA_PORT`                    | Backend port                        | `8080`                                                                 |
| `ESCRUTA_DATABASE_URL`            | JDBC URL for the database           | `jdbc:postgresql://localhost:5432/escruta?user=postgres&password=1234` |
| `ESCRUTA_AI_BASE_URL`             | Base URL for the AI provider        | (Required)                                                             |
| `ESCRUTA_AI_API_KEY`              | API Key for the AI provider         | (Required)                                                             |
| `ESCRUTA_AI_MODEL`                | AI model to use for chat            | (Required)                                                             |
| `ESCRUTA_AI_EMBEDDING_MODEL`      | AI model to use for embeddings      | (Required)                                                             |
| `ESCRUTA_AI_EMBEDDING_DIMENSIONS` | Dimensions of the embedding vectors | `768`                                                                  |
| `ESCRUTA_CORS_ALLOWED_ORIGINS`    | Allowed origins for CORS            | `http://localhost:5173`                                                |

See [application.yml](./src/main/resources/application.yml) for the full list of configuration options.
