package com.mohammadmurrar.leadflow.provisioning;

import com.mohammadmurrar.leadflow.LeadFlowApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.Map;

public final class WorkspaceProvisioningApplication {
    private WorkspaceProvisioningApplication() {}

    public static void main(String[] args) {
        new SpringApplicationBuilder(LeadFlowApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("provisioning")
                .properties(Map.of(
                        "spring.main.web-application-type", "none",
                        "leadflow.bootstrap.enabled", "false",
                        "leadflow.email-delivery.enabled", "false",
                        "leadflow.qualification-reliability.dispatcher-enabled", "false",
                        "leadflow.qualification-reliability.retry-enabled", "false",
                        "leadflow.qualification-reliability.legacy-callback-enabled", "false"))
                .run(args);
    }
}
