package com.ncba.countryinfo.repository;

import com.ncba.countryinfo.model.CountryInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryInfoRepository extends JpaRepository<CountryInfo, Long> {
    Optional<CountryInfo> findByIsoCode(String isoCode);
    Optional<CountryInfo> findByNameIgnoreCase(String name);
}
