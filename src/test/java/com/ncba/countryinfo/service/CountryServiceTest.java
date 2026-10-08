package com.ncba.countryinfo.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ncba.countryinfo.dto.CountryUpdateRequest;
import com.ncba.countryinfo.dto.LanguageDto;
import com.ncba.countryinfo.dto.UpsertResult;
import com.ncba.countryinfo.exception.ExternalServiceException;
import com.ncba.countryinfo.exception.ResourceNotFoundException;
import com.ncba.countryinfo.model.CountryInfo;
import com.ncba.countryinfo.model.Language;
import com.ncba.countryinfo.repository.CountryInfoRepository;
import com.ncba.countryinfo.soap.CountryInfoSoapClient;
import com.ncba.countryinfo.soap.SoapCountryInfo;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class CountryServiceTest {

    @Mock CountryInfoRepository repository;
    @Mock CountryInfoSoapClient soap;
    @Mock PlatformTransactionManager txManager;
    CountryService service;

    private static SoapCountryInfo kenyaSoap() {
        return new SoapCountryInfo("KE", "Kenya", "Nairobi", "254", "AF", "KES", "http://x/Kenya.jpg",
                List.of(new SoapCountryInfo.SoapLanguage("swa", "Swahili")));
    }

    private static CountryInfo storedKenya() {
        CountryInfo c = new CountryInfo();
        c.setId(1L);
        c.setIsoCode("KE");
        c.setName("Kenya");
        c.setCapitalCity("Nairobi");
        c.addLanguage(new Language("eng", "English"));
        return c;
    }

    @BeforeEach
    void setUp() {
        service = new CountryService(repository, soap, txManager);
    }

    @Test
    void newCountryIsSentenceCasedLookedUpAndCreated() {
        when(soap.getCountryIsoCode("Kenya")).thenReturn("KE");
        when(soap.getFullCountryInfo("KE")).thenReturn(kenyaSoap());
        when(repository.findByIsoCode("KE")).thenReturn(Optional.empty());
        when(repository.save(any(CountryInfo.class))).thenAnswer(inv -> {
            CountryInfo c = inv.getArgument(0);
            c.setId(5L);
            return c;
        });

        UpsertResult result = service.lookupAndStore("  kENYA ");

        assertTrue(result.created());
        assertEquals(5L, result.country().id());
        assertEquals("KE", result.country().isoCode());
        assertEquals("Swahili", result.country().languages().get(0).name());
        verify(soap).getCountryIsoCode("Kenya");
    }

    @Test
    void existingCountryIsRefreshedNotDuplicated() {
        CountryInfo existing = storedKenya();
        when(soap.getCountryIsoCode("Kenya")).thenReturn("KE");
        when(soap.getFullCountryInfo("KE")).thenReturn(kenyaSoap());
        when(repository.findByIsoCode("KE")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        UpsertResult result = service.lookupAndStore("Kenya");

        assertFalse(result.created());
        assertEquals(1, existing.getLanguages().size());
        assertEquals("Swahili", existing.getLanguages().get(0).getName()); // old "English" replaced
    }

    @Test
    void upstreamDownFallsBackToStoredCopy() {
        when(soap.getCountryIsoCode("Kenya")).thenThrow(new ExternalServiceException("down"));
        when(repository.findByNameIgnoreCase("Kenya")).thenReturn(Optional.of(storedKenya()));

        UpsertResult result = service.lookupAndStore("kenya");

        assertFalse(result.created());
        assertEquals("Kenya", result.country().name());
        verify(repository, never()).save(any());
    }

    @Test
    void upstreamDownAndNothingStoredPropagates() {
        when(soap.getCountryIsoCode("Kenya")).thenThrow(new ExternalServiceException("down"));
        when(repository.findByNameIgnoreCase("Kenya")).thenReturn(Optional.empty());

        assertThrows(ExternalServiceException.class, () -> service.lookupAndStore("Kenya"));
    }

    @Test
    void unknownCountryPropagatesNotFoundWithoutFallback() {
        when(soap.getCountryIsoCode("Narnia")).thenThrow(new ResourceNotFoundException("Country 'Narnia' not found"));

        assertThrows(ResourceNotFoundException.class, () -> service.lookupAndStore("narnia"));
        verifyNoInteractions(repository);
        verify(soap, never()).getFullCountryInfo(any());
    }

    @Test
    void findByIdMissingThrowsNotFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findById(9L));
    }

    @Test
    void updateChangesFieldsAndReplacesLanguages() {
        CountryInfo c = storedKenya();
        when(repository.findById(1L)).thenReturn(Optional.of(c));
        when(repository.saveAndFlush(c)).thenReturn(c);

        var res = service.update(1L, new CountryUpdateRequest("  KENYA REPUBLIC", "Nairobi City", "254", "AF", "KES",
                "flag", List.of(new LanguageDto("swa", "Swahili"))));

        assertEquals("Kenya republic", res.name());
        assertEquals("Nairobi City", res.capitalCity());
        assertEquals(1, c.getLanguages().size());
        assertEquals("swa", c.getLanguages().get(0).getIsoCode());
    }

    @Test
    void updateWithNullLanguagesKeepsExisting() {
        CountryInfo c = storedKenya();
        when(repository.findById(1L)).thenReturn(Optional.of(c));
        when(repository.saveAndFlush(c)).thenReturn(c);

        service.update(1L, new CountryUpdateRequest("Kenya", null, null, null, null, null, null));

        assertEquals("English", c.getLanguages().get(0).getName());
    }

    @Test
    void updateMissingThrowsNotFound() {
        when(repository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.update(2L,
                new CountryUpdateRequest("X", null, null, null, null, null, null)));
    }

    @Test
    void deleteRemovesEntity() {
        CountryInfo c = storedKenya();
        when(repository.findById(1L)).thenReturn(Optional.of(c));
        service.delete(1L);
        verify(repository).delete(c);
    }

    @Test
    void deleteMissingThrowsNotFound() {
        when(repository.findById(3L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.delete(3L));
        verify(repository, never()).delete(any());
    }
}
