package com.tfassbender.loreweave.graph;

/**
 * Raised when {@link SyncService#syncNow()} is called while another sync is
 * already running and the wait timed out. Phase 5 will map this to a 409
 * at the REST boundary.
 */
public class SyncInProgressException extends RuntimeException {

    public SyncInProgressException(String message) {
        super(message);
    }
}
