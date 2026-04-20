package com.tfassbender.loreweave.parsing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdValidatorTest {

    private final IdValidator validator = new IdValidator();

    @Test
    void acceptsWellFormedId() {
        assertThat(validator.validate("character_kael_varyn", "character").valid()).isTrue();
    }

    @Test
    void rejectsMissingPrefix() {
        IdValidator.Result r = validator.validate("kael_varyn", "character");
        assertThat(r.valid()).isFalse();
        assertThat(r.message()).contains("does not match");
    }

    @Test
    void rejectsMismatchedPrefix() {
        IdValidator.Result r = validator.validate("event_kael_varyn", "character");
        assertThat(r.valid()).isFalse();
        assertThat(r.message()).contains("prefix 'event'");
    }

    @Test
    void rejectsUppercase() {
        assertThat(validator.validate("Character_kael", "Character").valid()).isFalse();
    }

    @Test
    void rejectsHyphen() {
        assertThat(validator.validate("character_kael-varyn", "character").valid()).isFalse();
    }

    @Test
    void rejectsLeadingDigit() {
        assertThat(validator.validate("1character_kael", "character").valid()).isFalse();
    }

    @Test
    void rejectsBlankId() {
        assertThat(validator.validate("", "character").valid()).isFalse();
        assertThat(validator.validate(null, "character").valid()).isFalse();
    }
}
