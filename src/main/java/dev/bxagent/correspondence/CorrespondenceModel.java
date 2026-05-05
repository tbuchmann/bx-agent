package dev.bxagent.correspondence;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for all operations on the correspondence model.
 * The EPackage is defined programmatically and registered in EPackage.Registry.INSTANCE
 * when this class is first loaded.
 */
public class CorrespondenceModel {

    // ── EPackage (programmatically defined) ────────────────────────────────
    public static final EPackage CORR_PACKAGE;
    private static final EClass  CORR_MODEL_ECLASS;
    private static final EClass  CORR_ENTRY_ECLASS;

    // CorrespondenceModel attributes
    private static final EAttribute CM_SOURCE_MODEL_URI;
    private static final EAttribute CM_TARGET_MODEL_URI;
    private static final EAttribute CM_TRANSFORMATION_CLASS;
    private static final EAttribute CM_CREATED_AT;
    private static final EAttribute CM_LAST_SYNCED_AT;
    private static final EReference CM_ENTRIES;

    // CorrespondenceEntry references and attributes
    private static final EReference CE_SOURCE_OBJECT;
    private static final EAttribute CE_SOURCE_TYPE;
    private static final EAttribute CE_SOURCE_FINGERPRINT;
    private static final EReference CE_TARGET_OBJECT;
    private static final EAttribute CE_TARGET_TYPE;
    private static final EAttribute CE_TARGET_FINGERPRINT;
    private static final EAttribute CE_ORPHANED;
    private static final EAttribute CE_LAST_SYNCED_AT;
    private static final EAttribute CE_SOURCE_CONTAINMENT_ROLE;
    private static final EAttribute CE_TARGET_CONTAINMENT_ROLE;
    private static final EAttribute CE_IS_AGGREGATION;
    private static final EAttribute CE_IS_STRUCTURAL_DEDUP;

    static {
        EcoreFactory ef = EcoreFactory.eINSTANCE;
        EcorePackage ep = EcorePackage.eINSTANCE;

        CORR_PACKAGE = ef.createEPackage();
        CORR_PACKAGE.setName("correspondence");
        CORR_PACKAGE.setNsPrefix("corr");
        CORR_PACKAGE.setNsURI("http://emtagent/correspondence/1.0");

        CORR_MODEL_ECLASS = ef.createEClass();
        CORR_MODEL_ECLASS.setName("CorrespondenceModel");

        CORR_ENTRY_ECLASS = ef.createEClass();
        CORR_ENTRY_ECLASS.setName("CorrespondenceEntry");

        // CorrespondenceModel attributes
        CM_SOURCE_MODEL_URI      = strAttr(ef, ep, "sourceModelURI");
        CM_TARGET_MODEL_URI      = strAttr(ef, ep, "targetModelURI");
        CM_TRANSFORMATION_CLASS  = strAttr(ef, ep, "transformationClass");
        CM_CREATED_AT            = strAttr(ef, ep, "createdAt");
        CM_LAST_SYNCED_AT        = strAttr(ef, ep, "lastSyncedAt");

        CM_ENTRIES = ef.createEReference();
        CM_ENTRIES.setName("entries");
        CM_ENTRIES.setEType(CORR_ENTRY_ECLASS);
        CM_ENTRIES.setContainment(true);
        CM_ENTRIES.setUpperBound(-1);

        CORR_MODEL_ECLASS.getEStructuralFeatures().addAll(List.of(
                CM_SOURCE_MODEL_URI, CM_TARGET_MODEL_URI, CM_TRANSFORMATION_CLASS,
                CM_CREATED_AT, CM_LAST_SYNCED_AT, CM_ENTRIES));

        // CorrespondenceEntry references and attributes
        CE_SOURCE_OBJECT = ef.createEReference();
        CE_SOURCE_OBJECT.setName("sourceObject");
        CE_SOURCE_OBJECT.setEType(ep.getEObject());
        CE_SOURCE_OBJECT.setContainment(false);
        CE_SOURCE_OBJECT.setUpperBound(1);

        CE_SOURCE_TYPE        = strAttr(ef, ep, "sourceType");
        CE_SOURCE_FINGERPRINT = strAttr(ef, ep, "sourceFingerprint");

        CE_TARGET_OBJECT = ef.createEReference();
        CE_TARGET_OBJECT.setName("targetObject");
        CE_TARGET_OBJECT.setEType(ep.getEObject());
        CE_TARGET_OBJECT.setContainment(false);
        CE_TARGET_OBJECT.setUpperBound(1);

        CE_TARGET_TYPE        = strAttr(ef, ep, "targetType");
        CE_TARGET_FINGERPRINT = strAttr(ef, ep, "targetFingerprint");

        CE_ORPHANED = ef.createEAttribute();
        CE_ORPHANED.setName("orphaned");
        CE_ORPHANED.setEType(ep.getEBoolean());

        CE_LAST_SYNCED_AT = strAttr(ef, ep, "lastSyncedAt");

        CE_SOURCE_CONTAINMENT_ROLE = strAttr(ef, ep, "sourceContainmentRole");
        CE_TARGET_CONTAINMENT_ROLE = strAttr(ef, ep, "targetContainmentRole");

        CE_IS_AGGREGATION = ef.createEAttribute();
        CE_IS_AGGREGATION.setName("isAggregation");
        CE_IS_AGGREGATION.setEType(ep.getEBoolean());

        CE_IS_STRUCTURAL_DEDUP = ef.createEAttribute();
        CE_IS_STRUCTURAL_DEDUP.setName("isStructuralDedup");
        CE_IS_STRUCTURAL_DEDUP.setEType(ep.getEBoolean());

        CORR_ENTRY_ECLASS.getEStructuralFeatures().addAll(List.of(
                CE_SOURCE_OBJECT, CE_SOURCE_TYPE, CE_SOURCE_FINGERPRINT,
                CE_TARGET_OBJECT, CE_TARGET_TYPE, CE_TARGET_FINGERPRINT,
                CE_ORPHANED, CE_LAST_SYNCED_AT,
                CE_SOURCE_CONTAINMENT_ROLE, CE_TARGET_CONTAINMENT_ROLE,
                CE_IS_AGGREGATION, CE_IS_STRUCTURAL_DEDUP));

        CORR_PACKAGE.getEClassifiers().addAll(List.of(CORR_MODEL_ECLASS, CORR_ENTRY_ECLASS));

        // Register package and XMI factory
        EPackage.Registry.INSTANCE.put(CORR_PACKAGE.getNsURI(), CORR_PACKAGE);
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("xmi", new XMIResourceFactoryImpl());
    }

