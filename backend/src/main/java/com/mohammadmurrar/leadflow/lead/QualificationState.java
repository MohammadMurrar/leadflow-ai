package com.mohammadmurrar.leadflow.lead;

import java.util.Set;

public enum QualificationState {
    PROCESSING(Set.of(LeadStatus.NEW, LeadStatus.QUALIFYING)),
    SUCCESSFULLY_QUALIFIED(Set.of(
            LeadStatus.QUALIFIED,
            LeadStatus.CONTACTED,
            LeadStatus.WON,
            LeadStatus.LOST)),
    FAILED(Set.of(LeadStatus.AUTOMATION_FAILED));

    private final Set<LeadStatus> statuses;

    QualificationState(Set<LeadStatus> statuses) {
        this.statuses = statuses;
    }

    public Set<LeadStatus> statuses() {
        return statuses;
    }
}
