package com.escruta.core.services;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Splits inline {@code <think>...</think>} or {@code <thought>...</thought>} segments
 * out of a streaming LLM token sequence across arbitrary chunk boundaries.
 */
public class ThinkTagStreamParser {
    private static final List<String> OPEN_TAGS = List.of("<think>", "<thought>");
    private static final List<String> CLOSE_TAGS = List.of("</think>", "</thought>");
    private static final int MAX_TAG_LENGTH = 10; // "</thought>".length()

    private final Consumer<String> onThought;
    private final Consumer<String> onAnswer;
    private final StringBuilder buffer = new StringBuilder();
    private boolean inThink = false;
    private boolean trimNextAnswerLeadingWhitespace = false;

    public ThinkTagStreamParser(Consumer<String> onThought, Consumer<String> onAnswer) {
        this.onThought = onThought;
        this.onAnswer = onAnswer;
    }

    public void accept(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        buffer.append(chunk);
        drain(false);
    }

    public void flush() {
        drain(true);
    }

    private void drain(boolean eof) {
        while (!buffer.isEmpty()) {
            String lower = buffer.toString().toLowerCase(Locale.ROOT);
            List<String> targetTags = inThink ?
                    CLOSE_TAGS :
                    OPEN_TAGS;

            TagMatch match = findEarliestTag(lower, targetTags);
            if (match != null) {
                if (match.index() > 0) {
                    emitSegment(buffer.substring(0, match.index()));
                }
                buffer.delete(0, match.index() + match.tag().length());
                if (inThink) {
                    inThink = false;
                    trimNextAnswerLeadingWhitespace = true;
                } else {
                    inThink = true;
                }
                continue;
            }

            if (eof) {
                emitSegment(buffer.toString());
                buffer.setLength(0);
                break;
            }

            int holdback = computePartialTagHoldback(lower, targetTags);
            int safeLen = buffer.length() - holdback;
            if (safeLen > 0) {
                emitSegment(buffer.substring(0, safeLen));
                buffer.delete(0, safeLen);
            }
            break;
        }
    }

    private void emitSegment(String segment) {
        if (segment == null || segment.isEmpty()) {
            return;
        }
        if (inThink) {
            onThought.accept(segment);
            return;
        }
        if (trimNextAnswerLeadingWhitespace) {
            int i = 0;
            while (i < segment.length() && Character.isWhitespace(segment.charAt(i))) {
                i++;
            }
            if (i == segment.length()) {
                return;
            }
            trimNextAnswerLeadingWhitespace = false;
            onAnswer.accept(segment.substring(i));
            return;
        }
        onAnswer.accept(segment);
    }

    private static TagMatch findEarliestTag(String lower, List<String> tags) {
        TagMatch earliest = null;
        for (String tag : tags) {
            int idx = lower.indexOf(tag);
            if (idx >= 0 && (earliest == null || idx < earliest.index())) {
                earliest = new TagMatch(idx, tag);
            }
        }
        return earliest;
    }

    private static int computePartialTagHoldback(String lower, List<String> tags) {
        int maxSuffix = Math.min(lower.length(), MAX_TAG_LENGTH - 1);
        for (int len = maxSuffix; len >= 1; len--) {
            String suffix = lower.substring(lower.length() - len);
            for (String tag : tags) {
                if (tag.startsWith(suffix)) {
                    return len;
                }
            }
        }
        return 0;
    }

    private record TagMatch(
            int index,
            String tag
    ) {
    }
}