    private static EAttribute strAttr(EcoreFactory ef, EcorePackage ep, String name) {
        EAttribute a = ef.createEAttribute();
        a.setName(name);
        a.setEType(ep.getEString());
        return a;
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Derives the .corr.xmi path from the two model URIs.
     * Convention: &lt;sourceBasename&gt;_&lt;targetBasename&gt;.corr.xmi
     * in the same directory as the source model.
     */
    public static URI deriveCorrespondenceURI(URI sourceURI, URI targetURI) {
        String sourceName = sourceURI.trimFileExtension().lastSegment();
        String targetName = targetURI.trimFileExtension().lastSegment();
        String corrName   = sourceName + "_" + targetName + ".corr.xmi";
        return sourceURI.trimSegments(1).appendSegment(corrName);
    }

    /**
     * Loads an existing .corr.xmi or creates a new empty CorrespondenceModel
     * resource when the file does not exist.
     */
    public static Resource loadOrCreate(URI corrURI, ResourceSet rs) {
        // Return existing resource if already loaded in this ResourceSet.
        // rs.createResource() does NOT check for duplicates; calling it twice with the
        // same URI would produce two Resource objects with the same URI, causing
        // EMF proxy-resolution ambiguity and breaking the == checks in findBySource/buildIndex.
        Resource existing = rs.getResource(corrURI, false);
        if (existing != null) return existing;

        // Ensure XMI factory is available in this resource set
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .putIfAbsent("xmi", new XMIResourceFactoryImpl());

        Resource resource = rs.createResource(corrURI);
        if (resource == null) {
            // No factory matched the URI — fall back to XMI
            resource = new org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl(corrURI);
            rs.getResources().add(resource);
        }
        try {
            resource.load(Collections.emptyMap());
        } catch (Exception ignored) {
            // File doesn't exist → start with empty resource
        }
        if (resource.getContents().isEmpty()) {
            EObject model = CORR_PACKAGE.getEFactoryInstance().create(CORR_MODEL_ECLASS);
            model.eSet(CM_CREATED_AT, Instant.now().toString());
            model.eSet(CM_LAST_SYNCED_AT, Instant.now().toString());
            resource.getContents().add(model);
        }
        return resource;
    }

    /**
     * Builds a BiMap index from a loaded correspondence resource.
     * Key: source EObject, Value: target EObject.
     * Skips aggregation entries (isAggregation=true) — use buildAggregationIndex() for those.
     */
    public static BiMap<EObject, EObject> buildIndex(Resource corrResource) {
        BiMap<EObject, EObject> index = HashBiMap.create();
        if (corrResource.getContents().isEmpty()) return index;
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        List<EObject> fullyStaleEntries = new ArrayList<>();

        for (EObject entry : entries) {
            // Aggregation and structural dedup entries are handled by their own index builders
            if (Boolean.TRUE.equals(entry.eGet(CE_IS_AGGREGATION))) continue;
            if (Boolean.TRUE.equals(entry.eGet(CE_IS_STRUCTURAL_DEDUP))) continue;

            EObject srcObj = (EObject) entry.eGet(CE_SOURCE_OBJECT);
            EObject tgtObj = (EObject) entry.eGet(CE_TARGET_OBJECT);

            boolean srcMissing = srcObj == null || srcObj.eIsProxy();
            boolean tgtMissing = tgtObj == null || tgtObj.eIsProxy();

            if (!srcMissing && !tgtMissing) {
                // Fully valid entry.
                if (!index.containsKey(srcObj)) {
                    index.put(srcObj, tgtObj);
                }
            } else if (srcMissing && !tgtMissing) {
                // Source is an unresolved proxy → the source object was deleted from its resource.
                // Nullify CE_SOURCE_OBJECT so findDeletedSourceEntries() picks it up in Phase 2.
                if (srcObj != null) {
                    entry.eSet(CE_SOURCE_OBJECT, null);
                }
                // Do NOT add to index; Phase 2 will cascade-delete the target.
            } else if (!srcMissing && tgtMissing) {
                if (tgtObj == null) {
                    // Target was explicitly deleted (CE_TARGET_OBJECT nullified by EcoreUtil.delete).
                    // Do NOT add to index; Phase 2 backward will cascade-delete the source.
                } else {
                    // Target is an unresolved proxy → target resource was lost (previous run failed to save).
                    // Delete the corr entry so Phase 1 treats the source object as new and re-creates the target.
                    fullyStaleEntries.add(entry);
                }
            } else {
                // Both missing → completely stale.
                fullyStaleEntries.add(entry);
            }
        }
        entries.removeAll(fullyStaleEntries);
        return index;
    }

    /**
     * Builds a Map (source→target, many-to-one allowed) from aggregation entries only.
     * Also handles stale proxy cleanup for aggregation entries.
     */
    public static java.util.Map<EObject, EObject> buildAggregationIndex(Resource corrResource) {
        java.util.Map<EObject, EObject> index = new java.util.LinkedHashMap<>();
        if (corrResource.getContents().isEmpty()) return index;
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        List<EObject> fullyStaleEntries = new ArrayList<>();

        for (EObject entry : entries) {
            if (!Boolean.TRUE.equals(entry.eGet(CE_IS_AGGREGATION))) continue;

            EObject srcObj = (EObject) entry.eGet(CE_SOURCE_OBJECT);
            EObject tgtObj = (EObject) entry.eGet(CE_TARGET_OBJECT);

            boolean srcMissing = srcObj == null || srcObj.eIsProxy();
            boolean tgtMissing = tgtObj == null || tgtObj.eIsProxy();

            if (!srcMissing && !tgtMissing) {
                index.put(srcObj, tgtObj);
            } else if (srcMissing && !tgtMissing) {
                // Source was deleted — nullify so findDeletedAggregationSourceEntries picks it up
                if (srcObj != null) entry.eSet(CE_SOURCE_OBJECT, null);
            } else if (!srcMissing && tgtMissing) {
                fullyStaleEntries.add(entry);
            } else {
                fullyStaleEntries.add(entry);
            }
        }
        entries.removeAll(fullyStaleEntries);
        return index;
    }

    /**
     * Builds a reverse aggregation index: target EObject → list of source EObjects.
     * Derived from aggregation entries; source objects with null CE_SOURCE_OBJECT are excluded.
     */
    public static java.util.Map<EObject, List<EObject>> buildReverseAggregationIndex(Resource corrResource) {
        java.util.Map<EObject, List<EObject>> index = new java.util.LinkedHashMap<>();
        if (corrResource.getContents().isEmpty()) return index;
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);

        for (EObject entry : entries) {
            if (!Boolean.TRUE.equals(entry.eGet(CE_IS_AGGREGATION))) continue;
            EObject srcObj = (EObject) entry.eGet(CE_SOURCE_OBJECT);
            EObject tgtObj = (EObject) entry.eGet(CE_TARGET_OBJECT);
            if (srcObj != null && !srcObj.eIsProxy() && tgtObj != null && !tgtObj.eIsProxy()) {
                index.computeIfAbsent(tgtObj, k -> new ArrayList<>()).add(srcObj);
            }
        }
        return index;
    }

