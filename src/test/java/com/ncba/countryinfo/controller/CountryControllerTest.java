package com.ncba.countryinfo.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ncba.countryinfo.dto.CountryResponse;
import com.ncba.countryinfo.dto.LanguageDto;
import com.ncba.countryinfo.dto.UpsertResult;
import com.ncba.countryinfo.exception.ExternalServiceException;
import com.ncba.countryinfo.exception.ResourceNotFoundException;
import com.ncba.countryinfo.service.CountryService;
import com.ncba.countryinfo.web.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Plain MockMvc (no Spring context) so the test is fast and independent of DB / cache / AOP config. */
@ExtendWith(MockitoExtension.class)
class CountryControllerTest {

    @Mock CountryService service;
    MockMvc mvc;

    private static CountryResponse kenya() {
        return new CountryResponse(1L, "KE", "Kenya", "Nairobi", "254", "AF", "KES", "http://x/Kenya.jpg",
                List.of(new LanguageDto("swa", "Swahili")), Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CountryController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void postNewCountryReturns201WithLocation() throws Exception {
        when(service.lookupAndStore("kenya")).thenReturn(new UpsertResult(kenya(), true));

        mvc.perform(post("/api/v1/countries").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"kenya\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/countries/1")))
                .andExpect(jsonPath("$.isoCode").value("KE"))
                .andExpect(jsonPath("$.languages[0].name").value("Swahili"));
    }

    @Test
    void postExistingCountryReturns200() throws Exception {
        when(service.lookupAndStore("Kenya")).thenReturn(new UpsertResult(kenya(), false));

        mvc.perform(post("/api/v1/countries").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Kenya\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void postBlankNameReturns400WithDetails() throws Exception {
        mvc.perform(post("/api/v1/countries").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details", hasItem(containsString("name"))));
        verifyNoInteractions(service);
    }

    @Test
    void postMalformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/v1/countries").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postWrongContentTypeReturns415() throws Exception {
        mvc.perform(post("/api/v1/countries").contentType(MediaType.TEXT_PLAIN).content("kenya"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void postUnknownCountryReturns404() throws Exception {
        when(service.lookupAndStore("Narnia")).thenThrow(new ResourceNotFoundException("Country 'Narnia' not found"));

        mvc.perform(post("/api/v1/countries").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Narnia\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Country 'Narnia' not found"));
    }

    @Test
    void upstreamFailureReturns503WithRetryAfter() throws Exception {
        when(service.lookupAndStore("Kenya")).thenThrow(new ExternalServiceException("down"));

        mvc.perform(post("/api/v1/countries").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Kenya\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("down"))));
    }

    @Test
    void unexpectedErrorReturns500WithoutLeakingDetails() throws Exception {
        when(service.lookupAndStore("Kenya")).thenThrow(new IllegalStateException("secret internals"));

        mvc.perform(post("/api/v1/countries").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Kenya\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("secret internals"))));
    }

    @Test
    void getAllReturnsPage() throws Exception {
        when(service.findAll(any())).thenReturn(new PageImpl<>(List.of(kenya()), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Kenya"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void getByIdReturnsCountry() throws Exception {
        when(service.findById(1L)).thenReturn(kenya());

        mvc.perform(get("/api/v1/countries/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capitalCity").value("Nairobi"));
    }

    @Test
    void getByIdNotFoundReturns404() throws Exception {
        when(service.findById(99L)).thenThrow(new ResourceNotFoundException("Country with id 99 not found"));

        mvc.perform(get("/api/v1/countries/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/api/v1/countries/99"));
    }

    @Test
    void getByNonNumericIdReturns400() throws Exception {
        mvc.perform(get("/api/v1/countries/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void putUpdatesCountry() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(kenya());

        mvc.perform(put("/api/v1/countries/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kenya\",\"capitalCity\":\"Nairobi\",\"languages\":[{\"isoCode\":\"swa\",\"name\":\"Swahili\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kenya"));
    }

    @Test
    void putWithoutNameReturns400() throws Exception {
        mvc.perform(put("/api/v1/countries/1").contentType(MediaType.APPLICATION_JSON).content("{\"capitalCity\":\"X\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/v1/countries/1")).andExpect(status().isNoContent());
    }

    @Test
    void deleteMissingReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("nope")).when(service).delete(7L);

        mvc.perform(delete("/api/v1/countries/7")).andExpect(status().isNotFound());
    }
}
