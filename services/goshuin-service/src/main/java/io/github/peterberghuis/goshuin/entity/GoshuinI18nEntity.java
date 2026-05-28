package io.github.peterberghuis.goshuin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "goshuin_i18n")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GoshuinI18nEntity {

    @EmbeddedId
    private GoshuinI18nId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("goshuinId")
    @JoinColumn(name = "goshuin_id")
    private GoshuinEntity goshuin;

    @Column(length = 50)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String description;

    public GoshuinI18nEntity(GoshuinEntity goshuin, String locale, String label, String description) {
        this.goshuin = goshuin;
        this.id = new GoshuinI18nId(goshuin.getId(), locale);
        this.label = label;
        this.description = description;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoshuinI18nId implements Serializable {
        @Column(name = "goshuin_id")
        private UUID goshuinId;

        @Column(length = 10)
        private String locale;
    }
}
