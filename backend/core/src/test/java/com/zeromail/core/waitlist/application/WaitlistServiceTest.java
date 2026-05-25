package com.zeromail.core.waitlist.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.account.usecases.AccountService;
import com.zeromail.core.waitlist.domain.WaitlistSubscribeResult;
import com.zeromail.core.waitlist.persistence.WaitlistEmailEntity;
import com.zeromail.core.waitlist.persistence.WaitlistEmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class WaitlistServiceTest {

    private WaitlistEmailRepository waitlistRepository;
    private AccountService accountService;
    private WaitlistService service;

    @BeforeEach
    void setUp() {
        waitlistRepository = mock(WaitlistEmailRepository.class);
        accountService = mock(AccountService.class);
        service = new WaitlistService(waitlistRepository, accountService, "pepper");
    }

    @Test
    void new_email_is_saved_lowercased_with_hashed_ip() {
        when(accountService.isEmailRegistered(eq("alice@example.com"))).thenReturn(false);
        when(waitlistRepository.existsByEmailIgnoreCase(eq("alice@example.com"))).thenReturn(false);

        WaitlistSubscribeResult result =
                service.subscribe(
                        "  Alice@Example.COM  ", "landing_page", "203.0.113.7", "Mozilla/5.0");

        assertThat(result).isEqualTo(WaitlistSubscribeResult.ADDED);

        ArgumentCaptor<WaitlistEmailEntity> entityCaptor =
                ArgumentCaptor.forClass(WaitlistEmailEntity.class);
        verify(waitlistRepository).saveAndFlush(entityCaptor.capture());
        WaitlistEmailEntity savedEntity = entityCaptor.getValue();
        assertThat(savedEntity.getEmail()).isEqualTo("alice@example.com");
        assertThat(savedEntity.getSource()).isEqualTo("landing_page");
        assertThat(savedEntity.getIpHash()).isNotNull().doesNotContain("203.0.113.7").hasSize(64);
        assertThat(savedEntity.getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    void existing_user_email_returns_already_user_and_does_not_persist() {
        when(accountService.isEmailRegistered(eq("bob@example.com"))).thenReturn(true);

        WaitlistSubscribeResult result = service.subscribe("Bob@Example.com", null, null, null);

        assertThat(result).isEqualTo(WaitlistSubscribeResult.ALREADY_USER);
        verify(waitlistRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicate_waitlist_email_returns_already_registered_without_save() {
        when(accountService.isEmailRegistered(any())).thenReturn(false);
        when(waitlistRepository.existsByEmailIgnoreCase(eq("carol@example.com"))).thenReturn(true);

        WaitlistSubscribeResult result =
                service.subscribe("carol@example.com", "referral", "10.0.0.1", "agent");

        assertThat(result).isEqualTo(WaitlistSubscribeResult.ALREADY_REGISTERED);
        verify(waitlistRepository, never()).saveAndFlush(any());
    }

    @Test
    void race_condition_on_insert_is_downgraded_to_already_registered() {
        when(accountService.isEmailRegistered(any())).thenReturn(false);
        when(waitlistRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(waitlistRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_waitlist_email_email"));

        WaitlistSubscribeResult result = service.subscribe("dave@example.com", null, null, null);

        assertThat(result).isEqualTo(WaitlistSubscribeResult.ALREADY_REGISTERED);
        verify(waitlistRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void blank_source_and_user_agent_are_normalized_to_null() {
        when(accountService.isEmailRegistered(any())).thenReturn(false);
        when(waitlistRepository.existsByEmailIgnoreCase(any())).thenReturn(false);

        service.subscribe("erin@example.com", "   ", "10.0.0.2", "   ");

        ArgumentCaptor<WaitlistEmailEntity> entityCaptor =
                ArgumentCaptor.forClass(WaitlistEmailEntity.class);
        verify(waitlistRepository).saveAndFlush(entityCaptor.capture());
        WaitlistEmailEntity savedEntity = entityCaptor.getValue();
        assertThat(savedEntity.getSource()).isNull();
        assertThat(savedEntity.getUserAgent()).isNull();
    }
}
