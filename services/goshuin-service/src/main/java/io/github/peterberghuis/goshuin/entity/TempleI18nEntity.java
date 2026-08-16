package io.github.peterberghuis.goshuin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "temple_i18n")
@Getter
@Setter
@NoArgsConstructor
public class TempleI18nEntity {

    @EmbeddedId
    private TempleI18nId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("templeId")
    @JoinColumn(name = "temple_id")
    private TempleEntity temple;

    @Column(nullable = false)
    private String name;

    @Column
    private String region;

    @Column(name = "postal_code")
    private String postalCode;

    @Column
    private String prefecture;

    @Column
    private String city;

    @Column
    private String address;

    @Column(columnDefinition = "TEXT")
    private String description;

    public TempleI18nEntity(TempleEntity temple, String locale, String name, String region, String postalCode, String prefecture, String city, String address, String description) {
        this.temple = temple;
        this.id = new TempleI18nId(temple.getId(), locale);
        this.name = name;
        this.region = region;
        this.postalCode = postalCode;
        this.prefecture = prefecture;
        this.city = city;
        this.address = address;
        this.description = description;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class TempleI18nId implements Serializable {
        @Column(name = "temple_id")
        private UUID templeId;

        @Column(name = "locale", length = 10)
        private String locale;

        public TempleI18nId(UUID templeId, String locale) {
            this.templeId = templeId;
            this.locale = locale;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TempleI18nId that = (TempleI18nId) o;
            return Objects.equals(templeId, that.templeId) && Objects.equals(locale, that.locale);
        }

        @Override
        public int hashCode() {
            return Objects.hash(templeId, locale);
        }
    }
}
