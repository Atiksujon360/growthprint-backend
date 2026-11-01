# GrowthPrint Backend - Docker Deployment Guide for Render

## Project Structure Analysis

**Maven Project Type:** Single-module project
- **Root pom.xml:** Contains the Spring Boot application
- **Main Class:** `com.growthprint.backend.GrowthprintBackendApplication`
- **Artifact ID:** `growthprint-backend`
- **Version:** `0.0.1-SNAPSHOT`
- **Executable JAR:** `target/growthprint-backend-0.0.1-SNAPSHOT.jar`

**Java Version:** 21 (required by pom.xml:30)
**Spring Boot Version:** 4.1.1
**Application Port:** 8090 (configurable via environment variable)

## Docker Configuration Files Created

### Dockerfile
**Location:** `Dockerfile` (repository root)
**Type:** Multi-stage build
**Stage 1 (Build):** Maven 3.9.21 with Eclipse Temurin Java 21
**Stage 2 (Runtime):** Eclipse Temurin JRE 21

**Key Features:**
- Multi-stage build for smaller final image
- Non-root user for security (user: growthprint)
- Health check on `/actuator/health`
- PORT environment variable support (Render sets this automatically)
- Exposes port 8090 (configurable)

### .dockerignore
**Location:** `.dockerignore` (repository root)
**Excludes:**
- Git files (.git, .gitignore)
- Build artifacts (target/, *.jar)
- IDE files (.idea/, .vscode/)
- Environment files (.env, .env.local)
- Documentation (*.md, docs/)
- Test files (src/test/)
- Logs (*.log, logs/)
- Docker files (Dockerfile, .dockerignore, docker-compose.yml)

## Required Environment Variables for Render

### Database Configuration (Render PostgreSQL)
```
DATABASE_URL=jdbc:postgresql://<internal-host>:5432/<database-name>?sslmode=require
DATABASE_USERNAME=<database-user>
DATABASE_PASSWORD=<database-password>
```

**Important:**
- Use the **Internal Database URL** from Render's PostgreSQL Connect tab
- Internal URL format: `dpg-xxxxx.oregon-postgres.render.com:5432/growthprint`
- JDBC URL must start with `jdbc:postgresql://`
- Include `sslmode=require` for secure connections
- Never embed credentials in the URL

### Security Configuration
```
JWT_SECRET=<secure-random-key-min-32-chars>
AI_CREDENTIALS_ENCRYPTION_KEY=<base64-32-bytes>
```

**JWT_SECRET:**
- REQUIRED - application will not start without it
- Generate with: `openssl rand -base64 64`
- Minimum 32 characters
- No fallback value exists by design

**AI_CREDENTIALS_ENCRYPTION_KEY:**
- Optional for AI features
- Generate with: `openssl rand -base64 32`
- If missing, AI features cannot be configured (rest of app works)

### CORS Configuration
```
CORS_ALLOWED_ORIGINS=https://your-frontend-domain.com
```

**Examples:**
- Development: `http://localhost:3000`
- Production: `https://growthprint.vercel.app` (or custom domain)
- Multiple origins: comma-separated

### API Documentation
```
SPRINGDOC_ENABLED=false
```

**Production:** Set to `false` to disable Swagger UI
**Development:** Can be set to `true` for local testing

### Optional Company Details (Invoice PDFs)
```
COMPANY_NAME=GrowthPrint
COMPANY_EMAIL=
COMPANY_WEBSITE=
COMPANY_ADDRESS=
COMPANY_PHONE=
COMPANY_TAX_ID=
COMPANY_REGISTRATION_NUMBER=
COMPANY_PAYMENT_INSTRUCTIONS=
```

All optional - leave empty to omit from PDF

## Render Docker Deployment Configuration

### Step 1: Create New Web Service

1. Go to Render Dashboard
2. Click "New" → "Web Service"
3. Connect to GitHub repository
4. Select `growthprint-backend` repository

### Step 2: Configure Build Settings

**Docker Context:**
- **Dockerfile Path:** `Dockerfile` (leave as default if in repository root)
- **Docker Context:** `/` (repository root)
- **Build Command:** (leave empty - Docker builds automatically)

