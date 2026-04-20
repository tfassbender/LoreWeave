package com.tfassbender.loreweave.rest;

import com.tfassbender.loreweave.domain.Backlink;
import com.tfassbender.loreweave.domain.Note;
import com.tfassbender.loreweave.graph.Index;
import com.tfassbender.loreweave.graph.IndexedNote;
import com.tfassbender.loreweave.graph.ResolvedLink;
import com.tfassbender.loreweave.rest.dto.BacklinkDto;
import com.tfassbender.loreweave.rest.dto.LinkDto;
import com.tfassbender.loreweave.rest.dto.NoteDto;
import com.tfassbender.loreweave.rest.dto.NoteSummaryDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Converts graph-layer records into API DTOs. Unresolved forward links are
 * dropped at this boundary — they surface only via {@code /health}'s
 * validation report, not on individual note responses.
 */
public final class DtoMapper {

    private DtoMapper() {}

    public static NoteDto toNoteDto(IndexedNote indexed, Index index) {
        Note n = indexed.note();
        List<LinkDto> links = new ArrayList<>();
        for (ResolvedLink rl : indexed.resolvedLinks()) {
            if (!rl.isResolved()) continue;
            Optional<IndexedNote> target = index.get(rl.targetKey().orElseThrow());
            if (target.isEmpty()) continue;
            Note t = target.get().note();
            links.add(new LinkDto(t.key(), t.title(), t.type(), rl.link().displayText()));
        }
        List<BacklinkDto> backlinks = new ArrayList<>();
        for (Backlink b : indexed.backlinks()) {
            Optional<IndexedNote> src = index.get(b.sourceKey());
            if (src.isEmpty()) continue;
            Note s = src.get().note();
            backlinks.add(new BacklinkDto(s.key(), s.title(), s.type(), b.displayText()));
        }
        return new NoteDto(
                n.key(), n.title(), n.type(), n.summary(),
                n.tags(), n.body(), n.metadata(),
                List.copyOf(links), List.copyOf(backlinks),
                n.schemaVersion());
    }

    public static NoteSummaryDto toSummary(IndexedNote indexed, double score) {
        Note n = indexed.note();
        return new NoteSummaryDto(n.key(), n.title(), n.type(), n.summary(), n.tags(), score);
    }
}
