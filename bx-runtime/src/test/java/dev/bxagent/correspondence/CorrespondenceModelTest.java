package dev.bxagent.correspondence;

import com.google.common.collect.BiMap;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the CorrespondenceModel utility class and related infrastructure.
 * Uses in-memory EMF resources — no filesystem or external JAR required.
 */
class CorrespondenceModelTest {

    private ResourceSet rs;
    private Resource corrResource;

    /** Helper: create a fresh CorrespondenceEntry EObject to use as a stand-in for source/target. */
    private EObject makeEntry() {
        return CorrespondenceModel.CORR_PACKAGE.getEFactoryInstance()
                .create(CorrespondenceModel.CORR_PACKAGE.getEClassifiers().stream()
                        .filter(c -> "CorrespondenceEntry".equals(c.getName()))
                        .map(c -> (org.eclipse.emf.ecore.EClass) c)
                        .findFirst().get());
    }

    @BeforeEach
    void setUp() {
        rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("xmi", new XMIResourceFactoryImpl());
        // Load the correspondence package (triggers static initializer)
        // Triple-slash: scheme="memory", authority="", path="/test.corr.xmi"
        // EMF uses the ".xmi" extension to find the XMI resource factory.
        URI uri = URI.createURI("memory:///test.corr.xmi");
        corrResource = CorrespondenceModel.loadOrCreate(uri, rs);
    }

    // ── Test 1: loadOrCreate creates a fresh CorrespondenceModel ────────────

    @Test
    void testLoadOrCreateReturnsNonEmptyResource() {
        assertFalse(corrResource.getContents().isEmpty(),
                "loadOrCreate should create a root CorrespondenceModel EObject");
        EObject model = corrResource.getContents().get(0);
        assertEquals("CorrespondenceModel", model.eClass().getName());
    }

    // ── Test 2: addEntry + findBySource ────────────────────────────────────

    @Test
    void testAddEntryAndFindBySource() {
        EObject srcObj = makeEntry();
        EObject tgtObj = makeEntry();

        CorrespondenceModel.addEntry(corrResource,
                srcObj, "Person", "Person:Alice|",
                tgtObj, "Employee");

        Optional<EObject> found = CorrespondenceModel.findBySource(corrResource, srcObj);
        assertTrue(found.isPresent(), "Should find entry by source EObject");

        assertEquals("Person",   CorrespondenceModel.getSourceType(found.get()));
        assertEquals("Employee", CorrespondenceModel.getTargetType(found.get()));
        assertSame(tgtObj, CorrespondenceModel.getTargetObject(found.get()));
        assertSame(srcObj, CorrespondenceModel.getSourceObject(found.get()));
        assertEquals("Person:Alice|", CorrespondenceModel.getFingerprint(found.get()));
        assertFalse(CorrespondenceModel.isOrphaned(found.get()));
    }

    @Test
    void testFindBySourceReturnsEmptyForUnknownObject() {
        EObject unknown = makeEntry();
        Optional<EObject> found = CorrespondenceModel.findBySource(corrResource, unknown);
        assertTrue(found.isEmpty());
    }

    // ── Test 3: buildIndex ──────────────────────────────────────────────────

    @Test
    void testBuildIndex() {
        EObject srcA = makeEntry();
        EObject tgtX = makeEntry();
        EObject srcB = makeEntry();
        EObject tgtY = makeEntry();

        CorrespondenceModel.addEntry(corrResource,
                srcA, "TypeA", "fp:a", tgtX, "TypeX");
        CorrespondenceModel.addEntry(corrResource,
                srcB, "TypeB", "fp:b", tgtY, "TypeY");

        BiMap<EObject, EObject> index = CorrespondenceModel.buildIndex(corrResource);

        assertEquals(2, index.size());
        assertSame(tgtX, index.get(srcA));
        assertSame(tgtY, index.get(srcB));
        // Inverse lookup (BiMap)
        assertSame(srcA, index.inverse().get(tgtX));
    }

    // ── Test 4: markOrphaned ───────────────────────────────────────────────

    @Test
    void testMarkOrphaned() {
        EObject srcBart = makeEntry();
        EObject tgtBart = makeEntry();

        CorrespondenceModel.addEntry(corrResource,
                srcBart, "FamilyMember", "FM:Bart|", tgtBart, "Male");

        assertFalse(CorrespondenceModel.isOrphaned(
                CorrespondenceModel.findBySource(corrResource, srcBart).get()));

        CorrespondenceModel.markOrphaned(corrResource, srcBart);

        Optional<EObject> entry = CorrespondenceModel.findBySource(corrResource, srcBart);
        assertTrue(entry.isPresent(), "Orphaned entry should still exist");
        assertTrue(CorrespondenceModel.isOrphaned(entry.get()), "Entry should be marked orphaned");
    }

