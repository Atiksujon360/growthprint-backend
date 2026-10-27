# GrowthPrint Backend - Deployment Guide

## Overview

This document covers environment configuration, deployment, backups, monitoring, and rollback procedures for the GrowthPrint backend.

## Environment Configuration

### Development Environment

**Purpose:** Local development and testing

**Configuration:**
- Port: 8090
- Database: Render PostgreSQL (external connection) or local PostgreSQL
- JWT Secret: From `.env` file
- CORS: http://localhost:3000
- Swagger UI: Enabled
- Admin Endpoints: Disabled (feature flag)

**Environment Variables:**
```env
DATABASE_URL=jdbc:postgresql://external-host:5432/growthprint?sslmode=require
DATABASE_USERNAME=growthprint_user
DATABASE_PASSWORD=your_password
JWT_SECRET=dev-secret-key-min-32-chars
CORS_ALLOWED_ORIGINS=http://localhost:3000
SPRINGDOC_ENABLED=true
APP_ADMIN_ENDPOINTS_ENABLED=false
```

### Staging Environment

**Purpose:** Pre-production testing

**Configuration:**
- Port: 8090
- Database: Render PostgreSQL (internal connection)
- JWT Secret: From Render environment variables
- CORS: Staging frontend URL
- Swagger UI: Disabled
- Admin Endpoints: Enabled (with authentication)

**Environment Variables (Render Dashboard):**
```
DATABASE_URL=jdbc:postgresql://internal-host:5432/growthprint?sslmode=require
DATABASE_USERNAME=growthprint_user
DATABASE_PASSWORD=your_password
JWT_SECRET=staging-secret-key-min-32-chars
CORS_ALLOWED_ORIGINS=https://staging-frontend.growthprint.com
SPRINGDOC_ENABLED=false
APP_ADMIN_ENDPOINTS_ENABLED=true
```

### Production Environment

**Purpose:** Live production deployment

**Configuration:**
- Port: 8090
- Database: Render PostgreSQL (internal connection)
- JWT Secret: From Render environment variables
- CORS: Production frontend URL
- Swagger UI: Disabled
- Admin Endpoints: Enabled (with authentication)

**Environment Variables (Render Dashboard):**
```
DATABASE_URL=jdbc:postgresql://internal-host:5432/growthprint?sslmode=require
DATABASE_USERNAME=growthprint_user
DATABASE_PASSWORD=your_password
JWT_SECRET=prod-secret-key-min-32-chars-generate-with-openssl
CORS_ALLOWED_ORIGINS=https://growthprint.com
SPRINGDOC_ENABLED=false
APP_ADMIN_ENDPOINTS_ENABLED=true
```

**Security Notes:**
- JWT_SECRET must be generated with `openssl rand -base64 64`
- Never use default or dev secrets in production
- Rotate secrets regularly (recommended: 90 days)
- Use internal database URL for Render services

## Deployment Procedures

### Pre-Deployment Checklist

**Code Quality:**
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Code coverage > 80%
- [ ] No critical bugs
- [ ] No critical security vulnerabilities

**Security:**
- [ ] JWT_SECRET set to strong random value
- [ ] HTTPS configured
- [ ] CORS configured for production domain
- [ ] Rate limiting configured
- [ ] Security headers configured
- [ ] Dependency scan passed

**Database:**
- [ ] Flyway migrations tested
- [ ] Migrations applied to staging database
- [ ] Schema verified
- [ ] Data integrity verified
- [ ] Backup created before migration

**Documentation:**
- [ ] API documentation updated
- [ ] Release notes written
- [ ] PROJECT_STATUS.md updated
- [ ] AGENTS.md reviewed

### Deployment to Render

**Step 1: Build Application**

```bash
.\mvnw.cmd clean package
```

**Step 2: Verify Build**

```bash
.\mvnw.cmd verify
```

**Step 3: Tag Release**

