package com.ncba.countryinfo.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "country_info", uniqueConstraints = @UniqueConstraint(name = "uk_country_iso", columnNames = "iso_code"))
@Getter
@Setter
@NoArgsConstructor
public class CountryInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "iso_code", nullable = false, length = 2)
    private String isoCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "capital_city", length = 100)
    private String capitalCity;

    @Column(name = "phone_code", length = 20)
    private String phoneCode;

    @Column(name = "continent_code", length = 10)
    private String continentCode;

    @Column(name = "currency_iso_code", length = 10)
    private String currencyIsoCode;

    @Column(name = "country_flag", length = 500)
    private String countryFlag;

    @OneToMany(mappedBy = "country", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    private List<Language> languages = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public void addLanguage(Language language) {
        language.setCountry(this);
        languages.add(language);
    }
}
