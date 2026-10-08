package com.ncba.countryinfo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CountryUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String capitalCity,
        @Size(max = 20) String phoneCode,
        @Size(max = 10) String continentCode,
        @Size(max = 10) String currencyIsoCode,
        @Size(max = 500) String countryFlag,
        @Valid List<LanguageDto> languages) {
}