**Environment:**
- **Runtime:** Docker
- **Region:** Choose same region as PostgreSQL (e.g., Oregon, Frankfurt)

### Step 3: Configure Environment Variables

**Add the following in Render Dashboard → Environment:**

**Required Variables:**
```
DATABASE_URL=jdbc:postgresql://<internal-host>:5432/<database-name>?sslmode=require
DATABASE_USERNAME=<database-user>
DATABASE_PASSWORD=<database-password>
JWT_SECRET=<generate-with-openssl-rand-base64-64>
```

**Optional Variables:**
```
AI_CREDENTIALS_ENCRYPTION_KEY=<generate-with-openssl-rand-base64-32>
CORS_ALLOWED_ORIGINS=https://your-frontend-domain.com
SPRINGDOC_ENABLED=false
COMPANY_NAME=GrowthPrint
```

**Environment Scopes:**
- Production: Set values for production environment
- Preview: Set values for preview deployments (can use staging database)
- Development: Not needed for Docker deployment

### Step 4: Configure Health Check

**Render Health Check Path:** `/actuator/health`

**Expected Response:**
```json
{
  "status": "UP"
}
```

**Health Check Configuration in Dockerfile:**
```dockerfile
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8090/actuator/health || exit 1
```

### Step 5: Port Configuration

**Application Port:** 8090
**Render PORT Environment Variable:** Render automatically sets `PORT` env var

**Note:** Spring Boot's `server.port` configuration in `application.yml` will be overridden by Render's `PORT` environment variable. The application will use the port Render assigns (usually 8090 or another available port).

### Step 6: Deploy

1. Click "Create Web Service"
2. Wait for Docker build to complete
3. Monitor build logs in Render Dashboard
4. Verify deployment success

## Render PostgreSQL Configuration

### Step 1: Create PostgreSQL Database

1. Go to Render Dashboard
2. Click "New" → "PostgreSQL"
3. Choose database name (e.g., `growthprint`)
4. Choose region (same as backend service)
5. Create database

### Step 2: Get Connection Details

**From PostgreSQL Dashboard → Connect:**

**Internal URL (for Render services):**
```
postgresql://<internal-host>:5432/<database-name>
```

**External URL (for local development):**
```
postgresql://<external-host>:5432/<database-name>
```

**Step 3: Convert to JDBC URL for Backend:**

**Internal JDBC URL:**
```
jdbc:postgresql://<internal-host>:5432/<database-name>?sslmode=require
```

**Example:**
```
jdbc:postgresql://dpg-xxxxx.oregon-postgres.render.com:5432/growthprint?sslmode=require
```

### Step 4: Configure Backend Environment Variables

In the backend service environment, set:
```
DATABASE_URL=jdbc:postgresql://<internal-host>:5432/<database-name>?sslmode=require
DATABASE_USERNAME=<database-user>
DATABASE_PASSWORD=<database-password>
```

## Verification Steps

### 1. Verify Docker Build Locally (Optional)

If Docker is available locally:

```bash
cd C:\Users\Hi\OneDrive\Desktop\growthprint-backend\growthprint-backend
docker build -t growthprint-backend:latest .
```

Expected output:
- Stage 1: Maven download and build
- Stage 2: JAR copy and runtime image creation
- Final image size: ~250-300 MB

### 2. Test Container Locally (Optional)

```bash
docker run -p 8090:8090 \
  -e DATABASE_URL=jdbc:postgresql://localhost:5432/test?sslmode=require \
  -e DATABASE_USERNAME=test \
  -e DATABASE_PASSWORD=test \
  -e JWT_SECRET=test-secret-key-min-32-chars-test \
  growthprint-backend:latest
```

Expected: Application starts on port 8090

### 3. Verify Render Deployment

**Check Logs:**
- Go to Render Dashboard → Backend Service → Logs
- Look for "Started GrowthprintBackendApplication"
- Verify no errors during startup
- Verify Flyway migrations ran successfully

