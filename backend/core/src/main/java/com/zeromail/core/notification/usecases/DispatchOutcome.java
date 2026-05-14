package com.zeromail.core.notification.usecases;

public sealed interface DispatchOutcome
        permits DispatchOutcome.Success,
                DispatchOutcome.TransientFailure,
                DispatchOutcome.PermanentFailure {

    record Success(String externalId) implements DispatchOutcome {}

    record TransientFailure(String reason) implements DispatchOutcome {}

    record PermanentFailure(String reason) implements DispatchOutcome {}
}
