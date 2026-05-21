package com.zeromail.core.chat.usecases;

import com.zeromail.core.chat.persistence.lowlevel.AssistantPersonalInstructionsRepository;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantPersonalInstructionsService {

    private static final int MAX_PERSONAL_INSTRUCTIONS_LENGTH = 2_000;

    private final AssistantPersonalInstructionsRepository personalInstructionsRepository;

    public AssistantPersonalInstructionsService(
            AssistantPersonalInstructionsRepository personalInstructionsRepository) {
        this.personalInstructionsRepository =
                Objects.requireNonNull(
                        personalInstructionsRepository,
                        "personalInstructionsRepository must not be null");
    }

    @Transactional
    public InstructionUpdateResult overwrite(UUID tenantId, String personalInstructions) {
        String normalizedInstructions =
                AssistantMemoryService.requireBoundedText(
                        personalInstructions,
                        "personalInstructions",
                        MAX_PERSONAL_INSTRUCTIONS_LENGTH);
        String previousInstructions =
                personalInstructionsRepository.findPersonalInstructions(tenantId).orElse(null);
        personalInstructionsRepository.upsertPersonalInstructions(tenantId, normalizedInstructions);
        return new InstructionUpdateResult(
                previousInstructions == null ? 0 : previousInstructions.length(),
                normalizedInstructions.length());
    }

    public record InstructionUpdateResult(int beforeLength, int afterLength) {

        public Map<String, Object> toSummary() {
            return Map.of("before_length", beforeLength, "after_length", afterLength);
        }
    }
}
