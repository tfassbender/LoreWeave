package com.tfassbender.loreweave.git;

/**
 * Outcome of a successful pull.
 *
 * @param branch         the branch that was updated
 * @param fromRef        the commit SHA before the pull
 * @param toRef          the commit SHA after the pull (equal to {@code fromRef} if already up to date)
 * @param changedFiles   number of files touched by the update (0 if already up to date)
 * @param forceReset     {@code true} iff we had to fall back from fast-forward to a hard reset
 */
public record PullResult(String branch, String fromRef, String toRef, int changedFiles, boolean forceReset) {

    public boolean upToDate() {
        return fromRef != null && fromRef.equals(toRef);
    }
}
