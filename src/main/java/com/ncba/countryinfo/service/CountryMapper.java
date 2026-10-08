package com.ncba.countryinfo.service;

import com.ncba.countryinfo.dto.CountryResponse;
import com.ncba.countryinfo.dto.LanguageDto;
import com.ncba.countryinfo.model.CountryInfo;
import com.ncba.countryinfo.model.Language;
import com.ncba.countryinfo.soap.SoapCountryInfo;
import java.util.List;

final class CountryMapper {
    private CountryMapper() {}

    static CountryResponse toResponse(CountryInfo c) {
        List<LanguageDto> langs = c.getLanguages().stream()
                .map(l -> new LanguageDto(l.getIsoCode(), l.getName())).toList();
        return new CountryResponse(c.getId(), c.getIsoCode(), c.getName(), c.getCapitalCity(), c.getPhoneCode(),
                c.getContinentCode(), c.getCurrencyIsoCode(), c.getCountryFlag(), langs,
                c.getCreatedAt(), c.getUpdatedAt());
    }

    static void apply(CountryInfo c, SoapCountryInfo s) {
        c.setIsoCode(s.isoCode());
        c.setName(s.name());
        c.setCapitalCity(s.capitalCity());
        c.setPhoneCode(s.phoneCode());
        c.setContinentCode(s.continentCode());
        c.setCurrencyIsoCode(s.currencyIsoCode());
        c.setCountryFlag(s.countryFlag());
        c.getLanguages().clear();
        s.languages().forEach(l -> c.addLanguage(new Language(l.isoCode(), l.name())));
    }
}
