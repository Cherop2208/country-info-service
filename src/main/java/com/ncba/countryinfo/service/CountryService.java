package com.ncba.countryinfo.service;

import com.ncba.countryinfo.dto.CountryResponse;
import com.ncba.countryinfo.dto.CountryUpdateRequest;
import com.ncba.countryinfo.dto.UpsertResult;
import com.ncba.countryinfo.exception.ExternalServiceException;
import com.ncba.countryinfo.exception.ResourceNotFoundException;
import com.ncba.countryinfo.model.CountryInfo;
import com.ncba.countryinfo.model.Language;
import com.ncba.countryinfo.repository.CountryInfoRepository;
import com.ncba.countryinfo.soap.CountryInfoSoapClient;
import com.ncba.countryinfo.soap.SoapCountryInfo;
import com.ncba.countryinfo.util.SentenceCase;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class CountryService {

    private final CountryInfoRepository repository;
    private final CountryInfoSoapClient soapClient;
    private final TransactionTemplate tx;

    public CountryService(CountryInfoRepository repository, CountryInfoSoapClient soapClient,
                          PlatformTransactionManager txManager) {
        this.repository = repository;
        this.soapClient = soapClient;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Steps 3-6: sentence-case the name, call SOAP (name -> ISO -> full info) and upsert by ISO code.
     * The SOAP calls run OUTSIDE any DB transaction so slow upstream calls never hold a DB connection.
     * Fallback: if the upstream is down / circuit open and we already stored this country, serve the stored copy.
     */
    public UpsertResult lookupAndStore(String rawName) {
        String name = SentenceCase.of(rawName);
        log.info("Country lookup requested name={}", name);

        final SoapCountryInfo info;
        try {
            String iso = soapClient.getCountryIsoCode(name);
            info = soapClient.getFullCountryInfo(iso);
        } catch (ExternalServiceException | CallNotPermittedException ex) {
            log.warn("Upstream unavailable for name={}, trying stored copy: {}", name, ex.getMessage());
            return tx.execute(status -> repository.findByNameIgnoreCase(name)
                    .map(c -> new UpsertResult(CountryMapper.toResponse(c), false))
                    .orElseThrow(() -> ex));
        }

        return tx.execute(status -> {
            CountryInfo entity = repository.findByIsoCode(info.isoCode()).orElse(null);
            boolean created = entity == null;
            if (created) entity = new CountryInfo();
            CountryMapper.apply(entity, info);
            CountryInfo saved = repository.save(entity);
            log.info("Country {} id={} iso={}", created ? "created" : "refreshed", saved.getId(), saved.getIsoCode());
            return new UpsertResult(CountryMapper.toResponse(saved), created);
        });
    }

    @Transactional(readOnly = true)
    public Page<CountryResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(CountryMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CountryResponse findById(Long id) {
        return CountryMapper.toResponse(getOrThrow(id));
    }

    @Transactional
    public CountryResponse update(Long id, CountryUpdateRequest req) {
        CountryInfo c = getOrThrow(id);
        c.setName(SentenceCase.of(req.name()));
        c.setCapitalCity(req.capitalCity());
        c.setPhoneCode(req.phoneCode());
        c.setContinentCode(req.continentCode());
        c.setCurrencyIsoCode(req.currencyIsoCode());
        c.setCountryFlag(req.countryFlag());
        if (req.languages() != null) {
            c.getLanguages().clear();
            req.languages().forEach(l -> c.addLanguage(new Language(l.isoCode(), l.name())));
        }
        log.info("Country updated id={}", id);
        return CountryMapper.toResponse(repository.saveAndFlush(c));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getOrThrow(id));
        log.info("Country deleted id={}", id);
    }

    private CountryInfo getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Country with id " + id + " not found"));
    }
}
