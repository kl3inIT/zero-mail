package com.zeromail.core.llm.service;

import com.zeromail.core.llm.domain.Action;
import com.zeromail.core.llm.exception.SafetyViolationException;
import java.util.EnumSet;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Component;

@Component
public class ActionValidator {

    // D-C2: independent EnumSet check on top of Action.fromFunctionName.
    // Both checks must fail open for an unsafe action to leak.
    private static final EnumSet<Action> ALLOW_LIST =
            EnumSet.of(Action.LABEL, Action.ARCHIVE, Action.SAVE_DRAFT);

    public Action validate(String functionName) {
        if (functionName == null || functionName.isBlank()) {
            throw new SafetyViolationException();
        }

        Action resolvedAction;
        try {
            resolvedAction = Action.fromFunctionName(functionName);
        } catch (NoSuchElementException unknownAction) {
            throw new SafetyViolationException();
        }

        if (!ALLOW_LIST.contains(resolvedAction)) {
            throw new SafetyViolationException();
        }

        return resolvedAction;
    }
}
