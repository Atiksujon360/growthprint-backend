# GrowthPrint Backend

Spring Boot backend for the GrowthPrint business management platform.

## Prerequisites

- Java 17 or later (project targets Java 17; Spring Boot 4.1.1 supports Java 17-26)
- Maven Wrapper (`mvnw.cmd` on Windows or `./mvnw` on Unix-like systems)
- PostgreSQL for database-backed application startup

## Run locally

The application automatically loads environment variables from a `.env` file in the project root using spring-dotenv (compatible with Spring Boot 4.1.1).

### Setup

1. Copy `.env.example` to `.env`:
   ```powershell
   copy .env.example .env
   ```

2. Edit `.env` with your Render PostgreSQL external connection details:
   ```env
   DATABASE_URL=jdbc:postgresql://YOUR_EXTERNAL_HOST:5432/growthprint?sslmode=require
   DATABASE_USERNAME=growthprint_user
   DATABASE_PASSWORD=your_actual_password
   ```

   **Important**: Replace `YOUR_EXTERNAL_HOST` with the complete hostname from Render's External Database URL. The JDBC URL must start with `jdbc:postgresql://`. Never embed credentials inside the URL.

### Running the application

**From IntelliJ IDEA:**
- The application will automatically load `.env` when started from IntelliJ
- No additional Run Configuration setup is required
- Simply run the main class `GrowthprintBackendApplication`

**From Maven wrapper:**
```powershell
.\mvnw.cmd spring-boot:run
```

The `.env` file is automatically loaded by spring-dotenv at startup.

### Required environment variables

- `DATABASE_URL` - JDBC URL for PostgreSQL connection (e.g., `jdbc:postgresql://host:5432/dbname?sslmode=require`)
- `DATABASE_USERNAME` - Database username
- `DATABASE_PASSWORD` - Database password

### URL format for Render

When connecting to Render PostgreSQL from your local computer:
- Use the **external** hostname from Render's Connect tab
- The URL must start with `jdbc:postgresql://`
- Include `sslmode=require` for secure connections
- Keep username and password separate from the URL

Example:
```
jdbc:postgresql://dpg-xxxxx.singapore-postgres.render.com:5432/growthprint?sslmode=require
```

The application will automatically normalize URLs starting with `postgres://` or `postgresql://` to `jdbc:postgresql://` and add SSL mode for Render connections.

**Never commit `.env` files or credentials.** Flyway migrations are loaded from `classpath:db/migration` when enabled.

## Inquiry and lead vertical slice

The frontend inquiry contract is supported by:

- `POST /api/v1/public/inquiries` — validates and persists a lead, returning `201 Created` with `referenceNumber`, `status`, and `receivedAt`.
- `GET /api/v1/leads` — returns leads newest first only when `APP_ADMIN_ENDPOINTS_ENABLED=true` (development-only; no authentication or authorization is implemented yet).

The admin endpoint must not be enabled on a public deployment. It exposes lead contact details and is protected only by configuration until real server-side authentication and authorization are implemented.

### Applying the lead migration

Flyway remains disabled by default in `application.yml` because the current local database workflow requires an explicit, controlled migration step. Before testing persistence against the configured PostgreSQL database:

1. Confirm `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` are supplied through the local environment or `.env`; never place them in source control.
2. Run the application once with `SPRING_FLYWAY_ENABLED=true`.
3. Confirm the startup logs report successful Flyway migrations and verify `flyway_schema_history` contains versions `1` and `2`, and that `leads` exists.
4. Stop the application, then use the normal configuration only after deciding whether Flyway should remain enabled for the deployment. For a deployed service, migrations should run as part of a controlled release process rather than relying on an ad-hoc local run.

Until V2 has actually been applied and verified against the target database, persistence has not been verified by this repository's ordinary tests.

## API Documentation (Swagger UI)

Swagger UI is available for exploring and testing the API in local development:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

### Enabling/Disabling Swagger

Swagger is enabled by default in local development. To control Swagger availability:

- **Local development**: Set `SPRINGDOC_ENABLED=true` (default) or omit the variable
- **Production**: Set `SPRINGDOC_ENABLED=false` in the Render dashboard environment variables to disable Swagger UI and API docs

### Authentication

Authentication is not yet implemented. Swagger is currently for testing only. Once authentication is introduced, protected endpoints will require real authentication tokens.

### Troubleshooting

- If Swagger UI doesn't load, ensure the application started successfully and check the console logs
- If you see 404 errors, verify `SPRINGDOC_ENABLED=true` is set for local development
- For production deployments, always set `SPRINGDOC_ENABLED=false` to prevent public API documentation exposure

## Verify

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

## Current status

The project foundation is configured for:
- Spring Boot 4.1.1 with Spring Web MVC
- Spring Data JPA
- Jakarta Bean Validation
- PostgreSQL JDBC driver
- Flyway database migrations with PostgreSQL support
- Spring Boot Actuator
- Swagger UI (Springdoc OpenAPI 3.1.1)
- Automated testing

No GrowthPrint business domain or authentication implementation has been added yet.

The context smoke test intentionally excludes database auto-configuration because this repository does not yet contain database credentials or migrations. It verifies the application foundation without implying that PostgreSQL connectivity is configured.

## Known limitations and next step

- PostgreSQL connectivity is configured but not yet verified with a live database.
- A minimal Flyway migration exists (V1__initial_schema.sql) but has not been executed.
- Authentication is intentionally not implemented; the planned direction is managed OIDC with a deliberate server-side session or backend-for-frontend design.

## Database connection verification

To verify the database connection:

1. **Create a PostgreSQL database** on Render following the instructions in the project documentation
2. **Obtain connection details** from the Render Dashboard's Connect tab
3. **Set environment variables** in `.env` for local development:
   - `DATABASE_URL` - JDBC URL (e.g., `jdbc:postgresql://external-host:5432/growthprint?sslmode=require`)
   - `DATABASE_USERNAME` - Database user
   - `DATABASE_PASSWORD` - Database password
4. **Run the application**:
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
5. **Verify Flyway execution** - Check logs for "Flyway Community Edition" and migration success messages
6. **Check health endpoint** - Access `http://localhost:8080/actuator/health` to confirm database connectivity

**Note**: The existing test excludes database auto-configuration, so tests run without requiring a database connection. This allows development to continue before database provisioning.

## Render deployment

For production deployment on Render:

1. **Set environment variables in the Render dashboard** (not in .env):
   - `DATABASE_URL` - Use the **internal** connection URL from Render's Connect tab for services deployed on Render
   - `DATABASE_USERNAME` - Database user
   - `DATABASE_PASSWORD` - Database password
   - `SPRINGDOC_ENABLED=false` - Disable Swagger UI in production

2. **Deploy** using Render's build process. The application will read environment variables from Render's environment, not from a .env file.

3. **Internal vs External URLs**:
   - Use **internal** URLs when the backend is deployed on Render (same region, private network)
   - Use **external** URLs for local development from your computer

Authentication and authorization, rate limiting, idempotency, spam prevention, and pagination remain follow-up work for production hardening.