    /**
     * Finds a CorrespondenceEntry by source EObject (identity comparison).
     */
    public static Optional<EObject> findBySource(Resource corrResource, EObject sourceObj) {
        if (corrResource.getContents().isEmpty()) return Optional.empty();
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        return entries.stream()
                .filter(e -> e.eGet(CE_SOURCE_OBJECT) == sourceObj)
                .findFirst();
    }

    /**
     * Adds a new CorrespondenceEntry to the resource (without target fingerprint or containment roles).
     */
    public static void addEntry(Resource corrResource,
                                EObject sourceObj, String sourceType, String sourceFingerprint,
                                EObject targetObj, String targetType) {
        addEntry(corrResource, sourceObj, sourceType, sourceFingerprint, targetObj, targetType, "", "", "");
    }

    /**
     * Adds a new CorrespondenceEntry to the resource (with target fingerprint, without containment roles).
     */
    public static void addEntry(Resource corrResource,
                                EObject sourceObj, String sourceType, String sourceFingerprint,
                                EObject targetObj, String targetType, String targetFingerprint) {
        addEntry(corrResource, sourceObj, sourceType, sourceFingerprint, targetObj, targetType, targetFingerprint, "", "");
    }

    /**
     * Adds a new CorrespondenceEntry to the resource (full: with target fingerprint and containment roles).
     */
    public static void addEntry(Resource corrResource,
                                EObject sourceObj, String sourceType, String sourceFingerprint,
                                EObject targetObj, String targetType, String targetFingerprint,
                                String sourceContainmentRole, String targetContainmentRole) {
        EObject model = getOrCreateModel(corrResource);
        EObject entry = CORR_PACKAGE.getEFactoryInstance().create(CORR_ENTRY_ECLASS);
        entry.eSet(CE_SOURCE_OBJECT, sourceObj);
        entry.eSet(CE_SOURCE_TYPE, sourceType);
        entry.eSet(CE_SOURCE_FINGERPRINT, sourceFingerprint);
        entry.eSet(CE_TARGET_OBJECT, targetObj);
        entry.eSet(CE_TARGET_TYPE, targetType);
        entry.eSet(CE_TARGET_FINGERPRINT, targetFingerprint != null ? targetFingerprint : "");
        entry.eSet(CE_ORPHANED, false);
        entry.eSet(CE_LAST_SYNCED_AT, Instant.now().toString());
        entry.eSet(CE_SOURCE_CONTAINMENT_ROLE, sourceContainmentRole != null ? sourceContainmentRole : "");
        entry.eSet(CE_TARGET_CONTAINMENT_ROLE, targetContainmentRole != null ? targetContainmentRole : "");
        entry.eSet(CE_IS_AGGREGATION, false);
        entry.eSet(CE_IS_STRUCTURAL_DEDUP, false);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        entries.add(entry);
    }

