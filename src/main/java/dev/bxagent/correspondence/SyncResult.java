package dev.bxagent.correspondence;

import java.util.List;

/**
 * Summary of what a sync() invocation did.
 * Conflicts is non-empty only when SyncConflictPolicy.LOG_AND_SKIP was used.
 */
public record SyncResult(
        int objectsUpdatedForward,
        int objectsUpdatedBackward,
        int objectsCreatedForward,
        int objectsCreatedBackward,
        int objectsDeleted,
        int objectsLinked,
        List<SyncConflict> conflicts
) {
    /** Convenience constructor for the no-conflict, no-linked case. */
    public static SyncResult of(int fwd, int bwd, int createdFwd, int createdBwd, int deleted) {
        return new SyncResult(fwd, bwd, createdFwd, createdBwd, deleted, 0, List.of());
    }
}
