package com.tfassbender.loreweave.git;

/**
 * Raised when a git clone or pull fails. Carries a short human-readable
 * message suitable for inclusion in {@code /health}'s {@code last_sync} block.
 */
public class GitSyncException extends RuntimeException {

    public GitSyncException(String message) {
        super(message);
    }

    public GitSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