```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

**Step 4: Deploy to Render**

**Option A: Automatic Deployment (Git Integration)**
1. Connect Render to GitHub repository
2. Configure build settings:
   - Build Command: `./mvnw.cmd clean package`
   - Start Command: `java -jar target/growthprint-backend-0.0.1-SNAPSHOT.jar`
3. Configure environment variables in Render dashboard
4. Push to main branch triggers automatic deployment

**Option B: Manual Deployment**
1. Build JAR locally
2. Upload JAR to Render
3. Configure environment variables
4. Start service

**Step 5: Verify Deployment**

1. Check health endpoint: `https://growthprint-backend.onrender.com/actuator/health`
2. Check logs in Render dashboard
3. Test API endpoints
4. Verify authentication works
5. Verify database connectivity

### Deployment with Docker (Alternative)

**Step 1: Build Docker Image**

```dockerfile
FROM openjdk:17-slim
WORKDIR /app
COPY target/growthprint-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Step 2: Build Image**

```bash
docker build -t growthprint-backend:latest .
```

**Step 3: Push to Registry**

```bash
docker tag growthprint-backend:latest registry.example.com/growthprint-backend:latest
docker push registry.example.com/growthprint-backend:latest
```

**Step 4: Deploy**

```bash
kubectl apply -f k8s-deployment.yaml
```

## Database Migrations

### Migration Strategy

**Principles:**
- All schema changes via Flyway migrations
- Never modify existing migrations
- Create new migration for schema changes
- Test migrations on staging before production

### Applying Migrations

**Automatic (Default):**
- Flyway enabled by default
- Migrations run on application startup
- No manual intervention required

**Manual (if needed):**
```bash
.\mvnw.cmd flyway:migrate
```

### Migration Verification

**Check migration status:**
```bash
.\mvnw.cmd flyway:info
```

**Validate migrations:**
```bash
.\mvnw.cmd flyway:validate
```

### Rollback Strategy

**Flyway does not support rollback by design**

**If migration fails:**
1. Stop application
2. Identify failed migration
3. Create new migration to fix issue
4. Apply fix migration
5. Restart application

**If data corruption occurs:**
1. Restore from backup
2. Re-apply migrations from backup point
3. Verify data integrity

## Backup Strategy

### Database Backups

**Render PostgreSQL Automatic Backups:**
- Daily backups included with Render
- 7-day retention for free tier
- 30-day retention for paid tier

**Manual Backups:**
```bash
pg_dump -h host -U user -d growthprint > backup.sql
```

**Restore from Backup:**
```bash
psql -h host -U user -d growthprint < backup.sql
```

### Backup Schedule

**Recommended:**
- Daily automated backups (Render)
- Weekly manual backups before major releases
- Monthly full backups archived to cold storage

### Backup Verification

**Procedure:**
1. Restore backup to staging database
2. Verify data integrity
3. Run application against restored database
4. Verify functionality
5. Document verification results

## Monitoring

### Health Checks

**Spring Boot Actuator Endpoints:**
- Health: `/actuator/health`
- Info: `/actuator/info`
- Metrics: `/actuator/metrics` (if configured)

**Health Check Configuration:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
```

### Application Monitoring

**Recommended Tools:**
- Prometheus for metrics collection
- Grafana for visualization
- ELK Stack for log aggregation
- Sentry for error tracking

**Metrics to Monitor:**
- Request rate
- Response time
- Error rate
- Database connection pool utilization
- JVM memory usage
- CPU usage

### Log Aggregation

**Recommended Tools:**
- ELK Stack (Elasticsearch, Logstash, Kibana)
- Splunk
- CloudWatch Logs (AWS)
- Render Logs (Render built-in)

**Log Levels:**
- ERROR: Critical errors requiring immediate attention
- WARN: Warning conditions
- INFO: Informational messages
- DEBUG: Debug information (development only)

### Alerting

**Alert Rules:**
- Error rate > 1% for 5 minutes
- Response time > 1 second for 5 minutes
- Database connection pool utilization > 80%
- JVM memory usage > 90%
- CPU usage > 80% for 5 minutes
- Health check fails

**Alert Channels:**
- Email
- Slack
- SMS (for critical alerts)

## Rollback Procedures

### Immediate Rollback Triggers

