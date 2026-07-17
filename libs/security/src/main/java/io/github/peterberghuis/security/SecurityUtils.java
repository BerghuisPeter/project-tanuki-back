package io.github.peterberghuis.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

public class SecurityUtils {

    private SecurityUtils() {
        // Utility class
    }

    public static UUID getUserIdFromContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }
        String userIdStr = (String) authentication.getCredentials();
        if (userIdStr == null || userIdStr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID not found in security context");
        }
        return UUID.fromString(userIdStr);
    }
}
