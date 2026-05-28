package io.github.peterberghuis.goshuin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "goshuin_image")
@Getter
@Setter
@NoArgsConstructor
public class GoshuinImageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goshuin_id", nullable = false)
    private GoshuinEntity goshuin;

    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public GoshuinImageEntity(GoshuinEntity goshuin, String imageUrl) {
        this.goshuin = goshuin;
        this.imageUrl = imageUrl;
    }
}
