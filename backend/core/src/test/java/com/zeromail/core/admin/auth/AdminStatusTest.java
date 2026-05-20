package com.zeromail.core.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.admin.auth.domain.AdminStatus;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class AdminStatusTest {

    @Test
    void status_ids_are_stable_for_database_storage() {
        assertThat(AdminStatus.PENDING_ENROLLMENT.id()).isEqualTo("PENDING_ENROLLMENT");
        assertThat(AdminStatus.ACTIVE.id()).isEqualTo("ACTIVE");
        assertThat(AdminStatus.REVOKED.id()).isEqualTo("REVOKED");
    }

    @Test
    void from_id_is_fail_loud() {
        assertThat(AdminStatus.fromId("ACTIVE")).isEqualTo(AdminStatus.ACTIVE);
        assertThatThrownBy(() -> AdminStatus.fromId("BROKEN"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Unknown AdminStatus id");
    }
}
