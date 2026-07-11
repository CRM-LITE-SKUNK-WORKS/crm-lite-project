package com.crm.lookup.catalog.controller;

import com.crm.lookup.catalog.dto.LookupStatusResponse;
import com.crm.lookup.catalog.dto.LookupTypeResponse;
import com.crm.lookup.catalog.repository.GeneralStatusRepository;
import com.crm.lookup.catalog.repository.GeneralTypeRepository;
import com.crm.lookup.common.exception.LookupNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only API over the shared GNL_ST / GNL_TP catalogs (ADR-002).
 * Soft-deleted catalog rows are never returned.
 */
@RestController
@RequestMapping("/api/lookups")
@RequiredArgsConstructor
public class LookupController {

    private final GeneralStatusRepository statusRepository;
    private final GeneralTypeRepository typeRepository;

    @GetMapping("/statuses")
    public List<LookupStatusResponse> statuses(@RequestParam(required = false) String domain) {
        List<com.crm.lookup.catalog.entity.GeneralStatus> rows = StringUtils.hasText(domain)
                ? statusRepository.findByStatusDomainAndDeletedDateIsNullOrderById(domain)
                : statusRepository.findByDeletedDateIsNullOrderById();
        return rows.stream().map(LookupStatusResponse::from).toList();
    }

    @GetMapping("/statuses/{shortCode}")
    public LookupStatusResponse status(@PathVariable String shortCode) {
        return statusRepository.findByShortCodeAndDeletedDateIsNull(shortCode)
                .map(LookupStatusResponse::from)
                .orElseThrow(() -> new LookupNotFoundException("Unknown status code: " + shortCode));
    }

    @GetMapping("/types")
    public List<LookupTypeResponse> types(@RequestParam(required = false) String domain) {
        List<com.crm.lookup.catalog.entity.GeneralType> rows = StringUtils.hasText(domain)
                ? typeRepository.findByTypeDomainAndDeletedDateIsNullOrderById(domain)
                : typeRepository.findByDeletedDateIsNullOrderById();
        return rows.stream().map(LookupTypeResponse::from).toList();
    }

    @GetMapping("/types/{shortCode}")
    public LookupTypeResponse type(@PathVariable String shortCode) {
        return typeRepository.findByShortCodeAndDeletedDateIsNull(shortCode)
                .map(LookupTypeResponse::from)
                .orElseThrow(() -> new LookupNotFoundException("Unknown type code: " + shortCode));
    }
}