    /**
     * Adds a new aggregation CorrespondenceEntry (many source objects may share the same target).
     */
    public static void addAggregationEntry(Resource corrResource,
                                           EObject sourceObj, String sourceType, String sourceFingerprint,
                                           EObject targetObj, String targetType, String targetFingerprint) {
        EObject model = getOrCreateModel(corrResource);
        EObject entry = CORR_PACKAGE.getEFactoryInstance().create(CORR_ENTRY_ECLASS);
        entry.eSet(CE_SOURCE_OBJECT, sourceObj);
        entry.eSet(CE_SOURCE_TYPE, sourceType);
        entry.eSet(CE_SOURCE_FINGERPRINT, sourceFingerprint);
        entry.eSet(CE_TARGET_OBJECT, targetObj);
        entry.eSet(CE_TARGET_TYPE, targetType);
        entry.eSet(CE_TARGET_FINGERPRINT, targetFingerprint != null ? targetFingerprint : "");
        entry.eSet(CE_ORPHANED, false);
        entry.eSet(CE_LAST_SYNCED_AT, Instant.now().toString());
        entry.eSet(CE_SOURCE_CONTAINMENT_ROLE, "");
        entry.eSet(CE_TARGET_CONTAINMENT_ROLE, "");
        entry.eSet(CE_IS_AGGREGATION, true);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        entries.add(entry);
    }

