package com.ncba.countryinfo.controller;

import com.ncba.countryinfo.dto.*;
import com.ncba.countryinfo.service.CountryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Countries", description = "Look up countries via the SOAP provider and manage the stored copies")
@RestController
@RequestMapping("/api/v1/countries")
@RequiredArgsConstructor
public class CountryController {

    private final CountryService service;

    /** POST {"name":"kenya"} -> 201 (new) or 200 (already stored, refreshed). */
    @Operation(summary = "Look up a country by name (SOAP) and store it",
            description = "Name is converted to sentence case, resolved to an ISO code, then full info is fetched and upserted. "
                    + "201 = newly stored, 200 = already stored (refreshed or served from storage if the provider is down), "
                    + "404 = unknown country, 503 = provider unavailable.")
    @PostMapping
    public ResponseEntity<CountryResponse> lookupAndSave(@Valid @RequestBody CountryRequest request) {
        UpsertResult result = service.lookupAndStore(request.name());
        if (result.created()) {
            URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                    .buildAndExpand(result.country().id()).toUri();
            return ResponseEntity.created(location).body(result.country());
        }
        return ResponseEntity.ok(result.country());
    }

    @Operation(summary = "List stored countries (paged: page, size<=100, sort)")
    @GetMapping
    public PageResponse<CountryResponse> findAll(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return PageResponse.from(service.findAll(pageable));
    }

    @Operation(summary = "Get a stored country by id")
    @GetMapping("/{id}")
    public CountryResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @Operation(summary = "Update a stored country (languages are replaced when supplied)")
    @PutMapping("/{id}")
    public CountryResponse update(@PathVariable Long id, @Valid @RequestBody CountryUpdateRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Delete a stored country and its languages")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
