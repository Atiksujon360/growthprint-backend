package com.growthprint.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Password recovery, invitation and per-account throttling settings ({@code app.security.account.*}). */
@Component
@ConfigurationProperties(prefix = "app.security.account")
public class AccountSecurityProperties {

    /** Public URL of the frontend; reset and invitation links point here. */
    private String frontendBaseUrl = "http://localhost:3000";
    private Duration passwordResetTtl = Duration.ofMinutes(30);
    private Duration invitationTtl = Duration.ofHours(72);

    /** Login attempts per email address per window (in addition to the per-IP limit). */
    private int loginMaxAttemptsPerAccount = 20;
    private Duration loginAccountWindow = Duration.ofMinutes(15);

    /** Reset emails per address per window; extra requests get the same generic answer but send nothing. */
    private int resetMaxRequestsPerAccount = 3;
    private Duration resetAccountWindow = Duration.ofHours(1);

    public String getFrontendBaseUrl() {
        return frontendBaseUrl;
    }

    public void setFrontendBaseUrl(String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Base URL without trailing slash. */
    public String normalizedFrontendBaseUrl() {
        String base = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public Duration getPasswordResetTtl() {
        return passwordResetTtl;
    }

    public void setPasswordResetTtl(Duration passwordResetTtl) {
        this.passwordResetTtl = passwordResetTtl;
    }

    public Duration getInvitationTtl() {
        return invitationTtl;
    }

    public void setInvitationTtl(Duration invitationTtl) {
        this.invitationTtl = invitationTtl;
    }

    public int getLoginMaxAttemptsPerAccount() {
        return loginMaxAttemptsPerAccount;
    }

    public void setLoginMaxAttemptsPerAccount(int loginMaxAttemptsPerAccount) {
        this.loginMaxAttemptsPerAccount = loginMaxAttemptsPerAccount;
    }

    public Duration getLoginAccountWindow() {
        return loginAccountWindow;
    }

    public void setLoginAccountWindow(Duration loginAccountWindow) {
        this.loginAccountWindow = loginAccountWindow;
    }

    public int getResetMaxRequestsPerAccount() {
        return resetMaxRequestsPerAccount;
    }

    public void setResetMaxRequestsPerAccount(int resetMaxRequestsPerAccount) {
        this.resetMaxRequestsPerAccount = resetMaxRequestsPerAccount;
    }

    public Duration getResetAccountWindow() {
        return resetAccountWindow;
    }

    public void setResetAccountWindow(Duration resetAccountWindow) {
        this.resetAccountWindow = resetAccountWindow;
    }
}
