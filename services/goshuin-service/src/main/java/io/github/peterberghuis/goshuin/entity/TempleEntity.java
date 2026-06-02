package io.github.peterberghuis.goshuin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "temple")
@Getter
@Setter
public class TempleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "affiliation_type", nullable = false, length = 50)
    private String affiliationType;

    @Column(name = "website_url", length = 2048)
    private String websiteUrl;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "goshuin_type", length = 50)
    private String goshuinType;

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
    private List<TempleI18nEntity> translations = new ArrayList<>();
}