    // ── Test 5: removeEntry ────────────────────────────────────────────────

    @Test
    void testRemoveEntry() {
        EObject srcHomer = makeEntry();
        EObject tgtHomer = makeEntry();

        CorrespondenceModel.addEntry(corrResource,
                srcHomer, "FamilyMember", "FM:Homer|", tgtHomer, "Male");

        assertTrue(CorrespondenceModel.findBySource(corrResource, srcHomer).isPresent());

        CorrespondenceModel.removeEntry(corrResource, srcHomer);

        assertTrue(CorrespondenceModel.findBySource(corrResource, srcHomer).isEmpty(),
                "Entry should be removed");
    }

    // ── Test 6: updateFingerprint ──────────────────────────────────────────

    @Test
    void testUpdateFingerprint() {
        EObject srcMarge = makeEntry();
        EObject tgtMarge = makeEntry();

        CorrespondenceModel.addEntry(corrResource,
                srcMarge, "FamilyMember", "FM:Marge|", tgtMarge, "Female");

        EObject entry = CorrespondenceModel.findBySource(corrResource, srcMarge).get();
        assertEquals("FM:Marge|", CorrespondenceModel.getFingerprint(entry));

        CorrespondenceModel.updateFingerprint(entry, "FM:Marge-updated|");
        assertEquals("FM:Marge-updated|", CorrespondenceModel.getFingerprint(entry));
    }

    // ── Test 7: deriveCorrespondenceURI ────────────────────────────────────

    @Test
    void testDeriveCorrespondenceURI() {
        URI sourceURI = URI.createFileURI("/models/families.xmi");
        URI targetURI = URI.createFileURI("/models/persons.xmi");

        URI corrURI = CorrespondenceModel.deriveCorrespondenceURI(sourceURI, targetURI);

        assertTrue(corrURI.toString().endsWith("families_persons.corr.xmi"),
                "Corr URI should be <src>_<tgt>.corr.xmi, got: " + corrURI);
        assertTrue(corrURI.toString().contains("/models/"),
                "Corr URI should be in same directory as source model");
    }

    // ── Test 8: saveAndUpdateTimestamp (in-memory, no file write) ──────────

    @Test
    void testSaveAndUpdateTimestampDoesNotThrow() {
        EObject srcZ = makeEntry();
        EObject tgtZ = makeEntry();
        CorrespondenceModel.addEntry(corrResource,
                srcZ, "T", "fp:z", tgtZ, "U");
        // In-memory URI → should not throw, just updates timestamp
        assertDoesNotThrow(() -> CorrespondenceModel.saveAndUpdateTimestamp(corrResource));
    }

    // ── Test 9: TransformationContext builder ──────────────────────────────

    @Test
    void testTransformationContextBatchMode() {
        // Batch mode: no corrModel
        TransformationContext ctx = TransformationContext.builder()
                .sourceModel(corrResource)   // reusing resource as dummy
                .existingTarget(corrResource)
                .build();

        assertFalse(ctx.isIncremental());
        assertEquals(TransformationContext.Direction.SOURCE_TO_TARGET, ctx.getDirection());
        assertEquals(TransformationContext.DeletionPolicy.CASCADE, ctx.getDeletionPolicy());
    }

    @Test
    void testTransformationContextIncrementalMode() {
        // Incremental mode: corrModel provided
        TransformationContext ctx = TransformationContext.builder()
                .sourceModel(corrResource)
                .existingTarget(corrResource)
                .corrModel(corrResource)
                .build();

        assertTrue(ctx.isIncremental());
        assertNotNull(ctx.getCorrIndex(), "corrIndex should be built automatically");
    }

    @Test
    void testTransformationContextBuilderRequiresSourceModel() {
        assertThrows(IllegalStateException.class, () ->
                TransformationContext.builder().build());
    }

    // ── Test 10: ObjectIdentifier ──────────────────────────────────────────

    @Test
    void testObjectIdentifierFallback() {
        // EObject not attached to any resource → falls back to CONTENT_FINGERPRINT
        EObject obj = makeEntry();

        ObjectIdentifier.IdentificationEntry entry = ObjectIdentifier.identify(obj);
        assertNotNull(entry.identifier());
        assertEquals(ObjectIdentifier.IdentificationStrategy.CONTENT_FINGERPRINT, entry.strategy());
    }
}
