package com.tcc.api_visa.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tcc.api_visa.support.TestData;
import org.junit.jupiter.api.Test;

class ContentHasherTest {

    private final ContentHasher hasher = new ContentHasher();

    @Test
    void sameContentGivesTheSameHash() {
        assertThat(hasher.hash(TestData.content("135"))).isEqualTo(hasher.hash(TestData.content("135")));
    }

    @Test
    void anyChangeGivesADifferentHash() {
        assertThat(hasher.hash(TestData.content("135"))).isNotEqualTo(hasher.hash(TestData.content("140")));
    }

    @Test
    void isAHexSha256() {
        assertThat(hasher.hash(TestData.content("135"))).matches("[0-9a-f]{64}");
    }
}
