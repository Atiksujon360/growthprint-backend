package com.growthprint.backend.inquiry.dto;

import com.growthprint.backend.lead.entity.LeadEnums;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static com.growthprint.backend.lead.entity.LeadEnums.PreferredContactMethod.EMAIL;
import static com.growthprint.backend.lead.entity.LeadEnums.ServiceCategory.SOFTWARE_DEV;
import static org.assertj.core.api.Assertions.assertThat;

class PublicInquiryRequestValidationTest {

    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void rejectsShortDescriptionAndMissingConsent() {
        PublicInquiryRequest request = new PublicInquiryRequest(
                "Alex Morgan", "alex@company.com", "Example Company", null, SOFTWARE_DEV,
                "Web App", null, null, "Too short", EMAIL, false);

        Set<String> fields = validator.validate(request).stream()
                .map(error -> error.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(fields).contains("projectDescription", "consent");
    }

    @Test
    void acceptsOptionalBlankWebsite() {
        PublicInquiryRequest request = new PublicInquiryRequest(
                "Alex Morgan", "alex@company.com", "Example Company", "", SOFTWARE_DEV,
                "Web App", null, null, "A sufficiently detailed project description.", EMAIL, true);

        assertThat(validator.validate(request)).isEmpty();
    }
}
