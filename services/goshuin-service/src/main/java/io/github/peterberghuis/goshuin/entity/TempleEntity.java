package io.github.peterberghuis.goshuin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "temple")
@Getter
@Setter
public class TempleEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private Double latitude;

    @Column(name = "affiliation_type", nullable = false, length = 50)
    private String affiliationType;

    @Column(name = "website_url", length = 2048)
    private String websiteUrl;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;


    @Column(name = "goshuin_service_open_until")
    private LocalTime goshuinServiceOpenUntil;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "temple", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<TempleI18nEntity> translations = new LinkedHashSet<>();
}
