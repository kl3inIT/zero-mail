package com.zeromail.core.rules.usecases;

import com.zeromail.core.llm.usecases.RuleCompileGatewayResult;
import com.zeromail.core.rules.domain.ActionIntent;
import com.zeromail.core.rules.domain.MatcherNode;
import com.zeromail.core.rules.domain.MatcherType;
import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.rules.domain.RuleLanguage;
import com.zeromail.core.rules.domain.RuleSchemaVersion;
import com.zeromail.core.rules.domain.SemanticIntentMatcher;
import com.zeromail.core.shared.validation.EmailRecipientValidator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RuleCompileResultValidator {

    private static final Logger log = LoggerFactory.getLogger(RuleCompileResultValidator.class);
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final int MAX_DISPLAY_NAME_LENGTH = 160;
    private static final int MAX_MATCHER_TEXT_LENGTH = 512;
    private static final int MAX_REGEX_LENGTH = 256;
    private static final int MAX_ACTION_TEXT_LENGTH = 500;
    private static final int MAX_ACTION_BODY_LENGTH = 4000;
    private static final int MAX_RECIPIENTS = 10;
    private static final int MAX_CHILDREN = 24;
    private static final Set<String> TOP_LEVEL_FIELDS =
            Set.of(
                    "schemaVersion",
                    "sourceLanguage",
                    "displayName",
                    "matcher",
                    "actionIntents",
                    "clarificationRequired",
                    "clarificationQuestion");

    private static final Set<String> COMMON_MATCHER_FIELDS =
            Set.of("schemaVersion", "type", "matcherType", "nodeId");
    private static final Set<String> ACTION_FIELDS =
            Set.of(
                    "type",
                    "value",
                    "labelName",
                    "body",
                    "instruction",
                    "recipients",
                    "to",
                    "cc",
                    "bcc",
                    "subject",
                    "note");
    private static final Set<String> QUESTION_LEAK_MARKERS =
            Set.of("prompt", "system", "tool", "argument", "completion", "{", "}", "<script");

    public RuleCompileResult validate(
            String sourceText, String toolName, Map<String, Object> toolArguments) {
        RuleLanguage detectedLanguage = detectSourceLanguage(sourceText);
        try {
            if (!RuleCompileGatewayResult.TOOL_NAME.equals(toolName)) {
                return RuleCompileResult.invalid(detectedLanguage, "unknown_tool");
            }
            Map<String, Object> arguments = copyStringKeyedMap(toolArguments, "toolArguments");
            rejectUnknownFields(arguments, TOP_LEVEL_FIELDS, "toolArguments");

            RuleLanguage sourceLanguage =
                    resolveSourceLanguage(
                            detectedLanguage, stringField(arguments, "sourceLanguage", true));
            boolean clarificationRequired = booleanField(arguments, "clarificationRequired", false);
            boolean hasRequiredCompileSlots =
                    arguments.containsKey("schemaVersion")
                            && arguments.containsKey("displayName")
                            && arguments.containsKey("matcher")
                            && arguments.containsKey("actionIntents");

            if (clarificationRequired || !hasRequiredCompileSlots) {
                return clarificationResult(
                        sourceLanguage, optionalString(arguments, "clarificationQuestion"));
            }

            RuleSchemaVersion schemaVersion =
                    RuleSchemaVersion.fromId(stringField(arguments, "schemaVersion", false));
            String displayName =
                    boundedStringField(arguments, "displayName", MAX_DISPLAY_NAME_LENGTH, false);
            ParsedMatcher parsedMatcher =
                    parseMatcher(mapField(arguments, "matcher"), "$.matcher", schemaVersion, true);
            List<Map<String, Object>> normalizedActionIntents =
                    normalizeActionIntents(listField(arguments, "actionIntents"));
            if (normalizedActionIntents.isEmpty()) {
                return RuleCompileResult.invalid(sourceLanguage, "empty_action_list");
            }

            return RuleCompileResult.compiled(
                    sourceLanguage,
                    displayName,
                    schemaVersion,
                    writeJson(parsedMatcher.normalizedMatcher()),
                    writeJson(normalizedActionIntents));
        } catch (IllegalArgumentException | NoSuchElementException validationFailure) {
            // Exception messages here describe *schema-level* failures (unknown
            // matcher id, displayName too long, missing required field) — not
            // user content. Logging the class + message keeps the privacy rule
            // intact while making invalid-output bugs diagnosable.
            log.info(
                    "event=rule_compile_validation_failed errorClass={} reason={}",
                    validationFailure.getClass().getSimpleName(),
                    validationFailure.getMessage());
            return RuleCompileResult.invalid(detectedLanguage, "invalid_compile_output");
        }
    }

    public RuleLanguage detectSourceLanguage(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return RuleLanguage.UNKNOWN;
        }
        String normalizedSource = sourceText.toLowerCase(Locale.ROOT);
        if (containsVietnameseSignal(normalizedSource)) {
            return RuleLanguage.VI;
        }
        int englishScore = 0;
        for (String englishToken :
                List.of(
                        "archive",
                        "label",
                        "receipt",
                        "receipts",
                        "newsletter",
                        "newsletters",
                        "from",
                        "subject",
                        "email",
                        "draft",
                        "reply",
                        "sender")) {
            if (containsToken(normalizedSource, englishToken)) {
                englishScore++;
            }
        }
        return englishScore >= 2 ? RuleLanguage.EN : RuleLanguage.UNKNOWN;
    }

    private RuleCompileResult clarificationResult(RuleLanguage sourceLanguage, String rawQuestion) {
        String sanitizedQuestion = sanitizeClarificationQuestion(sourceLanguage, rawQuestion);
        if (sanitizedQuestion == null) {
            return RuleCompileResult.invalid(sourceLanguage, "invalid_clarification");
        }
        return RuleCompileResult.requiresClarification(
                new RuleClarificationQuestion(sourceLanguage, sanitizedQuestion));
    }

    private String sanitizeClarificationQuestion(RuleLanguage sourceLanguage, String rawQuestion) {
        String question =
                rawQuestion == null || rawQuestion.isBlank()
                        ? fallbackQuestion(sourceLanguage)
                        : rawQuestion;
        question =
                question.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
        if (question.length() > RuleClarificationQuestion.MAX_QUESTION_LENGTH) {
            return null;
        }
        String lowerQuestion = question.toLowerCase(Locale.ROOT);
        for (String leakMarker : QUESTION_LEAK_MARKERS) {
            if (lowerQuestion.contains(leakMarker)) {
                return null;
            }
        }
        long questionMarkCount =
                question.chars().filter(character -> character == '?' || character == '？').count();
        if (questionMarkCount > 1) {
            return null;
        }
        if (questionMarkCount == 0) {
            question = question + "?";
        }
        if (sourceLanguage == RuleLanguage.VI
                && !containsVietnameseSignal(question.toLowerCase(Locale.ROOT))) {
            return fallbackQuestion(RuleLanguage.VI);
        }
        if (sourceLanguage == RuleLanguage.EN
                && containsVietnameseSignal(question.toLowerCase(Locale.ROOT))) {
            return fallbackQuestion(RuleLanguage.EN);
        }
        return question;
    }

    private static String fallbackQuestion(RuleLanguage sourceLanguage) {
        if (sourceLanguage == RuleLanguage.VI) {
            return "Bạn muốn quy tắc này áp dụng cho người gửi, chủ đề hay nhãn nào?";
        }
        return "Which sender, subject, or label should this rule match?";
    }

    private static RuleLanguage resolveSourceLanguage(
            RuleLanguage detectedLanguage, String modelProvidedLanguage) {
        RuleLanguage safeModelLanguage = RuleLanguage.fromId(modelProvidedLanguage);
        return detectedLanguage == RuleLanguage.UNKNOWN ? safeModelLanguage : detectedLanguage;
    }

    private ParsedMatcher parseMatcher(
            Map<String, Object> matcherArguments,
            String matcherPath,
            RuleSchemaVersion schemaVersion,
            boolean rootMatcher) {
        MatcherType matcherType =
                MatcherType.fromId(stringField(matcherArguments, "type", "matcherType"));
        String nodeId = optionalString(matcherArguments, "nodeId");
        if (nodeId == null) {
            nodeId = matcherPath.replace("$.", "").replaceAll("[^A-Za-z0-9]+", "-");
        }

        Map<String, Object> normalizedMatcher = new LinkedHashMap<>();
        if (rootMatcher) {
            normalizedMatcher.put("schemaVersion", schemaVersion.id());
        }
        normalizedMatcher.put("nodeId", nodeId);
        normalizedMatcher.put("type", matcherType.id());

        MatcherNode typedMatcher =
                switch (matcherType) {
                    case SENDER_EMAIL -> {
                        rejectUnknownFields(
                                matcherArguments, fields("email", "value"), matcherPath);
                        String email =
                                firstBoundedString(
                                        matcherArguments,
                                        MAX_MATCHER_TEXT_LENGTH,
                                        "email",
                                        "value");
                        normalizedMatcher.put("email", email);
                        yield new MatcherNode.SenderEmailMatcher(nodeId, email);
                    }
                    case SENDER_DOMAIN -> {
                        rejectUnknownFields(
                                matcherArguments, fields("domain", "value"), matcherPath);
                        String domain =
                                firstBoundedString(
                                        matcherArguments,
                                        MAX_MATCHER_TEXT_LENGTH,
                                        "domain",
                                        "value");
                        normalizedMatcher.put("domain", domain);
                        yield new MatcherNode.SenderDomainMatcher(nodeId, domain);
                    }
                    case RECIPIENT_TO -> {
                        rejectUnknownFields(
                                matcherArguments, fields("email", "value"), matcherPath);
                        String email =
                                firstBoundedString(
                                        matcherArguments,
                                        MAX_MATCHER_TEXT_LENGTH,
                                        "email",
                                        "value");
                        normalizedMatcher.put("email", email);
                        yield new MatcherNode.RecipientToMatcher(nodeId, email);
                    }
                    case RECIPIENT_CC -> {
                        rejectUnknownFields(
                                matcherArguments, fields("email", "value"), matcherPath);
                        String email =
                                firstBoundedString(
                                        matcherArguments,
                                        MAX_MATCHER_TEXT_LENGTH,
                                        "email",
                                        "value");
                        normalizedMatcher.put("email", email);
                        yield new MatcherNode.RecipientCcMatcher(nodeId, email);
                    }
                    case SUBJECT_CONTAINS -> {
                        rejectUnknownFields(matcherArguments, fields("text", "value"), matcherPath);
                        String text =
                                firstBoundedString(
                                        matcherArguments, MAX_MATCHER_TEXT_LENGTH, "text", "value");
                        normalizedMatcher.put("text", text);
                        yield new MatcherNode.SubjectContainsMatcher(nodeId, text);
                    }
                    case SUBJECT_EQUALS -> {
                        rejectUnknownFields(matcherArguments, fields("text", "value"), matcherPath);
                        String text =
                                firstBoundedString(
                                        matcherArguments, MAX_MATCHER_TEXT_LENGTH, "text", "value");
                        normalizedMatcher.put("text", text);
                        yield new MatcherNode.SubjectEqualsMatcher(nodeId, text);
                    }
                    case SUBJECT_REGEX -> {
                        rejectUnknownFields(
                                matcherArguments,
                                fields("regexPattern", "regex", "text"),
                                matcherPath);
                        String regexPattern =
                                firstBoundedString(
                                        matcherArguments,
                                        MAX_REGEX_LENGTH,
                                        "regexPattern",
                                        "regex",
                                        "text");
                        normalizedMatcher.put("regexPattern", regexPattern);
                        yield new MatcherNode.SubjectRegexMatcher(nodeId, regexPattern);
                    }
                    case GMAIL_LABEL_PRESENT -> {
                        rejectUnknownFields(
                                matcherArguments, fields("labelId", "label"), matcherPath);
                        String labelId =
                                firstBoundedString(
                                        matcherArguments,
                                        MAX_MATCHER_TEXT_LENGTH,
                                        "labelId",
                                        "label");
                        normalizedMatcher.put("labelId", labelId);
                        yield new MatcherNode.GmailLabelPresentMatcher(nodeId, labelId);
                    }
                    case GMAIL_LABEL_ABSENT -> {
                        rejectUnknownFields(
                                matcherArguments, fields("labelId", "label"), matcherPath);
                        String labelId =
                                firstBoundedString(
                                        matcherArguments,
                                        MAX_MATCHER_TEXT_LENGTH,
                                        "labelId",
                                        "label");
                        normalizedMatcher.put("labelId", labelId);
                        yield new MatcherNode.GmailLabelAbsentMatcher(nodeId, labelId);
                    }
                    case GMAIL_CATEGORY_PRESENT -> {
                        rejectUnknownFields(matcherArguments, fields("category"), matcherPath);
                        String category =
                                boundedStringField(
                                        matcherArguments,
                                        "category",
                                        MAX_MATCHER_TEXT_LENGTH,
                                        false);
                        normalizedMatcher.put("category", category);
                        yield new MatcherNode.GmailCategoryPresentMatcher(nodeId, category);
                    }
                    case GMAIL_CATEGORY_ABSENT -> {
                        rejectUnknownFields(matcherArguments, fields("category"), matcherPath);
                        String category =
                                boundedStringField(
                                        matcherArguments,
                                        "category",
                                        MAX_MATCHER_TEXT_LENGTH,
                                        false);
                        normalizedMatcher.put("category", category);
                        yield new MatcherNode.GmailCategoryAbsentMatcher(nodeId, category);
                    }
                    case HAS_ATTACHMENT -> {
                        rejectUnknownFields(matcherArguments, fields(), matcherPath);
                        yield new MatcherNode.HasAttachmentMatcher(nodeId);
                    }
                    case LIST_UNSUBSCRIBE_PRESENT -> {
                        rejectUnknownFields(matcherArguments, fields(), matcherPath);
                        yield new MatcherNode.ListUnsubscribePresentMatcher(nodeId);
                    }
                    case NEWSLETTER_INDICATOR -> {
                        rejectUnknownFields(matcherArguments, fields(), matcherPath);
                        yield new MatcherNode.NewsletterIndicatorMatcher(nodeId);
                    }
                    case MESSAGE_AGE -> {
                        rejectUnknownFields(
                                matcherArguments, fields("operator", "days"), matcherPath);
                        MatcherNode.MessageAgeOperator operator =
                                MatcherNode.MessageAgeOperator.valueOf(
                                        stringField(matcherArguments, "operator", false));
                        int days = integerField(matcherArguments, "days");
                        normalizedMatcher.put("operator", operator.name());
                        normalizedMatcher.put("days", days);
                        yield new MatcherNode.MessageAgeMatcher(nodeId, operator, days);
                    }
                    case MESSAGE_DATE -> {
                        rejectUnknownFields(
                                matcherArguments, fields("operator", "date"), matcherPath);
                        MatcherNode.MessageDateOperator operator =
                                MatcherNode.MessageDateOperator.valueOf(
                                        stringField(matcherArguments, "operator", false));
                        LocalDate date =
                                LocalDate.parse(stringField(matcherArguments, "date", false));
                        normalizedMatcher.put("operator", operator.name());
                        normalizedMatcher.put("date", date.toString());
                        yield new MatcherNode.MessageDateMatcher(nodeId, operator, date);
                    }
                    case ALL -> {
                        rejectUnknownFields(matcherArguments, fields("children"), matcherPath);
                        List<ParsedMatcher> childMatchers =
                                parseChildren(
                                        listField(matcherArguments, "children"),
                                        matcherPath,
                                        schemaVersion);
                        List<MatcherNode> children =
                                childMatchers.stream().map(ParsedMatcher::typedMatcher).toList();
                        normalizedMatcher.put(
                                "children",
                                childMatchers.stream()
                                        .map(ParsedMatcher::normalizedMatcher)
                                        .toList());
                        yield new MatcherNode.AllMatcher(nodeId, children);
                    }
                    case ANY -> {
                        rejectUnknownFields(matcherArguments, fields("children"), matcherPath);
                        List<ParsedMatcher> childMatchers =
                                parseChildren(
                                        listField(matcherArguments, "children"),
                                        matcherPath,
                                        schemaVersion);
                        List<MatcherNode> children =
                                childMatchers.stream().map(ParsedMatcher::typedMatcher).toList();
                        normalizedMatcher.put(
                                "children",
                                childMatchers.stream()
                                        .map(ParsedMatcher::normalizedMatcher)
                                        .toList());
                        yield new MatcherNode.AnyMatcher(nodeId, children);
                    }
                    case NOT -> {
                        rejectUnknownFields(matcherArguments, fields("child"), matcherPath);
                        ParsedMatcher childMatcher =
                                parseMatcher(
                                        mapField(matcherArguments, "child"),
                                        matcherPath + ".child",
                                        schemaVersion,
                                        false);
                        normalizedMatcher.put("child", childMatcher.normalizedMatcher());
                        yield new MatcherNode.NotMatcher(nodeId, childMatcher.typedMatcher());
                    }
                    case SEMANTIC_INTENT -> {
                        rejectUnknownFields(
                                matcherArguments,
                                fields("intent", "description", "deferred"),
                                matcherPath);
                        boolean deferred = booleanField(matcherArguments, "deferred", false);
                        String intent =
                                firstBoundedString(
                                        matcherArguments,
                                        MAX_MATCHER_TEXT_LENGTH,
                                        "intent",
                                        "description");
                        normalizedMatcher.put("intent", intent);
                        normalizedMatcher.put("deferred", deferred);
                        yield new SemanticIntentMatcher(nodeId, intent, deferred);
                    }
                };
        return new ParsedMatcher(typedMatcher, normalizedMatcher);
    }

    private List<ParsedMatcher> parseChildren(
            List<Object> childArguments, String matcherPath, RuleSchemaVersion schemaVersion) {
        if (childArguments.isEmpty() || childArguments.size() > MAX_CHILDREN) {
            throw new IllegalArgumentException("invalid child matcher count");
        }
        List<ParsedMatcher> parsedChildren = new ArrayList<>();
        for (int childIndex = 0; childIndex < childArguments.size(); childIndex++) {
            parsedChildren.add(
                    parseMatcher(
                            copyStringKeyedMap(
                                    childArguments.get(childIndex), matcherPath + ".children"),
                            matcherPath + ".children[" + childIndex + "]",
                            schemaVersion,
                            false));
        }
        return parsedChildren;
    }

    private List<Map<String, Object>> normalizeActionIntents(List<Object> actionArguments) {
        List<Map<String, Object>> normalizedActions = new ArrayList<>();
        for (Object actionArgument : actionArguments) {
            Map<String, Object> actionMap = copyStringKeyedMap(actionArgument, "actionIntent");
            rejectUnknownFields(actionMap, ACTION_FIELDS, "actionIntent");
            RuleActionType actionType =
                    RuleActionType.fromId(stringField(actionMap, "type", false));
            Map<String, Object> normalizedAction = new LinkedHashMap<>();
            normalizedAction.put("type", actionType.id());
            ActionIntent typedAction =
                    switch (actionType) {
                        case LABEL -> {
                            rejectUnknownFields(
                                    actionMap, actionFields("labelName", "value"), "actionIntent");
                            String labelName =
                                    firstBoundedString(
                                            actionMap,
                                            MAX_ACTION_TEXT_LENGTH,
                                            "labelName",
                                            "value");
                            normalizedAction.put("labelName", labelName);
                            yield new ActionIntent.Label(labelName);
                        }
                        case ARCHIVE -> {
                            if (actionMap.size() > 1) {
                                throw new IllegalArgumentException(
                                        "archive action cannot carry content");
                            }
                            yield new ActionIntent.Archive();
                        }
                        case MARK_READ -> {
                            rejectNoValueAction(actionMap, "mark_read");
                            yield new ActionIntent.MarkRead();
                        }
                        case STAR -> {
                            rejectNoValueAction(actionMap, "star");
                            yield new ActionIntent.Star();
                        }
                        case ADD_TO_DIGEST -> {
                            rejectNoValueAction(actionMap, "add_to_digest");
                            yield new ActionIntent.AddToDigest();
                        }
                        case MARK_SPAM -> {
                            rejectNoValueAction(actionMap, "mark_spam");
                            yield new ActionIntent.MarkSpam();
                        }
                        case SAVE_DRAFT -> {
                            rejectUnknownFields(
                                    actionMap,
                                    actionFields("instruction", "body", "value"),
                                    "actionIntent");
                            String instruction =
                                    firstBoundedString(
                                            actionMap,
                                            MAX_ACTION_TEXT_LENGTH,
                                            "instruction",
                                            "body",
                                            "value");
                            normalizedAction.put("instruction", instruction);
                            yield new ActionIntent.SaveDraft(instruction);
                        }
                        case SEND_REPLY -> {
                            rejectUnknownFields(
                                    actionMap,
                                    actionFields("instruction", "body", "value"),
                                    "actionIntent");
                            String instruction =
                                    firstBoundedString(
                                            actionMap,
                                            MAX_ACTION_TEXT_LENGTH,
                                            "instruction",
                                            "body",
                                            "value");
                            normalizedAction.put("instruction", instruction);
                            yield new ActionIntent.SendReply(instruction);
                        }
                        case FORWARD_EMAIL -> {
                            rejectUnknownFields(
                                    actionMap,
                                    actionFields("recipients", "to", "instruction", "note"),
                                    "actionIntent");
                            List<String> recipients =
                                    requiredRecipientList(actionMap, "recipients", "to");
                            normalizedAction.put("recipients", recipients);
                            String instruction = optionalString(actionMap, "instruction");
                            if (instruction == null) {
                                instruction = optionalString(actionMap, "note");
                            }
                            if (instruction != null) {
                                if (instruction.length() > MAX_ACTION_TEXT_LENGTH) {
                                    throw new IllegalArgumentException("instruction is too long");
                                }
                                normalizedAction.put("instruction", instruction);
                            }
                            yield new ActionIntent.ForwardEmail(recipients, instruction);
                        }
                        case SEND_EMAIL -> {
                            rejectUnknownFields(
                                    actionMap,
                                    actionFields(
                                            "to", "recipients", "cc", "bcc", "subject", "body"),
                                    "actionIntent");
                            List<String> to = requiredRecipientList(actionMap, "to", "recipients");
                            List<String> cc = optionalRecipientList(actionMap, "cc");
                            List<String> bcc = optionalRecipientList(actionMap, "bcc");
                            String subject =
                                    boundedStringField(
                                            actionMap, "subject", MAX_ACTION_TEXT_LENGTH, false);
                            String body =
                                    boundedStringField(
                                            actionMap, "body", MAX_ACTION_BODY_LENGTH, false);
                            normalizedAction.put("to", to);
                            if (!cc.isEmpty()) {
                                normalizedAction.put("cc", cc);
                            }
                            if (!bcc.isEmpty()) {
                                normalizedAction.put("bcc", bcc);
                            }
                            normalizedAction.put("subject", subject);
                            normalizedAction.put("body", body);
                            yield new ActionIntent.SendEmail(to, cc, bcc, subject, body);
                        }
                    };
            Objects.requireNonNull(typedAction, "typedAction");
            normalizedActions.add(normalizedAction);
        }
        return List.copyOf(normalizedActions);
    }

    private static void rejectNoValueAction(Map<String, Object> actionMap, String actionType) {
        if (actionMap.size() > 1) {
            throw new IllegalArgumentException(actionType + " action cannot carry content");
        }
    }

    private static List<String> requiredRecipientList(
            Map<String, Object> values, String primaryFieldName, String fallbackFieldName) {
        List<String> recipients = recipientList(values, primaryFieldName, fallbackFieldName, true);
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException(primaryFieldName + " is required");
        }
        return recipients;
    }

    private static List<String> optionalRecipientList(
            Map<String, Object> values, String fieldName) {
        return recipientList(values, fieldName, fieldName, false);
    }

    private static List<String> recipientList(
            Map<String, Object> values,
            String primaryFieldName,
            String fallbackFieldName,
            boolean required) {
        Object rawValue = values.get(primaryFieldName);
        if (rawValue == null && !primaryFieldName.equals(fallbackFieldName)) {
            rawValue = values.get(fallbackFieldName);
        }
        if (rawValue == null) {
            if (required) {
                throw new IllegalArgumentException(primaryFieldName + " is required");
            }
            return List.of();
        }
        List<String> recipientValues;
        if (rawValue instanceof String stringValue) {
            recipientValues = List.of(stringValue);
        } else if (rawValue instanceof List<?> rawList) {
            if (rawList.size() > MAX_RECIPIENTS) {
                throw new IllegalArgumentException(primaryFieldName + " has too many recipients");
            }
            recipientValues = new ArrayList<>();
            for (Object rawRecipient : rawList) {
                if (!(rawRecipient instanceof String recipient)) {
                    throw new IllegalArgumentException(primaryFieldName + " must contain strings");
                }
                recipientValues.add(recipient);
            }
        } else {
            throw new IllegalArgumentException(primaryFieldName + " must be a list");
        }
        // Delegate the final syntax check to the shared validator, which normalizes via RFC 822
        // parsing (accepts "Name <addr>" and comma-joined strings the LLM legitimately emits for a
        // valid recipient) instead of a brittle no-whitespace regex. Keeping this in one place also
        // means the rule-compile path and the outbound send path agree on what a valid recipient
        // is.
        return EmailRecipientValidator.optional(recipientValues, primaryFieldName);
    }

    private static boolean containsVietnameseSignal(String normalizedText) {
        if (normalizedText.matches(
                ".*[ăâđêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ].*")) {
            return true;
        }
        for (String vietnameseToken :
                List.of(
                        " hoa don ",
                        " hóa đơn ",
                        " bien lai ",
                        " biên lai ",
                        " tu ",
                        " từ ",
                        " nhan ",
                        " nhãn ",
                        " thu ",
                        " thư ",
                        " cua ",
                        " của ",
                        " khong ",
                        " không ")) {
            if ((" " + normalizedText + " ").contains(vietnameseToken)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsToken(String normalizedText, String token) {
        return (" " + normalizedText + " ").contains(" " + token + " ");
    }

    private static Set<String> fields(String... fieldNames) {
        Set<String> allowedFields = new java.util.LinkedHashSet<>(COMMON_MATCHER_FIELDS);
        allowedFields.addAll(Arrays.asList(fieldNames));
        return allowedFields;
    }

    private static Set<String> actionFields(String... fieldNames) {
        Set<String> allowedFields = new java.util.LinkedHashSet<>(Set.of("type"));
        allowedFields.addAll(Arrays.asList(fieldNames));
        return allowedFields;
    }

    private static void rejectUnknownFields(
            Map<String, Object> values, Set<String> allowedFields, String path) {
        for (String fieldName : values.keySet()) {
            if (!allowedFields.contains(fieldName)) {
                throw new IllegalArgumentException("Unknown field at " + path + ": " + fieldName);
            }
        }
    }

    private static Map<String, Object> copyStringKeyedMap(Object value, String fieldName) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException(fieldName + " must be an object");
        }
        Map<String, Object> copiedMap = new LinkedHashMap<>();
        for (Map.Entry<?, ?> rawEntry : rawMap.entrySet()) {
            if (!(rawEntry.getKey() instanceof String key)) {
                throw new IllegalArgumentException(fieldName + " contains non-string key");
            }
            copiedMap.put(key, rawEntry.getValue());
        }
        return copiedMap;
    }

    private static Map<String, Object> mapField(Map<String, Object> values, String fieldName) {
        return copyStringKeyedMap(values.get(fieldName), fieldName);
    }

    private static List<Object> listField(Map<String, Object> values, String fieldName) {
        Object value = values.get(fieldName);
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalArgumentException(fieldName + " must be a list");
        }
        return List.copyOf(rawList);
    }

    private static boolean booleanField(
            Map<String, Object> values, String fieldName, boolean required) {
        Object value = values.get(fieldName);
        if (value == null && !required) {
            return false;
        }
        if (!(value instanceof Boolean booleanValue)) {
            throw new IllegalArgumentException(fieldName + " must be boolean");
        }
        return booleanValue;
    }

    private static int integerField(Map<String, Object> values, String fieldName) {
        Object value = values.get(fieldName);
        if (!(value instanceof Number numberValue)) {
            throw new IllegalArgumentException(fieldName + " must be a number");
        }
        return numberValue.intValue();
    }

    private static String optionalString(Map<String, Object> values, String fieldName) {
        Object value = values.get(fieldName);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String stringValue)) {
            throw new IllegalArgumentException(fieldName + " must be string");
        }
        String normalizedValue = stringValue.trim();
        return normalizedValue.isBlank() ? null : normalizedValue;
    }

    private static String stringField(
            Map<String, Object> values, String fieldName, boolean allowMissing) {
        String value = optionalString(values, fieldName);
        if (value == null && allowMissing) {
            return RuleLanguage.UNKNOWN.id();
        }
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static String stringField(
            Map<String, Object> values, String primaryFieldName, String fallbackFieldName) {
        String value = optionalString(values, primaryFieldName);
        if (value == null) {
            value = optionalString(values, fallbackFieldName);
        }
        if (value == null) {
            throw new IllegalArgumentException(primaryFieldName + " is required");
        }
        return value;
    }

    private static String boundedStringField(
            Map<String, Object> values, String fieldName, int maxLength, boolean allowMissing) {
        String value = stringField(values, fieldName, allowMissing);
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return value;
    }

    private static String firstBoundedString(
            Map<String, Object> values,
            int maxLength,
            String firstFieldName,
            String... otherFieldNames) {
        String value = optionalString(values, firstFieldName);
        for (String fieldName : otherFieldNames) {
            if (value == null) {
                value = optionalString(values, fieldName);
            }
        }
        if (value == null) {
            throw new IllegalArgumentException(firstFieldName + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(firstFieldName + " is too long");
        }
        return value;
    }

    private static String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JacksonException serializationFailure) {
            throw new IllegalArgumentException(
                    "Unable to serialize compile result", serializationFailure);
        }
    }

    private record ParsedMatcher(MatcherNode typedMatcher, Map<String, Object> normalizedMatcher) {}
}
