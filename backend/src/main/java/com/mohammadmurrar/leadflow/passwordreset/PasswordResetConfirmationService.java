package com.mohammadmurrar.leadflow.passwordreset;

import com.mohammadmurrar.leadflow.user.User;
import com.mohammadmurrar.leadflow.user.UserRepository;
import com.mohammadmurrar.leadflow.user.UserRole;
import com.mohammadmurrar.leadflow.security.PasswordPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.ConcurrencyFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;

@Service
public class PasswordResetConfirmationService {
    public static final String INVALID_TOKEN_MESSAGE =
            "This password reset link is invalid or has expired.";
    private final PasswordResetTokenService tokens;
    private final PasswordResetRequestRepository requests;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetSessionService sessions;
    private final Clock clock;

    @Autowired
    public PasswordResetConfirmationService(PasswordResetTokenService tokens,
            PasswordResetRequestRepository requests, UserRepository users,
            PasswordEncoder passwordEncoder, PasswordResetSessionService sessions) {
        this(tokens, requests, users, passwordEncoder, sessions, Clock.systemUTC());
    }

    PasswordResetConfirmationService(PasswordResetTokenService tokens,
            PasswordResetRequestRepository requests, UserRepository users,
            PasswordEncoder passwordEncoder, PasswordResetSessionService sessions, Clock clock) {
        this.tokens = tokens;
        this.requests = requests;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional
    public void confirm(String encodedToken, String newPassword) {
        confirm(encodedToken, newPassword, clock.instant());
    }

    @Transactional
    void confirm(String encodedToken, String newPassword, Instant confirmedAt) {
        byte[] tokenBytes = null;
        byte[] tokenHash = null;
        try {
            tokenBytes = tokens.decodeSubmitted(encodedToken);
            tokenHash = tokens.storedHash(tokenBytes);
            PasswordResetRequest candidate = requests.findByTokenHash(tokenHash)
                    .orElseThrow(InvalidPasswordResetException::new);
            User user = users.findByIdForUpdate(candidate.getUser().getId())
                    .orElseThrow(InvalidPasswordResetException::new);
            if (!user.isEnabled() || user.getRole() != UserRole.ADMIN) throw invalid();
            PasswordResetRequest request = requests.findByIdForUpdate(candidate.getId())
                    .orElseThrow(InvalidPasswordResetException::new);
            Instant now = Objects.requireNonNull(confirmedAt).truncatedTo(ChronoUnit.MICROS);
            if (request.getWorkspace() == null || user.getWorkspace() == null
                    || !request.getWorkspace().getId().equals(user.getWorkspace().getId())
                    || !request.getUser().getId().equals(user.getId())
                    || !tokens.matches(request.getTokenHash(), tokenBytes)
                    || !request.isActiveAt(now)) throw invalid();

            validatePassword(newPassword, user);
            String encodedPassword = passwordEncoder.encode(newPassword);
            if (!user.changePasswordHash(encodedPassword)) throw new InvalidPasswordException(
                    "Choose a password you have not used before");
            if (!request.consume(now)) throw invalid();
            sessions.deleteByPrincipalName(user.getNormalizedEmail());
            users.flush();
            requests.flush();
        } catch (PasswordResetTokenService.InvalidPasswordResetTokenException exception) {
            throw invalid();
        } catch (ConcurrencyFailureException exception) {
            throw invalid();
        } finally {
            if (tokenBytes != null) Arrays.fill(tokenBytes, (byte) 0);
            if (tokenHash != null) Arrays.fill(tokenHash, (byte) 0);
        }
    }

    private void validatePassword(String password, User user) {
        try {
            PasswordPolicy.validateNewPassword(password, user.getNormalizedEmail());
        } catch (PasswordPolicy.InvalidPasswordException exception) {
            if (exception.reason() == PasswordPolicy.Reason.EMAIL) {
                throw new InvalidPasswordException("Password must not contain the account email");
            }
            throw new InvalidPasswordException(
                    "Use 12 to 128 characters with uppercase, lowercase, number, and symbol");
        }
        try {
            if (passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new InvalidPasswordException("Choose a password you have not used before");
            }
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static InvalidPasswordResetException invalid() {
        return new InvalidPasswordResetException();
    }

    public static final class InvalidPasswordResetException extends RuntimeException {
        public InvalidPasswordResetException() { super(INVALID_TOKEN_MESSAGE); }
    }

    public static final class InvalidPasswordException extends RuntimeException {
        public InvalidPasswordException(String message) { super(message); }
    }
}
