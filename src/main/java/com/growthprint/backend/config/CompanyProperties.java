package com.growthprint.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Seller details printed on generated documents (invoice PDFs), bound from {@code app.company.*}.
 * <p>
 * Every value is optional and comes ONLY from configuration (environment variables, see {@code .env.example}).
 * Nothing is ever invented: a block whose values are not configured is simply left out of the document.
 * Multi-line values (address, payment instructions) may use a real newline or the two characters {@code \n}
 * as line separator, so they can be supplied through a single-line environment variable.
 */
@Component
@ConfigurationProperties(prefix = "app.company")
public class CompanyProperties {

    private String name;
    private String email;
    private String website;
    private String address;
    private String phone;
    private String taxId;
    private String registrationNumber;
    private String paymentInstructions;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public String getPaymentInstructions() { return paymentInstructions; }
    public void setPaymentInstructions(String paymentInstructions) { this.paymentInstructions = paymentInstructions; }

    /** Address split into non-blank lines; empty when not configured. */
    public List<String> addressLines() {
        return lines(address);
    }

    /** Payment instructions split into non-blank lines; empty when not configured. */
    public List<String> paymentInstructionLines() {
        return lines(paymentInstructions);
    }

    /** True when the value is configured (non-null, non-blank). */
    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static List<String> lines(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.replace("\\n", "\n").split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
