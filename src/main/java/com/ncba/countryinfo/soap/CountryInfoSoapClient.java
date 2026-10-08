package com.ncba.countryinfo.soap;

import com.ncba.countryinfo.config.SoapProperties;
import com.ncba.countryinfo.exception.ExternalServiceException;
import com.ncba.countryinfo.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Anti-corruption layer around the CountryInfoService SOAP API.
 * Builds SOAP 1.2 envelopes by hand and parses responses with a hardened DOM parser
 * (no code generation, no XXE). Protected by retry (outer) + circuit breaker (inner) + cache.
 */
@Slf4j
@Component
public class CountryInfoSoapClient {

    static final String NS = "http://www.oorsprong.org/websamples.countryinfo";
    private static final MediaType SOAP_12 = MediaType.parseMediaType("application/soap+xml;charset=UTF-8");

    private final RestClient restClient;
    private final SoapProperties props;

    public CountryInfoSoapClient(RestClient soapRestClient, SoapProperties props) {
        this.restClient = soapRestClient;
        this.props = props;
    }

    /** Step 4: sCountryName -> CountryISOCodeResult. */
    @Cacheable(cacheNames = "isoCodes", key = "#countryName.toLowerCase()")
    @CircuitBreaker(name = "countryInfoSoap")
    @Retry(name = "countryInfoSoap")
    public String getCountryIsoCode(String countryName) {
        String body = "<CountryISOCode xmlns=\"" + NS + "\"><sCountryName>" + xmlEscape(countryName)
                + "</sCountryName></CountryISOCode>";
        return parseIsoCode(call("CountryISOCode", body), countryName);
    }

    /** Step 5: sCountryISOCode -> FullCountryInfoResult. */
    @Cacheable(cacheNames = "fullCountryInfo", key = "#isoCode.toUpperCase()")
    @CircuitBreaker(name = "countryInfoSoap")
    @Retry(name = "countryInfoSoap")
    public SoapCountryInfo getFullCountryInfo(String isoCode) {
        String body = "<FullCountryInfo xmlns=\"" + NS + "\"><sCountryISOCode>" + xmlEscape(isoCode)
                + "</sCountryISOCode></FullCountryInfo>";
        return parseFullInfo(call("FullCountryInfo", body), isoCode);
    }

    private String call(String operation, String bodyXml) {
        String envelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Body>" + bodyXml + "</soap12:Body></soap12:Envelope>";
        long start = System.nanoTime();
        try {
            String response = restClient.post().uri(props.url())
                    .contentType(SOAP_12).accept(MediaType.ALL)
                    .body(envelope).retrieve().body(String.class);
            log.info("SOAP call ok operation={} durationMs={}", operation, (System.nanoTime() - start) / 1_000_000);
            if (response == null || response.isBlank()) {
                throw new ExternalServiceException("Empty response from SOAP service for " + operation);
            }
            return response;
        } catch (RestClientException e) {
            log.warn("SOAP call failed operation={} durationMs={} error={}", operation,
                    (System.nanoTime() - start) / 1_000_000, e.getMessage());
            throw new ExternalServiceException("Country info SOAP service unavailable (" + operation + ")", e);
        }
    }

    // ---------- parsing (package-private for unit tests) ----------

    static String parseIsoCode(String xml, String countryName) {
        Element el = firstElement(parse(xml), "CountryISOCodeResult");
        String iso = el == null ? null : el.getTextContent().trim();
        if (iso == null) throw new ExternalServiceException("Unexpected SOAP response: CountryISOCodeResult missing");
        if (iso.length() != 2) {
            // the service returns a sentence ("Country not found in the database") instead of a fault
            throw new ResourceNotFoundException("Country '" + countryName + "' not found");
        }
        return iso.toUpperCase();
    }

    static SoapCountryInfo parseFullInfo(String xml, String isoCode) {
        Element root = firstElement(parse(xml), "FullCountryInfoResult");
        if (root == null) throw new ExternalServiceException("Unexpected SOAP response: FullCountryInfoResult missing");
        String name = childText(root, "sName");
        if (name == null || name.isBlank() || name.toLowerCase().contains("not found")) {
            throw new ResourceNotFoundException("No country info found for ISO code '" + isoCode + "'");
        }
        List<SoapCountryInfo.SoapLanguage> languages = new ArrayList<>();
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element langs && "Languages".equals(langs.getLocalName())) {
                for (Node l = langs.getFirstChild(); l != null; l = l.getNextSibling()) {
                    if (l instanceof Element tl && "tLanguage".equals(tl.getLocalName())) {
                        languages.add(new SoapCountryInfo.SoapLanguage(childText(tl, "sISOCode"), childText(tl, "sName")));
                    }
                }
            }
        }
        return new SoapCountryInfo(childText(root, "sISOCode"), name, childText(root, "sCapitalCity"),
                childText(root, "sPhoneCode"), childText(root, "sContinentCode"),
                childText(root, "sCurrencyISOCode"), childText(root, "sCountryFlag"), languages);
    }

    private static Document parse(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new ExternalServiceException("Malformed SOAP response", e);
        }
    }

    private static Element firstElement(Document doc, String localName) {
        NodeList nl = doc.getElementsByTagNameNS(NS, localName);
        return nl.getLength() == 0 ? null : (Element) nl.item(0);
    }

    private static String childText(Element parent, String localName) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && localName.equals(e.getLocalName())) return e.getTextContent().trim();
        }
        return null;
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
