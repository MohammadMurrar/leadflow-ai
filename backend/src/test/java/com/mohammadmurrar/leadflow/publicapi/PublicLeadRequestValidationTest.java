package com.mohammadmurrar.leadflow.publicapi;

import com.mohammadmurrar.leadflow.publicapi.api.PublicLeadRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PublicLeadRequestValidationTest {
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void normalizesAllTextFieldsAndBlankOptionalValues() {
        PublicLeadRequest request = request(
                "  Alex Morgan  ", "  alex@example.com  ", "  +1 555 0100  ",
                "  Northstar Services  ", "  12345678901234567890  ", "  blank-check  ",
                BigDecimal.ZERO, LocalDate.now());

        assertThat(request.fullName()).isEqualTo("Alex Morgan");
        assertThat(request.email()).isEqualTo("alex@example.com");
        assertThat(request.phone()).isEqualTo("+1 555 0100");
        assertThat(request.company()).isEqualTo("Northstar Services");
        assertThat(request.message()).isEqualTo("12345678901234567890");
        assertThat(request.website()).isEqualTo("blank-check");

        PublicLeadRequest blankOptional = request(
                "Alex Morgan", "alex@example.com", " \t ", "   ",
                "12345678901234567890", "   ", BigDecimal.ZERO, LocalDate.now());
        assertThat(blankOptional.phone()).isNull();
        assertThat(blankOptional.company()).isNull();
        assertThat(blankOptional.website()).isEmpty();
    }

    @Test
    void validatesTrimmedRequiredTextAndEmail() {
        assertInvalid(request("   ", "alex@example.com", null, null,
                "12345678901234567890", null, BigDecimal.ZERO, LocalDate.now()), "fullName");
        assertInvalid(request("Alex Morgan", "   ", null, null,
                "12345678901234567890", null, BigDecimal.ZERO, LocalDate.now()), "email");
        assertInvalid(request("Alex Morgan", "alex@example.com", null, null,
                "    nineteen-characters    ", null, BigDecimal.ZERO, LocalDate.now()), "message");

        PublicLeadRequest valid = request("  Alex Morgan  ", "  alex@example.com  ", null, null,
                "  12345678901234567890  ", null, BigDecimal.ZERO, LocalDate.now());
        assertThat(violations(valid)).isEmpty();
        assertThat(valid.message()).hasSize(20);
    }

    @Test
    void validatesDesiredStartDate() {
        assertInvalid(validRequest(BigDecimal.ZERO, LocalDate.now().minusDays(1)), "desiredStartDate");
        assertThat(violations(validRequest(BigDecimal.ZERO, LocalDate.now()))).isEmpty();
        assertThat(violations(validRequest(BigDecimal.ZERO, LocalDate.now().plusDays(1)))).isEmpty();
    }

    @Test
    void validatesBudgetRangeAndPrecision() {
        assertInvalid(validRequest(new BigDecimal("-0.01"), LocalDate.now()), "estimatedBudget");
        assertThat(violations(validRequest(BigDecimal.ZERO, LocalDate.now()))).isEmpty();
        assertInvalid(validRequest(new BigDecimal("10000000000"), LocalDate.now()), "estimatedBudget");
        assertInvalid(validRequest(new BigDecimal("1.001"), LocalDate.now()), "estimatedBudget");
    }

    @Test
    void validatesHoneypotMaximumLength() {
        PublicLeadRequest request = request("Alex Morgan", "alex@example.com", null, null,
                "12345678901234567890", "x".repeat(201), BigDecimal.ZERO, LocalDate.now());

        assertInvalid(request, "website");
    }

    private PublicLeadRequest validRequest(BigDecimal budget, LocalDate desiredStartDate) {
        return request("Alex Morgan", "alex@example.com", null, null,
                "12345678901234567890", null, budget, desiredStartDate);
    }

    private PublicLeadRequest request(String fullName, String email, String phone, String company,
            String message, String website, BigDecimal budget, LocalDate desiredStartDate) {
        return new PublicLeadRequest(fullName, email, phone, company, UUID.randomUUID(), budget,
                desiredStartDate, message, website);
    }

    private Set<ConstraintViolation<PublicLeadRequest>> violations(PublicLeadRequest request) {
        return validator.validate(request);
    }

    private void assertInvalid(PublicLeadRequest request, String property) {
        assertThat(violations(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(property);
    }
}
