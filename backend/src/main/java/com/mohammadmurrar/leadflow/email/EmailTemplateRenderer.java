package com.mohammadmurrar.leadflow.email;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

@Component
public class EmailTemplateRenderer {
    private final EmailDeliveryProperties properties;

    public EmailTemplateRenderer(EmailDeliveryProperties properties) {
        this.properties = properties;
    }

    public RenderedEmail render(EmailTemplateType type) {
        URI base = properties.validatedBaseUri();
        return switch (type) {
            case NEW_INQUIRY -> new RenderedEmail("New inquiry received",
                    message("A new inquiry is ready for review.", base.resolve("/leads")));
            case QUALIFICATION_COMPLETED -> new RenderedEmail("Lead qualification completed",
                    message("A lead qualification has completed.", base.resolve("/ai-qualification")));
            case QUALIFICATION_NEEDS_ATTENTION -> new RenderedEmail("Lead qualification needs attention",
                    message("A lead qualification needs administrator attention.",
                            base.resolve("/ai-qualification")));
            case PASSWORD_CHANGED -> new RenderedEmail("Your LeadFlow password was changed",
                    message("Your LeadFlow administrator password was changed.", base));
            case PASSWORD_RESET -> throw new IllegalArgumentException("Password reset token is required");
        };
    }

    public RenderedEmail render(ClaimedEmail email) {
        URI base = properties.validatedBaseUri();
        URI dashboard = base.resolve("/leads");
        return switch (email.templateType()) {
            case NEW_INQUIRY -> new RenderedEmail("New inquiry received",
                    message(lines("New inquiry received", "Name: " + email.leadFullName(),
                            optional("Company: ", email.company()),
                            "Requested service: " + email.requestedService(),
                            optional("Estimated budget: ", formatMoney(email))), dashboard));
            case QUALIFICATION_COMPLETED -> new RenderedEmail("Lead qualification completed",
                    message(lines("Lead qualification completed", "Name: " + email.leadFullName(),
                            "Requested service: " + email.requestedService(),
                            "Qualification score: " + email.qualificationScore(),
                            "Priority: " + email.priority()), dashboard));
            case QUALIFICATION_NEEDS_ATTENTION -> new RenderedEmail(
                    "Lead qualification needs attention",
                    message(lines("Lead qualification needs attention", "Name: " + email.leadFullName(),
                            "Requested service: " + email.requestedService(),
                            "Automated qualification did not complete."), dashboard));
            case PASSWORD_CHANGED -> render(EmailTemplateType.PASSWORD_CHANGED);
            case PASSWORD_RESET -> throw new IllegalArgumentException("Password reset token is required");
        };
    }

    public RenderedEmail renderPasswordReset(String encodedToken) {
        URI base = properties.validatedBaseUri();
        if (encodedToken == null || !encodedToken.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("Password reset token is invalid");
        }
        String link = base.toString() + "/reset-password#token=" + encodedToken;
        String body = "Reset your LeadFlow administrator password" + System.lineSeparator()
                + System.lineSeparator()
                + "Use the secure link below to reset your password. This link expires in 30 minutes."
                + System.lineSeparator() + System.lineSeparator() + link
                + System.lineSeparator() + System.lineSeparator()
                + "If you did not request this, ignore this message.";
        return new RenderedEmail("Reset your LeadFlow password", body);
    }

    private String lines(String... values) {
        return java.util.Arrays.stream(values).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    }

    private String optional(String prefix, String value) {
        return value == null ? null : prefix + value;
    }

    private String formatMoney(ClaimedEmail email) {
        if (email.estimatedBudget() == null || email.currency() == null) return null;
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.US);
        formatter.setCurrency(Currency.getInstance(email.currency()));
        return formatter.format(email.estimatedBudget());
    }

    private String message(String copy, URI destination) {
        return copy + System.lineSeparator() + System.lineSeparator()
                + "Open LeadFlow: " + destination;
    }
}
