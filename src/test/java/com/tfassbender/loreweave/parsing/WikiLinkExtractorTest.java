package com.tfassbender.loreweave.parsing;

import com.tfassbender.loreweave.domain.Link;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class WikiLinkExtractorTest {

    private final MarkdownBodyParser md = new MarkdownBodyParser();
    private final WikiLinkExtractor extractor = new WikiLinkExtractor();

    private List<Link> extract(String body) {
        return extractor.extract(md.parse(body));
    }

    @Test
    void extractsPlainWikiLink() {
        assertThat(extract("See [[Kael Varyn]] for details."))
                .extracting(Link::rawTarget, Link::displayText, Link::fragment)
                .containsExactly(tuple("Kael Varyn", null, null));
    }

    @Test
    void stripsPipeDisplay() {
        assertThat(extract("[[Faction - Outer Union|Union]]"))
                .extracting(Link::rawTarget, Link::displayText, Link::fragment)
                .containsExactly(tuple("Faction - Outer Union", "Union", null));
    }

    @Test
    void stripsHeadingFragment() {
        assertThat(extract("[[Kael Varyn#Backstory]]"))
                .extracting(Link::rawTarget, Link::fragment)
                .containsExactly(tuple("Kael Varyn", "Backstory"));
    }

    @Test
    void stripsBlockFragment() {
        assertThat(extract("[[Kael#^abc123]]"))
                .extracting(Link::rawTarget, Link::fragment)
                .containsExactly(tuple("Kael", "^abc123"));
    }

    @Test
    void embedsAreIgnored() {
        assertThat(extract("Inline ![[diagram.png]] and ![[page]] are embeds.")).isEmpty();
    }

    @Test
    void ignoresLinksInsideInlineCode() {
        assertThat(extract("`[[not a link]]` — right?")).isEmpty();
    }

    @Test
    void ignoresLinksInsideFencedCodeBlock() {
        String body = """
                ```
                [[Not a link either]]
                ```
                """;
        assertThat(extract(body)).isEmpty();
    }

    @Test
    void extractsMultipleLinksFromOneLine() {
        assertThat(extract("A [[One]] then [[Two|Second]] then [[Three#h]]."))
                .extracting(Link::rawTarget)
                .containsExactly("One", "Two", "Three");
    }
}
