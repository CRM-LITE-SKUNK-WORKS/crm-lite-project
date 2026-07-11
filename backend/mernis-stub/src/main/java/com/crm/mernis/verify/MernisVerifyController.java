package com.crm.mernis.verify;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mernis")
@RequiredArgsConstructor
@EnableConfigurationProperties(MernisStubProperties.class)
public class MernisVerifyController {

    private static final String NATID_PATTERN = "^[0-9]{11}$";

    private final MernisStubProperties properties;

    @PostMapping("/verify")
    public MernisVerifyResponse verify(@Valid @RequestBody MernisVerifyRequest request) {
        boolean verified = request.nationalityId().matches(NATID_PATTERN)
                && !properties.deniedIds().contains(request.nationalityId());
        return new MernisVerifyResponse(verified);
    }
}
