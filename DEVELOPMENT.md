# GrowthPrint Backend - Development Guide

## Prerequisites

**Required Software:**
- Java 17 or later (project targets Java 17; Spring Boot 4.1.1 supports Java 17-26)
- Maven Wrapper (`mvnw.cmd` on Windows or `./mvnw` on Unix-like systems)
- PostgreSQL for database-backed application startup
- Git for version control
- IntelliJ IDEA (recommended for Java/Spring Boot development)

**Optional Software:**
- Docker Desktop (for integration testing)
- Docker Engine (for integration testing)

## Setup Instructions

### 1. Clone Repository

```bash
git clone <repository-url>
cd growthprint-backend/growthprint-backend
```

### 2. Configure Environment Variables

**Option A: Using .env file (development)**

1. Copy `.env.example` to `.env`:
   ```powershell
   copy .env.example .env
   ```

2. Edit `.env` with your database credentials:
   ```env
   DATABASE_URL=jdbc:postgresql://YOUR_EXTERNAL_HOST:5432/growthprint?sslmode=require
   DATABASE_USERNAME=growthprint_user
   DATABASE_PASSWORD=your_actual_password
   JWT_SECRET=your-secret-key-min-32-characters-long
   CORS_ALLOWED_ORIGINS=http://localhost:3000
   ```

**Important:**
- Replace `YOUR_EXTERNAL_HOST` with the complete hostname from Render's External Database URL
- The JDBC URL must start with `jdbc:postgresql://`
- Never embed credentials inside the URL
- Generate JWT_SECRET with: `openssl rand -base64 64`

**Option B: Using system environment variables**

Set environment variables in your OS or IDE:
- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `JWT_SECRET`
- `CORS_ALLOWED_ORIGINS`

### 3. Database Setup

**Option A: Use Render PostgreSQL (recommended for development)**

1. Create PostgreSQL database on Render
2. Obtain connection details from Render Dashboard's Connect tab
3. Use external hostname for local development
4. Configure `.env` with connection details

**Option B: Use local PostgreSQL**

1. Install PostgreSQL locally
2. Create database:
   ```sql
   CREATE DATABASE growthprint;
   ```
3. Create user:
   ```sql
   CREATE USER growthprint_user WITH PASSWORD 'your_password';
   GRANT ALL PRIVILEGES ON DATABASE growthprint TO growthprint_user;
   ```
4. Configure `.env` with local connection details

### 4. Verify Setup

Run the application to verify configuration:
```powershell
.\mvnw.cmd spring-boot:run
```

Expected output:
- Application starts on port 8090
- Flyway migrations run successfully
- Health endpoint accessible at http://localhost:8090/actuator/health
- Swagger UI accessible at http://localhost:8090/swagger-ui.html

## Development Commands

### Build

**Clean compile:**
```powershell
.\mvnw.cmd clean compile
```

**Package:**
```powershell
.\mvnw.cmd clean package
```

**Verify (tests + package):**
```powershell
.\mvnw.cmd verify
```

### Run

**Run with Maven wrapper:**
```powershell
.\mvnw.cmd spring-boot:run
```

**Run with IntelliJ IDEA:**
- Open project in IntelliJ IDEA
- Import as Maven project
- Run main class `GrowthprintBackendApplication`
- Application will automatically load `.env` file

### Test

**Run all tests:**
```powershell
.\mvnw.cmd test
```

**Run specific test class:**
```powershell
.\mvnw.cmd test -Dtest=ClientControllerTest
```

**Run integration tests (requires Docker):**
```powershell
.\mvnw.cmd test -Dtest=PostgreSQLIntegrationTest,FlywayMigrationTest,CrmLeadRepositoryIntegrationTest
```

**Run with coverage:**
```powershell
.\mvnw.cmd test jacoco:report
```

### Database Migrations

**Apply migrations on startup:**
- Flyway enabled by default in `application.yml`
- Migrations run automatically on application startup

**Apply migrations manually:**
```powershell
.\mvnw.cmd flyway:migrate
```

**Validate migrations:**
```powershell
.\mvnw.cmd flyway:validate
```

**View migration status:**
```powershell
.\mvnw.cmd flyway:info
```

**Clean and rebuild schema (development only):**
```powershell
.\mvnw.cmd flyway:clean
.\mvnw.cmd flyway:migrate
```

**Warning:** Never run `flyway:clean` in production

## IntelliJ IDEA Configuration

### Run Configuration

**Main Class:**
```
com.growthprint.backend.GrowthprintBackendApplication
```

**VM Options:**
```
-Dspring.profiles.active=dev
```

**Environment Variables:**
- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `JWT_SECRET`
- `CORS_ALLOWED_ORIGINS`

**Working Directory:**
```
$MODULE_WORKING_DIR$
```

**Use classpath of module:**
```
growthprint-backend
```

### Maven Configuration

**Maven Home:**
- Use bundled Maven or project Maven wrapper

**User Settings File:**
- Default: `~/.m2/settings.xml`

**Local Repository:**
- Default: `~/.m2/repository`

### Code Style

**Import:** Spring Boot Code Style (optional)
- Download from: https://github.com/spring-io/initializr/tree/master/initializr-generator/src/main/resources/templates/code-style

