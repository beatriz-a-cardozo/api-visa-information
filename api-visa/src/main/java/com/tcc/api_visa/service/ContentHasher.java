package com.tcc.api_visa.service;

import com.tcc.api_visa.domain.VisaRequirementContent;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** SHA-256 of the content's JSON form. Two collections with the same content produce the same hash. */
@Component
public class ContentHasher {

    // Own mapper, so changes to the application's JSON settings never change existing hashes.
    private final JsonMapper mapper = JsonMapper.builder().build();

    public String hash(VisaRequirementContent content) {
        byte[] json = mapper.writeValueAsBytes(content);
        return HexFormat.of().formatHex(sha256().digest(json));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }
}
