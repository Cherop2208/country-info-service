package com.ncba.countryinfo.soap;

import static org.junit.jupiter.api.Assertions.*;

import com.ncba.countryinfo.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

class SoapParsingTest {

    private static final String WRAP = "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"><soap:Body>%s</soap:Body></soap:Envelope>";
    private static final String NS = "http://www.oorsprong.org/websamples.countryinfo";

    @Test
    void parsesIsoCode() {
        String xml = WRAP.formatted("<m:CountryISOCodeResponse xmlns:m=\"" + NS + "\"><m:CountryISOCodeResult>KE</m:CountryISOCodeResult></m:CountryISOCodeResponse>");
        assertEquals("KE", CountryInfoSoapClient.parseIsoCode(xml, "Kenya"));
    }

    @Test
    void unknownCountryIsNotFound() {
        String xml = WRAP.formatted("<m:CountryISOCodeResponse xmlns:m=\"" + NS + "\"><m:CountryISOCodeResult>Country not found in the database</m:CountryISOCodeResult></m:CountryISOCodeResponse>");
        assertThrows(ResourceNotFoundException.class, () -> CountryInfoSoapClient.parseIsoCode(xml, "Narnia"));
    }

    @Test
    void parsesFullInfoWithLanguages() {
        String xml = WRAP.formatted("<m:FullCountryInfoResponse xmlns:m=\"" + NS + "\"><m:FullCountryInfoResult>"
                + "<m:sISOCode>KE</m:sISOCode><m:sName>Kenya</m:sName><m:sCapitalCity>Nairobi</m:sCapitalCity>"
                + "<m:sPhoneCode>254</m:sPhoneCode><m:sContinentCode>AF</m:sContinentCode>"
                + "<m:sCurrencyISOCode>KES</m:sCurrencyISOCode><m:sCountryFlag>http://x/Kenya.jpg</m:sCountryFlag>"
                + "<m:Languages><m:tLanguage><m:sISOCode>swa</m:sISOCode><m:sName>Swahili</m:sName></m:tLanguage></m:Languages>"
                + "</m:FullCountryInfoResult></m:FullCountryInfoResponse>");
        SoapCountryInfo info = CountryInfoSoapClient.parseFullInfo(xml, "KE");
        assertEquals("Nairobi", info.capitalCity());
        assertEquals(1, info.languages().size());
        assertEquals("Swahili", info.languages().get(0).name());
    }
}
