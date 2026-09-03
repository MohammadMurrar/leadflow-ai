package com.mohammadmurrar.leadflow.settings;

import java.util.Set;

public enum SupportedCurrency {
    USD, EUR, ILS, JOD, SAR, AED, GBP, KWD, QAR, EGP;

    private static final Set<String> CODES = Set.of(
            "USD", "EUR", "ILS", "JOD", "SAR", "AED", "GBP", "KWD", "QAR", "EGP");

    public static boolean contains(String code) {
        return code != null && CODES.contains(code);
    }
}
