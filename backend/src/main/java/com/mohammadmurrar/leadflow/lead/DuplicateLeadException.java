package com.mohammadmurrar.leadflow.lead;

import com.mohammadmurrar.leadflow.common.ConflictException;

public class DuplicateLeadException extends ConflictException {
    public DuplicateLeadException() {
        super("A recent lead already exists for this email");
    }
}
