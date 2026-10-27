# GrowthPrint Backend - Quality Gates

## Overview

This document defines required tests, builds, static analysis, security scans, and release criteria for the GrowthPrint backend. All quality gates must pass before code can be merged to main or deployed to production.

## Test Requirements

### Unit Tests

**Coverage Goal:** 80% line coverage for business logic

**Required Unit Tests:**
- All service classes must have unit tests
- All validation logic must have unit tests
- All custom exceptions must have unit tests
- All utility classes must have unit tests

**Test Framework:** JUnit 5 with Mockito

**Command:**
```bash
.\mvnw.cmd test
```

**Pass Criteria:**
- All unit tests pass
- No skipped tests (except Docker-dependent tests)
- Test execution time < 2 minutes

**Current Status:**
- 21/32 tests passing
- 11/32 tests skipped (Docker unavailable)
- Unit tests: All passing
- Coverage: Not measured (add JaCoCo plugin)

### Integration Tests

**Coverage Goal:** All repository operations and API endpoints

**Required Integration Tests:**
- All repository operations must have integration tests
- All API endpoints must have integration tests
- All Flyway migrations must have tested
- Authentication flow must be tested
- Authorization rules must be tested

**Test Framework:** Spring Boot Test with Testcontainers

**Command:**
```bash
# Run all integration tests (requires Docker)
.\mvnw.cmd test -Dtest=PostgreSQLIntegrationTest,FlywayMigrationTest,CrmLeadRepositoryIntegrationTest

# Run specific integration test class
.\mvnw.cmd test -Dtest=FlywayMigrationTest
```

**Pass Criteria:**
- All integration tests pass
- Docker available
- Test execution time < 5 minutes

**Current Status:**
- 11/11 integration tests skipped (Docker unavailable)
- Test infrastructure in place
- Blocked by Docker unavailability

### API Tests

**Coverage Goal:** All API endpoints

**Required API Tests:**
- All REST endpoints must have API tests
- Request validation must be tested
- Response format must be tested
- Error handling must be tested
- Authentication must be tested
- Authorization must be tested

**Test Framework:** Spring Boot Test with MockMvc

**Command:**
```bash
.\mvnw.cmd test -Dtest=*ControllerTest
```

**Pass Criteria:**
- All API tests pass
- Authentication tests pass
- Authorization tests pass
- Error response tests pass

**Current Status:**
- ClientControllerTest: 3/3 passing
- Other controller tests: Not implemented

## Build Requirements

### Maven Build

**Command:**
```bash
.\mvnw.cmd clean compile
```

**Pass Criteria:**
- Compilation succeeds with no errors
- No compilation warnings
- Build time < 1 minute

**Current Status:** Passing

### Maven Package

**Command:**
```bash
.\mvnw.cmd clean package
```

**Pass Criteria:**
- Package builds successfully
- JAR file created in target/
- Build time < 2 minutes

**Current Status:** Passing

### Maven Verify

**Command:**
```bash
.\mvnw.cmd verify
```

**Pass Criteria:**
- All tests pass
- All checks pass
- Build succeeds

**Current Status:** Partial (11 tests skipped)

## Static Analysis

### Code Quality

**Recommended Tools:**
- SpotBugs - Static code analysis
- PMD - Code quality checks
- Checkstyle - Code style checks
- SonarQube - Comprehensive quality analysis

**Configuration:**
- Add plugins to pom.xml
- Configure quality rules
- Set quality thresholds
- Fail build on quality violations

**Command:**
```bash
.\mvnw.cmd spotbugs:check
.\mvnw.cmd pmd:check
.\mvnw.cmd checkstyle:check
```

**Pass Criteria:**
- No critical bugs
- No critical code quality issues
- No critical style violations
- Code coverage > 80%

**Current Status:** Not implemented

### TypeScript Analysis (N/A)

Not applicable for Java backend.

## Security Scans

### Dependency Scanning

**Recommended Tools:**
- OWASP Dependency-Check
- Snyk
- Dependabot (GitHub)

**Command:**
```bash
.\mvnw.cmd org.owasp:dependency-check-maven:check
```

**Pass Criteria:**
- No critical vulnerabilities
- No high vulnerabilities (with justification)
- Medium vulnerabilities reviewed and accepted

**Current Status:** Not implemented

### Secret Scanning

**Recommended Tools:**
- TruffleHog
- GitLeaks
- Gitleaks (pre-commit hook)

**Command:**
```bash
gitleaks detect
```

**Pass Criteria:**
- No secrets detected in code
- No secrets detected in git history
- No secrets detected in configuration files

**Current Status:** Not implemented

### SAST (Static Application Security Testing)

**Recommended Tools:**
- SonarQube Security Rules
- Find Security Bugs (SpotBugs plugin)
- Semgrep

**Command:**
```bash
.\mvnw.cmd spotbugs:check
```

**Pass Criteria:**
- No critical security issues
- No high security issues (with justification)
- Medium security issues reviewed and accepted

**Current Status:** Not implemented

## Release Criteria

### Pre-Release Checklist

**Code Quality:**
- [ ] All unit tests pass
- [ ] All integration tests pass (when Docker available)
- [ ] All API tests pass
- [ ] Code coverage > 80%
- [ ] No critical bugs
- [ ] No critical code quality issues
- [ ] No critical style violations