Rollback immediately if:
- Critical security vulnerability discovered
- Data corruption or data loss
- System downtime > 5 minutes
- Error rate > 5%
- Performance degradation > 50%

### Rollback Process

**Step 1: Identify Issue**
- Monitor alerts
- Review logs
- Reproduce issue
- Determine root cause

**Step 2: Stop Traffic**
- Put service in maintenance mode
- Stop accepting new requests
- Allow in-flight requests to complete

**Step 3: Rollback Code**
```bash
git checkout previous-release-tag
git push origin main
```

**Step 4: Re-deploy**
- Trigger Render deployment
- Wait for deployment to complete
- Verify health endpoint

**Step 5: Verify Recovery**
- Check health endpoint
- Run smoke tests
- Monitor error rate
- Monitor response time

**Step 6: Investigate Root Cause**
- Analyze logs
- Review code changes
- Identify bug
- Create fix

**Step 7: Test Fix**
- Deploy fix to staging
- Run integration tests
- Verify fix resolves issue

**Step 8: Deploy Fix**
- Merge fix to main
- Deploy to production
- Verify recovery

### Database Rollback

**If migration caused issue:**
1. Stop application
2. Restore database from backup
3. Revert migration code
4. Create fix migration
5. Apply fix migration
6. Restart application

**If data corruption:**
1. Stop application
2. Restore database from backup
3. Verify data integrity
4. Investigate corruption cause
5. Implement preventive measures
6. Restart application

## Disaster Recovery

### Disaster Scenarios

**Scenario 1: Complete Data Center Failure**
- Deploy to alternative region
- Restore database from backup
- Update DNS to point to alternative region
- Verify functionality

**Scenario 2: Database Corruption**
- Restore from most recent backup
- Verify data integrity
- Re-apply any lost transactions
- Investigate corruption cause

**Scenario 3: Security Breach**
- Rotate all secrets
- Reset all user passwords
- Review audit logs
- Patch vulnerability
- Monitor for suspicious activity

### Recovery Time Objectives (RTO)

**Target RTO:**
- Critical services: 1 hour
- Non-critical services: 4 hours

### Recovery Point Objectives (RPO)

**Target RPO:**
- Critical data: 15 minutes
- Non-critical data: 1 hour

## Security Considerations

### Production Security Checklist

- [ ] HTTPS enabled with valid SSL certificate
- [ ] JWT_SECRET set to strong random value
- [ ] CORS configured for production domain only
- [ ] Rate limiting configured
- [ ] Security headers configured
- [ ] Dependency scan passed
- [ ] Secret scan passed
- [ ] Firewall rules configured
- [ ] Database access restricted
- [ ] API authentication enforced
- [ ] Admin endpoints protected
- [ ] Swagger UI disabled
- [ ] Audit logging enabled

### Incident Response

**Security Incident Types:**
- Unauthorized access
- Data breach
- Denial of service
- Malware infection
- Phishing attack

**Response Process:**
1. Detect incident
2. Contain threat
3. Eradicate threat
4. Recover systems
5. Document lessons learned

## Post-Deployment Verification

### Smoke Tests

**Tests to Run:**
1. Health endpoint returns UP
2. Authentication works
3. Sample API calls succeed
4. Database connectivity verified
5. Error rate < 1%
6. Response time < 500ms

### Monitoring Verification

**Checks:**
1. Health checks passing
2. Metrics collecting
3. Logs aggregating
4. Alerts configured
5. No critical errors in logs

### User Acceptance Testing

**Tasks:**
1. Admin login
2. Client login
3. Create lead
4. Create client
5. Create project
6. Create support request
7. Create invoice
8. Verify all workflows

## Documentation Updates

**Post-Deployment:**
- Update PROJECT_STATUS.md
- Document any issues encountered
- Update deployment runbook
- Update monitoring configuration
- Document lessons learned

## References

**Render Documentation:**
- https://render.com/docs

**Spring Boot Deployment:**
- https://docs.spring.io/spring-boot/docs/current/reference/html/deployment.html

**Flyway Documentation:**
- https://flywaydb.org/documentation/

**PostgreSQL Backup:**
- https://www.postgresql.org/docs/current/backup.html
