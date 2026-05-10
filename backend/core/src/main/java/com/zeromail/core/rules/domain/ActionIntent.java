package com.zeromail.core.rules.domain;

import com.zeromail.core.llm.domain.Action;

public sealed interface ActionIntent
    permits ActionIntent.Label, ActionIntent.Archive, ActionIntent.SaveDraft {

  RuleActionType type();

  record Label(String labelName) implements ActionIntent {
    public Label {
      if (labelName == null || labelName.isBlank()) {
        throw new IllegalArgumentException("labelName must not be blank");
      }
    }

    @Override
    public RuleActionType type() {
      return RuleActionType.LABEL;
    }
  }

  record Archive() implements ActionIntent {
    @Override
    public RuleActionType type() {
      return RuleActionType.ARCHIVE;
    }
  }

  record SaveDraft(String instruction) implements ActionIntent {
    public SaveDraft {
      if (instruction == null || instruction.isBlank()) {
        throw new IllegalArgumentException("instruction must not be blank");
      }
    }

    @Override
    public RuleActionType type() {
      return RuleActionType.SAVE_DRAFT;
    }
  }

  static ActionIntent fromAction(Action action) {
    return switch (RuleActionType.fromAction(action)) {
      case LABEL -> new Label("Unspecified");
      case ARCHIVE -> new Archive();
      case SAVE_DRAFT -> new SaveDraft("Draft a safe reply for user review");
    };
  }
}
