package com.tcc.api_visa.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AdminPropertiesTest {

    @Test
    void blankKeyMeansAdminIsClosed() {
        assertThat(new AdminProperties("  ").apiKey()).isNull();
    }

    @Test
    void rejectsShortKeys() {
        assertThatThrownBy(() -> new AdminProperties("too-short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    void acceptsLongKeys() {
        String key = "a".repeat(AdminProperties.MIN_KEY_LENGTH);

        assertThat(new AdminProperties(key).apiKey()).isEqualTo(key);
    }
}
