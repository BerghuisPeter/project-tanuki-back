package io.github.peterberghuis.profile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "user_preferences", schema = "profile_schema")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "display_name", length = 45)
    private String displayName;

    @Column(name = "user_color")
    private String color;

    @Column(name = "locale", nullable = false)
    private String locale;

    @Column(name = "avatar_url")
    private String avatarUrl;
}
