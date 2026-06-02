package io.github.peterberghuis.goshuin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "goshuin")
@Getter
@Setter
@NoArgsConstructor
public class GoshuinEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String format;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "temple_id", nullable = false)
    private TempleEntity temple;

    @Column(nullable = false)
    private Integer pages = 1;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "goshuin", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoshuinI18nEntity> translations = new ArrayList<>();

    @OneToMany(mappedBy = "goshuin", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoshuinImageEntity> images = new ArrayList<>();
}