**Security:**
- [ ] No critical vulnerabilities in dependencies
- [ ] No secrets in code or git history
- [ ] No critical security issues
- [ ] JWT_SECRET set to strong random value
- [ ] HTTPS configured
- [ ] CORS configured for production domain
- [ ] Rate limiting configured
- [ ] Security headers configured

**Documentation:**
- [ ] API documentation updated (Swagger)
- [ ] API_COVERAGE_MATRIX.md updated
- [ ] AGENTS.md reviewed and updated
- [ ] ARCHITECTURE.md reviewed and updated
- [ ] SECURITY.md reviewed and updated
- [ ] PROJECT_STATUS.md updated
- [ ] Release notes written

**Database:**
- [ ] Flyway migrations tested
- [ ] Migrations applied to staging database
- [ ] Schema verified
- [ ] Data integrity verified
- [ ] Performance tested

**Testing:**
- [ ] Manual testing completed
- [ ] Integration testing completed
- [ ] Performance testing completed
- [ ] Security testing completed
- [ ] UAT (User Acceptance Testing) completed

### Release Gates

**Gate 1: Code Review**
- All changes reviewed by peer
- Reviewer approves changes
- No unresolved review comments

**Gate 2: CI/CD Pipeline**
- All automated tests pass
- All quality checks pass
- All security scans pass
- Build succeeds

**Gate 3: Staging Deployment**
- Deployment to staging succeeds
- Smoke tests pass on staging
- Integration tests pass on staging
- Performance tests pass on staging

**Gate 4: Production Deployment**
- Deployment to production succeeds
- Health checks pass
- Monitoring confirms normal operation
- No errors in logs

## Continuous Integration

### GitHub Actions Workflow (Recommended)

**Triggers:**
- Push to main branch
- Pull request to main branch
- Manual trigger

**Steps:**
1. Checkout code
2. Set up Java 17
3. Cache Maven dependencies
4. Run unit tests
5. Run integration tests (if Docker available)
6. Run static analysis
7. Run dependency scanning
8. Run secret scanning
8. Build package
9. Upload artifacts

**Status:** Not implemented

### Pre-Commit Hooks (Recommended)

**Hooks:**
- Check for secrets (Gitleaks)
- Check code style (Checkstyle)
- Run unit tests
- Validate Git commit message

**Configuration:** Husky or pre-commit framework

**Status:** Not implemented

## Quality Metrics

### Code Coverage

**Tool:** JaCoCo

**Configuration:**
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Target:** 80% line coverage

**Current Status:** Not measured

### Code Complexity

**Tool:** SonarQube or PMD

**Metrics:**
- Cyclomatic complexity < 10 per method
- Cognitive complexity < 15 per method
- Class size < 500 lines
- Method size < 50 lines

**Current Status:** Not measured

### Technical Debt

**Tool:** SonarQube

**Metrics:**
- Technical debt ratio < 5%
- Code smells < 10 per 1000 lines
- Duplicated lines < 3%

**Current Status:** Not measured

## Performance Requirements

### API Response Time

**Targets:**
- Simple queries: < 200ms
- Complex queries: < 500ms
- Authentication: < 100ms

**Measurement:**
- Load testing with JMeter or k6
- APM monitoring in production

**Current Status:** Not measured

### Database Performance

**Targets:**
- Query execution time < 100ms
- Connection pool utilization < 80%
- Database CPU utilization < 70%

**Measurement:**
- Database query logging
- APM monitoring

**Current Status:** Not measured

## Rollback Criteria

### Immediate Rollback

Rollback immediately if:
- Critical security vulnerability discovered
- Data corruption or data loss
- System downtime > 5 minutes
- Error rate > 5%
- Performance degradation > 50%

### Rollback Process

1. Identify issue
2. Stop traffic to affected version
3. Deploy previous stable version
4. Verify system recovery
5. Investigate root cause
6. Fix issue
7. Test fix
8. Deploy fix

## Quality Gates Summary

| Gate | Tool | Status | Threshold |
|------|------|--------|-----------|
| Unit Tests | JUnit 5 | Partial (21/32 passing) | 100% pass |
| Integration Tests | Testcontainers | Blocked (Docker unavailable) | 100% pass |
| API Tests | MockMvc | Partial (ClientController only) | 100% pass |
| Code Coverage | JaCoCo | Not implemented | > 80% |
| Static Analysis | SpotBugs, PMD | Not implemented | No critical issues |
| Dependency Scanning | OWASP Dependency-Check | Not implemented | No critical vulnerabilities |
| Secret Scanning | Gitleaks | Not implemented | No secrets |
| Security Scanning | SonarQube Security | Not implemented | No critical issues |
| Build | Maven | Passing | Success |
| Performance | JMeter/k6 | Not implemented | < 200ms simple, < 500ms complex |

## Next Steps

1. Add JaCoCo plugin for code coverage measurement
2. Enable Docker in CI/CD for integration tests
3. Implement GitHub Actions workflow
4. Add SpotBugs, PMD, Checkstyle plugins
5. Add OWASP Dependency-Check plugin
6. Implement Gitleaks pre-commit hook
7. Set up SonarQube for comprehensive quality analysis
8. Implement performance testing
9. Set up APM monitoring
10. Document quality gate violations and resolution process
