package com.ncba.countryinfo.dto;

public record UpsertResult(CountryResponse country, boolean created) {
}
