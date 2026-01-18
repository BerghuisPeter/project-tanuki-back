package io.github.peterberghuis.auth.dto;

import io.github.peterberghuis.auth.entity.UserRole;
import io.github.peterberghuis.auth.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private UUID id;
    private String email;
    private UserStatus status;
    private LocalDateTime createdAt;
    private Set<UserRole> roles;
}
