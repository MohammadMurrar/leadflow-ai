package com.mohammadmurrar.leadflow.security;

import com.mohammadmurrar.leadflow.user.User;
import com.mohammadmurrar.leadflow.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserRepository repository;

    public DatabaseUserDetailsService(UserRepository repository) { this.repository = repository; }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        User user;
        try {
            user = repository.findByNormalizedEmail(User.normalizeEmail(email))
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        } catch (IllegalArgumentException exception) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        return new AuthenticatedPrincipal(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole(), user.getPasswordHash(), user.isEnabled());
    }
}
