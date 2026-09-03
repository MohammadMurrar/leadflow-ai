package com.mohammadmurrar.leadflow.passwordreset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetSessionService {
    private final JdbcTemplate jdbc;

    public PasswordResetSessionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int deleteByPrincipalName(String principalName) {
        return jdbc.update("delete from SPRING_SESSION where PRINCIPAL_NAME = ?", principalName);
    }
}
