package com.tfassbender.loreweave.parsing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TitleResolverTest {

    private final TitleResolver resolver = new TitleResolver();

    @Test
    void usesFrontmatterTitleWhenPresent() {
        TitleResolver.Resolved r = resolver.resolve("Kael Varyn", "character_kael_varyn", "character_kael_varyn");
        assertThat(r.title()).isEqualTo("Kael Varyn");
        assertThat(r.missingTitleWarning()).isFalse();
    }

    @Test
    void fallsBackToFilenameAndWarns() {
        TitleResolver.Resolved r = resolver.resolve(null, "Kael Varyn", "character_kael_varyn");
        assertThat(r.title()).isEqualTo("Kael Varyn");
        assertThat(r.missingTitleWarning()).isTrue();
    }

    @Test
    void derivesFromIdWhenFilenameAlsoAbsent() {
        TitleResolver.Resolved r = resolver.resolve(null, null, "character_kael_varyn");
        assertThat(r.title()).isEqualTo("Kael Varyn");
        assertThat(r.missingTitleWarning()).isTrue();
    }

    @Test
    void blankFrontmatterTitleIsTreatedAsAbsent() {
        TitleResolver.Resolved r = resolver.resolve("  ", "fallback", "character_x");
        assertThat(r.title()).isEqualTo("fallback");
        assertThat(r.missingTitleWarning()).isTrue();
    }
}
