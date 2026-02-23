package io.github.peterberghuis.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "oauth2_codes", schema = "auth_schema")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2Code {

    @Id
    private String code;

    @Column(nullable = false)
    private String email;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;
}
