package com.escruta.core.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ThinkTagStreamParser Tests")
class ThinkTagStreamParserTest {

    @Test
    @DisplayName("Should separate <think> blocks from answer text across chunk boundaries")
    void separatesThinkTagsAcrossChunks() {
        StringBuilder thoughts = new StringBuilder();
        StringBuilder answer = new StringBuilder();
        ThinkTagStreamParser parser = new ThinkTagStreamParser(thoughts::append, answer::append);

        String full = "<think>First, search for A.\nThen compare with B.</think>\n\nHere is the final answer.";
        // Feed 2 characters at a time to exercise partial tag boundaries
        for (int i = 0; i < full.length(); i += 2) {
            parser.accept(full.substring(i, Math.min(full.length(), i + 2)));
        }
        parser.flush();

        assertThat(thoughts.toString()).isEqualTo("First, search for A.\nThen compare with B.");
        assertThat(answer.toString()).isEqualTo("Here is the final answer.");
    }

    @Test
    @DisplayName("Should support <thought> tags and preserve < characters that are not tags")
    void supportsThoughtTagsAndLiteralAngleBrackets() {
        StringBuilder thoughts = new StringBuilder();
        StringBuilder answer = new StringBuilder();
        ThinkTagStreamParser parser = new ThinkTagStreamParser(thoughts::append, answer::append);

        parser.accept("<thought>x < 5</thought>Value is x < 5.");
        parser.flush();

        assertThat(thoughts.toString()).isEqualTo("x < 5");
        assertThat(answer.toString()).isEqualTo("Value is x < 5.");
    }

    @Test
    @DisplayName("Should pass plain text directly to answer callback")
    void passesPlainTextDirectly() {
        StringBuilder thoughts = new StringBuilder();
        StringBuilder answer = new StringBuilder();
        ThinkTagStreamParser parser = new ThinkTagStreamParser(thoughts::append, answer::append);

        parser.accept("Direct answer without thinking tags.");
        parser.flush();

        assertThat(thoughts.toString()).isEmpty();
        assertThat(answer.toString()).isEqualTo("Direct answer without thinking tags.");
    }
}
