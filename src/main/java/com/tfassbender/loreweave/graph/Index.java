package com.tfassbender.loreweave.graph;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable snapshot of a parsed vault. Holds the served notes (keyed by
 * ID) and the accompanying {@link ValidationReport}. A sync wipes and rebuilds
 * the {@code Index}; readers always see a consistent snapshot.
 */
public record Index(Map<String, IndexedNote> notesById, ValidationReport report) {

    public Index {
        notesById = notesById == null ? Map.of() : Map.copyOf(notesById);
        if (report == null) report = new ValidationReport(Map.of());
    }

    public Optional<IndexedNote> get(String id) {
        return Optional.ofNullable(notesById.get(id));
    }

    public Collection<IndexedNote> notes() {
        return notesById.values();
    }

    public int size() {
        return notesById.size();
    }
}