    /**
     * Marks a CorrespondenceEntry as orphaned (for TOMBSTONE deletion policy).
     */
    public static void markOrphaned(Resource corrResource, EObject sourceObj) {
        findBySource(corrResource, sourceObj).ifPresent(e -> {
            e.eSet(CE_ORPHANED, true);
            e.eSet(CE_LAST_SYNCED_AT, Instant.now().toString());
        });
    }

    /**
     * Removes a CorrespondenceEntry (for CASCADE and ORPHAN deletion policies).
     */
    public static void removeEntry(Resource corrResource, EObject sourceObj) {
        if (corrResource.getContents().isEmpty()) return;
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        entries.removeIf(e -> e.eGet(CE_SOURCE_OBJECT) == sourceObj);
    }

    /**
     * Removes a CorrespondenceEntry directly by entry reference (for null-reference deletion detection).
     */
    public static void removeCorrespondenceEntry(Resource corrResource, EObject corrEntry) {
        if (corrResource.getContents().isEmpty()) return;
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        entries.remove(corrEntry);
    }

    /**
     * Returns non-aggregation entries where sourceObject has been nulled out by EMF (object was deleted).
     * These represent source-side deletions that need to be propagated to the target.
     */
    public static List<EObject> findDeletedSourceEntries(Resource corrResource) {
        if (corrResource.getContents().isEmpty()) return Collections.emptyList();
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        List<EObject> result = new ArrayList<>();
        for (EObject entry : entries) {
            if (Boolean.TRUE.equals(entry.eGet(CE_IS_AGGREGATION))) continue;
            if (Boolean.TRUE.equals(entry.eGet(CE_IS_STRUCTURAL_DEDUP))) continue;
            if (entry.eGet(CE_SOURCE_OBJECT) == null && !Boolean.TRUE.equals(entry.eGet(CE_ORPHANED))) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Returns non-aggregation entries where targetObject has been nulled out by EMF (object was deleted).
     * These represent target-side deletions that need to be propagated back to the source.
     */
    public static List<EObject> findDeletedTargetEntries(Resource corrResource) {
        if (corrResource.getContents().isEmpty()) return Collections.emptyList();
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        List<EObject> result = new ArrayList<>();
        for (EObject entry : entries) {
            if (Boolean.TRUE.equals(entry.eGet(CE_IS_AGGREGATION))) continue;
            if (Boolean.TRUE.equals(entry.eGet(CE_IS_STRUCTURAL_DEDUP))) continue;
            if (entry.eGet(CE_TARGET_OBJECT) == null && !Boolean.TRUE.equals(entry.eGet(CE_ORPHANED))) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Returns aggregation entries where sourceObject has been nulled out (source element deleted).
     * Handled by materializeAggregationIncremental rather than the regular Phase 2 loop.
     */
    public static List<EObject> findDeletedAggregationSourceEntries(Resource corrResource) {
        if (corrResource.getContents().isEmpty()) return Collections.emptyList();
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        List<EObject> result = new ArrayList<>();
        for (EObject entry : entries) {
            if (!Boolean.TRUE.equals(entry.eGet(CE_IS_AGGREGATION))) continue;
            if (entry.eGet(CE_SOURCE_OBJECT) == null) result.add(entry);
        }
        return result;
    }

    /**
     * Returns aggregation entries where targetObject has been nulled out (target element deleted).
     * Handled by materializeAggregationIncrementalBack rather than the regular Phase 2 loop.
     */
    public static List<EObject> findDeletedAggregationTargetEntries(Resource corrResource) {
        if (corrResource.getContents().isEmpty()) return Collections.emptyList();
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        List<EObject> result = new ArrayList<>();
        for (EObject entry : entries) {
            if (!Boolean.TRUE.equals(entry.eGet(CE_IS_AGGREGATION))) continue;
            if (entry.eGet(CE_TARGET_OBJECT) == null) result.add(entry);
        }
        return result;
    }

    /**
     * Adds a new structural deduplication CorrespondenceEntry.
     * Many source AST nodes may share the same target DAG node.
     */
    public static void addStructuralDedupEntry(Resource corrResource,
                                               EObject sourceObj, String sourceType, String sourceFingerprint,
                                               EObject targetObj, String targetType, String targetFingerprint) {
        EObject model = getOrCreateModel(corrResource);
        EObject entry = CORR_PACKAGE.getEFactoryInstance().create(CORR_ENTRY_ECLASS);
        entry.eSet(CE_SOURCE_OBJECT, sourceObj);
        entry.eSet(CE_SOURCE_TYPE, sourceType);
        entry.eSet(CE_SOURCE_FINGERPRINT, sourceFingerprint != null ? sourceFingerprint : "");
        entry.eSet(CE_TARGET_OBJECT, targetObj);
        entry.eSet(CE_TARGET_TYPE, targetType);
        entry.eSet(CE_TARGET_FINGERPRINT, targetFingerprint != null ? targetFingerprint : "");
        entry.eSet(CE_ORPHANED, false);
        entry.eSet(CE_LAST_SYNCED_AT, Instant.now().toString());
        entry.eSet(CE_SOURCE_CONTAINMENT_ROLE, "");
        entry.eSet(CE_TARGET_CONTAINMENT_ROLE, "");
        entry.eSet(CE_IS_AGGREGATION, false);
        entry.eSet(CE_IS_STRUCTURAL_DEDUP, true);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        entries.add(entry);
    }

    /**
     * Builds a Map (AST source→DAG target, many-to-one allowed) from structural dedup entries only.
     */
    public static java.util.Map<EObject, EObject> buildStructuralDedupIndex(Resource corrResource) {
        java.util.Map<EObject, EObject> index = new java.util.LinkedHashMap<>();
        if (corrResource.getContents().isEmpty()) return index;
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        List<EObject> staleEntries = new ArrayList<>();

        for (EObject entry : entries) {
            if (!Boolean.TRUE.equals(entry.eGet(CE_IS_STRUCTURAL_DEDUP))) continue;
            EObject srcObj = (EObject) entry.eGet(CE_SOURCE_OBJECT);
            EObject tgtObj = (EObject) entry.eGet(CE_TARGET_OBJECT);
            boolean srcMissing = srcObj == null || srcObj.eIsProxy();
            boolean tgtMissing = tgtObj == null || tgtObj.eIsProxy();
            if (!srcMissing && !tgtMissing) {
                index.put(srcObj, tgtObj);
            } else if (srcMissing && !tgtMissing) {
                if (srcObj != null) entry.eSet(CE_SOURCE_OBJECT, null);
            } else {
                staleEntries.add(entry);
            }
        }
        entries.removeAll(staleEntries);
        return index;
    }

    /**
     * Builds a reverse structural dedup index: DAG target → list of AST source copies.
     */
    public static java.util.Map<EObject, List<EObject>> buildReverseStructuralDedupIndex(Resource corrResource) {
        java.util.Map<EObject, List<EObject>> index = new java.util.LinkedHashMap<>();
        if (corrResource.getContents().isEmpty()) return index;
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        for (EObject entry : entries) {
            if (!Boolean.TRUE.equals(entry.eGet(CE_IS_STRUCTURAL_DEDUP))) continue;
            EObject srcObj = (EObject) entry.eGet(CE_SOURCE_OBJECT);
            EObject tgtObj = (EObject) entry.eGet(CE_TARGET_OBJECT);
            if (srcObj != null && !srcObj.eIsProxy() && tgtObj != null && !tgtObj.eIsProxy()) {
                index.computeIfAbsent(tgtObj, k -> new ArrayList<>()).add(srcObj);
            }
        }
        return index;
    }

    /**
     * Builds a fingerprint→target Map from structural dedup entries (for incremental forward).
     * Maps structural fingerprint (CE_SOURCE_FINGERPRINT) → existing DAG node.
     */
    public static java.util.Map<String, EObject> buildStructuralDedupFpIndex(Resource corrResource) {
        java.util.Map<String, EObject> index = new java.util.LinkedHashMap<>();
        if (corrResource.getContents().isEmpty()) return index;
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        for (EObject entry : entries) {
            if (!Boolean.TRUE.equals(entry.eGet(CE_IS_STRUCTURAL_DEDUP))) continue;
            EObject tgtObj = (EObject) entry.eGet(CE_TARGET_OBJECT);
            String srcFp = (String) entry.eGet(CE_SOURCE_FINGERPRINT);
            if (tgtObj != null && !tgtObj.eIsProxy() && srcFp != null && !srcFp.isEmpty()) {
                index.putIfAbsent(srcFp, tgtObj);
            }
        }
        return index;
    }

    /**
     * Finds a structural dedup CorrespondenceEntry by source AST EObject.
     */
    public static Optional<EObject> findStructuralDedupBySource(Resource corrResource, EObject sourceObj) {
        if (corrResource.getContents().isEmpty()) return Optional.empty();
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        return entries.stream()
                .filter(e -> Boolean.TRUE.equals(e.eGet(CE_IS_STRUCTURAL_DEDUP))
                          && e.eGet(CE_SOURCE_OBJECT) == sourceObj)
                .findFirst();
    }

    /**
     * Returns structural dedup entries where sourceObject has been nulled out (AST node deleted).
     */
    public static List<EObject> findDeletedStructuralDedupSourceEntries(Resource corrResource) {
        if (corrResource.getContents().isEmpty()) return Collections.emptyList();
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        List<EObject> result = new ArrayList<>();
        for (EObject entry : entries) {
            if (!Boolean.TRUE.equals(entry.eGet(CE_IS_STRUCTURAL_DEDUP))) continue;
            if (entry.eGet(CE_SOURCE_OBJECT) == null) result.add(entry);
        }
        return result;
    }

    /**
     * Returns structural dedup entries where targetObject has been nulled out (DAG node deleted).
     */
    public static List<EObject> findDeletedStructuralDedupTargetEntries(Resource corrResource) {
        if (corrResource.getContents().isEmpty()) return Collections.emptyList();
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        List<EObject> result = new ArrayList<>();
        for (EObject entry : entries) {
            if (!Boolean.TRUE.equals(entry.eGet(CE_IS_STRUCTURAL_DEDUP))) continue;
            if (entry.eGet(CE_TARGET_OBJECT) == null) result.add(entry);
        }
        return result;
    }

    /**
     * Updates the target fingerprint on all aggregation entries pointing to the given target object.
     * Called when a group's multiplicity changes and the target fingerprint must be refreshed.
     */
    public static void updateAllAggregationTargetFingerprints(Resource corrResource, EObject targetObj, String newTargetFp) {
        if (corrResource.getContents().isEmpty()) return;
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        for (EObject entry : entries) {
            if (!Boolean.TRUE.equals(entry.eGet(CE_IS_AGGREGATION))) continue;
            if (entry.eGet(CE_TARGET_OBJECT) == targetObj) {
                entry.eSet(CE_TARGET_FINGERPRINT, newTargetFp != null ? newTargetFp : "");
                entry.eSet(CE_LAST_SYNCED_AT, Instant.now().toString());
            }
        }
    }

    /**
     * Marks a CorrespondenceEntry as orphaned directly by entry reference.
     */
    public static void markOrphaned(EObject corrEntry) {
        corrEntry.eSet(CE_ORPHANED, true);
        corrEntry.eSet(CE_LAST_SYNCED_AT, Instant.now().toString());
    }

    /**
     * Updates lastSyncedAt on the CorrespondenceModel and saves the resource.
     */
    public static void saveAndUpdateTimestamp(Resource corrResource) {
        if (!corrResource.getContents().isEmpty()) {
            corrResource.getContents().get(0).eSet(CM_LAST_SYNCED_AT, Instant.now().toString());
        }
        if (corrResource.getURI() != null && !corrResource.getURI().toString().startsWith("memory://")) {
            try {
                corrResource.save(Collections.emptyMap());
            } catch (IOException e) {
                throw new RuntimeException("Failed to save correspondence model", e);
            }
        }
    }

    // ── Helpers used by generated transformation code ───────────────────────

    /** Returns the stored source fingerprint of a CorrespondenceEntry. */
    public static String getFingerprint(EObject corrEntry) {
        return (String) corrEntry.eGet(CE_SOURCE_FINGERPRINT);
    }

    /** Updates the source fingerprint and lastSyncedAt of a CorrespondenceEntry. */
    public static void updateFingerprint(EObject corrEntry, String fingerprint) {
        corrEntry.eSet(CE_SOURCE_FINGERPRINT, fingerprint);
        corrEntry.eSet(CE_LAST_SYNCED_AT, Instant.now().toString());
    }

    /** Returns true if the entry has been marked as orphaned. */
    public static boolean isOrphaned(EObject corrEntry) {
        return Boolean.TRUE.equals(corrEntry.eGet(CE_ORPHANED));
    }

    /** Returns the source EObject of a CorrespondenceEntry. */
    public static EObject getSourceObject(EObject corrEntry) {
        return (EObject) corrEntry.eGet(CE_SOURCE_OBJECT);
    }

    /** Returns the target EObject of a CorrespondenceEntry. */
    public static EObject getTargetObject(EObject corrEntry) {
        return (EObject) corrEntry.eGet(CE_TARGET_OBJECT);
    }

    /** Returns the sourceType of a CorrespondenceEntry. */
    public static String getSourceType(EObject corrEntry) {
        return (String) corrEntry.eGet(CE_SOURCE_TYPE);
    }

    /** Returns the targetType of a CorrespondenceEntry. */
    public static String getTargetType(EObject corrEntry) {
        return (String) corrEntry.eGet(CE_TARGET_TYPE);
    }

    /** Returns the stored target fingerprint of a CorrespondenceEntry. */
    public static String getTargetFingerprint(EObject corrEntry) {
        return (String) corrEntry.eGet(CE_TARGET_FINGERPRINT);
    }

    /** Updates the target fingerprint and lastSyncedAt of a CorrespondenceEntry. */
    public static void updateTargetFingerprint(EObject corrEntry, String fingerprint) {
        corrEntry.eSet(CE_TARGET_FINGERPRINT, fingerprint != null ? fingerprint : "");
        corrEntry.eSet(CE_LAST_SYNCED_AT, Instant.now().toString());
    }

    /** Replaces the target object and type of a CorrespondenceEntry (for role changes). */
    public static void updateTargetObject(EObject corrEntry, EObject newTargetObj, String newTargetType) {
        corrEntry.eSet(CE_TARGET_OBJECT, newTargetObj);
        corrEntry.eSet(CE_TARGET_TYPE, newTargetType);
        corrEntry.eSet(CE_LAST_SYNCED_AT, Instant.now().toString());
    }

    /** Replaces the source object and type of a CorrespondenceEntry (used by sync() Partition 3). */
    public static void updateSourceObject(EObject corrEntry, EObject newSourceObj, String newSourceType) {
        corrEntry.eSet(CE_SOURCE_OBJECT, newSourceObj);
        corrEntry.eSet(CE_SOURCE_TYPE, newSourceType);
        corrEntry.eSet(CE_LAST_SYNCED_AT, Instant.now().toString());
    }

    /**
     * Returns a snapshot list of all CorrespondenceEntry objects in the resource.
     * Snapshot is safe to iterate while modifying the live collection.
     */
    public static List<EObject> getAllEntries(Resource corrResource) {
        if (corrResource.getContents().isEmpty()) return Collections.emptyList();
        EObject model = corrResource.getContents().get(0);
        @SuppressWarnings("unchecked")
        EList<EObject> entries = (EList<EObject>) model.eGet(CM_ENTRIES);
        return new ArrayList<>(entries);
    }

    /** Returns the source containment role of a CorrespondenceEntry (e.g. "father", "sons"). */
    public static String getSourceContainmentRole(EObject corrEntry) {
        return (String) corrEntry.eGet(CE_SOURCE_CONTAINMENT_ROLE);
    }

    /** Updates the source containment role of a CorrespondenceEntry. */
    public static void updateSourceContainmentRole(EObject corrEntry, String role) {
        corrEntry.eSet(CE_SOURCE_CONTAINMENT_ROLE, role != null ? role : "");
        corrEntry.eSet(CE_LAST_SYNCED_AT, Instant.now().toString());
    }

    /** Returns the target containment role of a CorrespondenceEntry. */
    public static String getTargetContainmentRole(EObject corrEntry) {
        return (String) corrEntry.eGet(CE_TARGET_CONTAINMENT_ROLE);
    }

    /** Updates the target containment role of a CorrespondenceEntry. */
    public static void updateTargetContainmentRole(EObject corrEntry, String role) {
        corrEntry.eSet(CE_TARGET_CONTAINMENT_ROLE, role != null ? role : "");
        corrEntry.eSet(CE_LAST_SYNCED_AT, Instant.now().toString());
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private static EObject getOrCreateModel(Resource corrResource) {
        if (corrResource.getContents().isEmpty()) {
            EObject model = CORR_PACKAGE.getEFactoryInstance().create(CORR_MODEL_ECLASS);
            model.eSet(CM_CREATED_AT, Instant.now().toString());
            corrResource.getContents().add(model);
            return model;
        }
        return corrResource.getContents().get(0);
    }
}
