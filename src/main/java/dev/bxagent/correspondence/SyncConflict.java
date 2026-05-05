package dev.bxagent.correspondence;

import org.eclipse.emf.ecore.EObject;

/**
 * Describes a synchronisation conflict where both source and target were
 * independently modified since the last sync and LOG_AND_SKIP is active.
 */
public record SyncConflict(
        EObject sourceObject,
        EObject targetObject,
        String sourceType,
        String targetType,
        String storedSourceFingerprint,
        String currentSourceFingerprint,
        String storedTargetFingerprint,
        String currentTargetFingerprint
) {}
