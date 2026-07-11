package com.crm.customer.address.controller;

import com.crm.customer.address.dto.CityResponse;
import com.crm.customer.address.dto.DistrictResponse;
import com.crm.customer.address.repository.CityRepository;
import com.crm.customer.address.repository.DistrictRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** City/district reference data for the cascading address dropdowns (FR-ADDR-02). */
@RestController
@RequestMapping("/api/cities")
@RequiredArgsConstructor
public class CityController {

    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;

    @GetMapping
    public ResponseEntity<List<CityResponse>> cities() {
        return ResponseEntity.ok(cityRepository.findActiveOrderByName().stream()
                .map(CityResponse::from).toList());
    }

    @GetMapping("/{cityId}/districts")
    public ResponseEntity<List<DistrictResponse>> districts(@PathVariable Long cityId) {
        return ResponseEntity.ok(districtRepository.findActiveByCityOrderByName(cityId).stream()
                .map(DistrictResponse::from).toList());
    }
}
