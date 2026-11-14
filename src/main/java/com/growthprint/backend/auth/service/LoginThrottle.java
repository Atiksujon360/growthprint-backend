package com.growthprint.backend.auth.service;

import com.growthprint.backend.common.exception.TooManyRequestsException;
import com.growthprint.backend.config.AccountSecurityProperties;
import com.growthprint.backend.config.InMemoryRateLimiter;
import com.growthprint.backend.security.SecureTokens;
import com.growthprint.backend.security.SecurityAudit;
import org.springframework.stereotype.Component;

/**
 * Per-account login throttle, complementing the per-IP limit in RateLimitFilter: it slows down password
 * guessing against one account from many IPs. The same answer (429) is given for existing and unknown
 * addresses, so it does not reveal which emails have accounts. Keys are hashed: no addresses kept in memory.
 */
@Component
public class LoginThrottle {

    private final InMemoryRateLimiter rateLimiter;
    private final AccountSecurityProperties properties;

    public LoginThrottle(InMemoryRateLimiter rateLimiter, AccountSecurityProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    public void checkAllowed(String email) {
        String key = "login-account:" + SecureTokens.sha256Hex(PasswordRecoveryService.normalize(email));
        if (!rateLimiter.tryAcquire(key, properties.getLoginMaxAttemptsPerAccount(),
                properties.getLoginAccountWindow())) {
            SecurityAudit.record(SecurityAudit.Event.LOGIN_THROTTLED, null, null, "per-account");
            throw new TooManyRequestsException(properties.getLoginAccountWindow());
        }
    }
}