**Check Health Endpoint:**
```bash
curl https://<your-backend-url>.onrender.com/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

**Check Application Logs:**
- Verify database connection successful
- Verify JWT secret loaded
- Verify Flyway migrations applied
- Verify application listening on correct port

## Troubleshooting

### Issue: Container fails to start

**Check:**
1. Environment variables are set correctly
2. JWT_SECRET is at least 32 characters
3. DATABASE_URL is in correct JDBC format
4. Database is accessible from Render

**Solution:**
- Render logs will show specific error
- Common issue: Missing or invalid JWT_SECRET

### Issue: Database connection fails

**Check:**
1. DATABASE_URL uses internal hostname
2. DATABASE_USERNAME and DATABASE_PASSWORD are correct
3. Database is in same region as backend service

**Solution:**
- Use internal URL from Render PostgreSQL Connect tab
- Verify credentials match database user

### Issue: Port binding fails

**Check:**
1. Application port configured correctly
2. Render PORT environment variable not conflicting

**Solution:**
- Spring Boot automatically uses Render's PORT
- No manual port configuration needed

### Issue: Flyway migrations fail

**Check:**
1. Database connection works
2. Flyway is enabled (default: true)
3. Migration files exist in `src/main/resources/db/migration`

**Solution:**
- Render logs will show migration errors
- Verify migration SQL syntax
- Check for version conflicts

## Security Considerations

### No Secrets in Image
- Dockerfile does not copy `.env` or environment files
- All secrets come from Render environment variables
- Image is clean and reproducible

### Non-Root User
- Container runs as non-root user `growthprint`
- Reduces attack surface

### Health Check
- Health check prevents Render from routing to unhealthy containers
- Automatically restarts failing containers

### SSL/TLS
- Render provides automatic HTTPS
- Database connection uses `sslmode=require`

## Rollback Procedure

### Option 1: Redeploy Previous Commit
1. Go to Render Dashboard → Deployments
2. Find previous successful deployment
3. Click "Redeploy"
4. Wait for deployment to complete

### Option 2: Rollback Git Commit
1. Revert to previous commit in Git
2. Push to main branch
3. Render automatically deploys
4. Verify deployment

### Option 3: Manual Rollback
1. Tag previous commit with version
2. Deploy specific commit via Render UI
3. Verify deployment

## Next Steps for Render Deployment

### Immediate Actions:
1. ✅ Dockerfile created (multi-stage, Java 21)
2. ✅ .dockerignore created (excludes secrets and build artifacts)
3. ⏭️ Create Render PostgreSQL database
4. ⏭️ Create Render Web Service with Docker configuration
5. ⏭️ Configure environment variables in Render
6. ⏭️ Deploy and verify

### Configuration Checklist:
- [ ] PostgreSQL database created in Render
- [ ] Backend service created with Docker runtime
- [ ] DATABASE_URL set to internal JDBC URL
- [ ] DATABASE_USERNAME and DATABASE_PASSWORD set
- [ ] JWT_SECRET generated and set (min 32 chars)
- [ ] CORS_ALLOWED_ORIGINS set to frontend domain
- [ ] SPRINGDOC_ENABLED set to false
- [ ] Health check configured: `/actuator/health`
- [ ] Deployment successful
- [ ] Health endpoint returns UP
- [ ] Flyway migrations applied successfully

### Post-Deployment Verification:
- [ ] Health check passes
- [ ] Authentication works
- [ ] API endpoints respond
- [ ] Database connectivity verified
- [ ] Logs show no errors
- [ ] Swagger UI disabled (verify at /swagger-ui.html)

## Summary

**Docker Configuration:** Complete and production-ready
**Build Strategy:** Multi-stage with Maven + Java 21
**Runtime:** Eclipse Temurin JRE 21
**Security:** Non-root user, no secrets in image, health checks
**Render Integration:** Fully compatible with Render Docker deployment
**Database:** Render PostgreSQL with internal connection
**Environment Variables:** All required and optional documented

**Estimated Image Size:** 250-300 MB
**Estimated Build Time:** 3-5 minutes on Render
**Estimated Startup Time:** 30-60 seconds (includes Flyway migrations)

**Deployment Status:** Ready for Render deployment once environment variables are configured.
