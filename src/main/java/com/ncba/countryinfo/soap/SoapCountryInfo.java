package com.ncba.countryinfo.soap;

import java.util.List;

public record SoapCountryInfo(String isoCode, String name, String capitalCity, String phoneCode,
                              String continentCode, String currencyIsoCode, String countryFlag,
                              List<SoapLanguage> languages) {
    public record SoapLanguage(String isoCode, String name) {}
}
