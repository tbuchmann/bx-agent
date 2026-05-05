package dev.bxagent.correspondence;

/**
 * Policy for resolving conflicts in sync() when both source and target
 * have been independently modified since the last synchronisation.
 */
public enum SyncConflictPolicy {
    /** Source overwrites target (default). */
    SOURCE_WINS,
    /** Target overwrites source. */
    TARGET_WINS,
    /** Neither side is propagated; the conflict is recorded in SyncResult.conflicts(). */
    LOG_AND_SKIP
}
