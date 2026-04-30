package com.tfassbender.loreweave.git;

/**
 * A single file changed by a commit.
 *
 * @param path   vault-relative path of the file as of the new tree (or old tree, for deletes)
 * @param change kind of change applied to {@link #path()}
 */
public record FileChange(String path, ChangeType change) {

    public enum ChangeType {
        ADDED,
        MODIFIED,
        DELETED,
        RENAMED,
        COPIED
    }
}
