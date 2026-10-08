package com.ncba.countryinfo.soap;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ncba.countryinfo.config.SoapProperties;
import com.ncba.countryinfo.exception.ExternalServiceException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Exercises the real HTTP/XML path against a mock server (no AOP, so no retry/cache here). */
class CountryInfoSoapClientTest {

    private static final String URL = "http://soap.test/CountryInfoService.wso";
    private static final String NS = "http://www.oorsprong.org/websamples.countryinfo";

    MockRestServiceServer server;
    CountryInfoSoapClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CountryInfoSoapClient(builder.build(),
                new SoapProperties(URL, Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    @Test
    void sendsCountryNameAndParsesIsoCode() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("<sCountryName>Kenya</sCountryName>")))
                .andRespond(withSuccess("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"><soap:Body>"
                        + "<m:CountryISOCodeResponse xmlns:m=\"" + NS + "\"><m:CountryISOCodeResult>KE</m:CountryISOCodeResult>"
                        + "</m:CountryISOCodeResponse></soap:Body></soap:Envelope>", MediaType.APPLICATION_XML));

        assertEquals("KE", client.getCountryIsoCode("Kenya"));
        server.verify();
    }

    @Test
    void escapesXmlInInput() {
        server.expect(requestTo(URL))
                .andExpect(content().string(containsString("&lt;b&gt;&amp;")))
                .andRespond(withSuccess("<a xmlns=\"" + NS + "\"/>", MediaType.APPLICATION_XML));

        // body has no CountryISOCodeResult, so parsing fails, but the request assertion above is what matters here
        assertThrows(ExternalServiceException.class, () -> client.getCountryIsoCode("<b>&"));
        server.verify();
    }

    @Test
    void serverErrorBecomesExternalServiceException() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThrows(ExternalServiceException.class, () -> client.getCountryIsoCode("Kenya"));
    }

    @Test
    void malformedXmlBecomesExternalServiceException() {
        server.expect(requestTo(URL)).andRespond(withSuccess("<not-closed", MediaType.APPLICATION_XML));
        assertThrows(ExternalServiceException.class, () -> client.getCountryIsoCode("Kenya"));
    }

    @Test
    void doctypeIsRejectedXxeProtection() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><a>&x;</a>";
        server.expect(requestTo(URL)).andRespond(withSuccess(xxe, MediaType.APPLICATION_XML));
        assertThrows(ExternalServiceException.class, () -> client.getCountryIsoCode("Kenya"));
    }

    @Test
    void fullInfoRequestUsesIsoCodeElement() {
        server.expect(requestTo(URL))
                .andExpect(content().string(containsString("<sCountryISOCode>KE</sCountryISOCode>")))
                .andRespond(withSuccess("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"><soap:Body>"
                        + "<m:FullCountryInfoResponse xmlns:m=\"" + NS + "\"><m:FullCountryInfoResult>"
                        + "<m:sISOCode>KE</m:sISOCode><m:sName>Kenya</m:sName><m:sCapitalCity>Nairobi</m:sCapitalCity>"
                        + "</m:FullCountryInfoResult></m:FullCountryInfoResponse></soap:Body></soap:Envelope>",
                        MediaType.APPLICATION_XML));

        SoapCountryInfo info = client.getFullCountryInfo("KE");
        assertEquals("Nairobi", info.capitalCity());
        assertTrue(info.languages().isEmpty());
    }
}