**Formatter:**
- Enable auto-format on save
- Configure line length: 120

**Inspections:**
- Enable Spring inspections
- Enable Java inspections
- Enable code style inspections

## Verification Steps

### 1. Verify Application Startup

```powershell
.\mvnw.cmd spring-boot:run
```

**Expected:**
- Application starts without errors
- Port 8090 accessible
- Flyway migrations run successfully
- No Spring Boot startup errors

### 2. Verify Health Endpoint

```powershell
curl http://localhost:8090/actuator/health
```

**Expected:**
```json
{
  "status": "UP"
}
```

### 3. Verify Swagger UI

Open browser: http://localhost:8090/swagger-ui.html

**Expected:**
- Swagger UI loads
- API endpoints listed
- Can try API endpoints

### 4. Verify Database Connection

Check application logs for:
- Flyway migration success messages
- Database connection success
- No connection errors

### 5. Verify Tests

```powershell
.\mvnw.cmd test
```

**Expected:**
- 21 unit tests pass
- 11 integration tests skipped (Docker unavailable)
- No test failures

## Troubleshooting

### Application Won't Start

**Issue:** Application fails to start

**Solutions:**
1. Check `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` are set
2. Verify PostgreSQL database is accessible
3. Check network connectivity to database
4. Verify JWT_SECRET is set (min 32 characters)
5. Check application logs for specific error

### Database Connection Failed

**Issue:** Cannot connect to PostgreSQL

**Solutions:**
1. Verify PostgreSQL is running
2. Check connection details in `.env`
3. Verify database exists
4. Check firewall rules
5. Verify SSL mode (`sslmode=require` for Render)

### Flyway Migration Failed

**Issue:** Flyway migration fails on startup

**Solutions:**
1. Check migration SQL syntax
2. Verify migration version sequence
3. Check database permissions
4. Verify database is empty (for first run)
5. Check Flyway schema history table

### Tests Fail

**Issue:** Unit tests fail

**Solutions:**
1. Check test error message
2. Verify test dependencies are up-to-date
3. Check test configuration
4. Run specific test class for details
5. Check for missing test data

### Integration Tests Skipped

**Issue:** Integration tests skipped

**Reason:** Docker unavailable in test environment

**Solutions:**
1. Install Docker Desktop
2. Start Docker daemon
3. Verify Docker is available: `docker --version`
4. Re-run integration tests

### JWT Authentication Fails

**Issue:** JWT token validation fails

**Solutions:**
1. Verify JWT_SECRET is set
2. Check JWT_SECRET length (min 32 characters)
3. Verify token format (Bearer <token>)
4. Check token expiration
5. Verify issuer matches configuration

## Recommended IDE Extensions

### IntelliJ IDEA Plugins

**Spring Boot:**
- Spring Boot Helper
- Spring Assistant

**Code Quality:**
- SonarLint
- CheckStyle-IDEA
- SpotBugs

**Database:**
- Database Navigator
- SQL Data Sources

**Testing:**
- JUnit Runner
- Coverage

**Git:**
- GitLens
- GitToolBox

## Development Workflow

### 1. Create Feature Branch

```bash
git checkout -b feature/your-feature-name
```

### 2. Make Changes

- Follow layered architecture pattern
- Write unit tests for new code
- Update API documentation
- Update relevant documentation files

### 3. Run Tests

```powershell
.\mvnw.cmd test
```

### 4. Build and Verify

```powershell
.\mvnw.cmd clean verify
```

### 5. Commit Changes

```bash
git add .
git commit -m "Add feature: description"
```

### 6. Push Branch

```bash
git push origin feature/your-feature-name
```

### 7. Create Pull Request

- Describe changes
- Link to issue or milestone
- Request code review

### 8. Merge After Approval

- Ensure CI/CD passes
- Address review comments
- Merge to main branch

## Debugging

### Remote Debugging

**Configure JVM options:**
```
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
```

**Connect from IntelliJ IDEA:**
- Run → Edit Configurations
- Add Remote JVM Debug
- Host: localhost
- Port: 5005

### Logging

**Configure log level in `application-dev.yml`:**
```yaml
logging:
  level:
    com.growthprint.backend: DEBUG
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### Database Query Logging

**Enable SQL logging:**
```yaml
spring:
  jpa:
    show-sql: true
```

**Enable parameter logging:**
```yaml
logging:
  level:
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

## Performance Profiling

### Java Flight Recorder

**Enable JFR:**
```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments="-XX:StartFlightRecording=duration=60s,filename=recording.jfr,settings=profile"
```

**Analyze recording:**
- Use JDK Mission Control
- Open recording.jfr file

### VisualVM

**Connect to running application:**
- Start VisualVM
- Connect to localhost:8090
- Monitor CPU, memory, threads

## Additional Resources

**Spring Boot Documentation:**
- https://docs.spring.io/spring-boot/docs/current/reference/html/

**Spring Security Documentation:**
- https://docs.spring.io/spring-security/reference/

**Flyway Documentation:**
- https://flywaydb.org/documentation/

**PostgreSQL Documentation:**
- https://www.postgresql.org/docs/

**Maven Documentation:**
- https://maven.apache.org/guides/

**IntelliJ IDEA Documentation:**
- https://www.jetbrains.com/idea/documentation/
