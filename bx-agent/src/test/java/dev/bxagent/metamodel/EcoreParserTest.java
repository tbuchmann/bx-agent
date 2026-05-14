package dev.bxagent.metamodel;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EcoreParser functionality.
 */
class EcoreParserTest {

    @Test
    void testParsePdbExample() {
        Path ecorePath = Paths.get("examples/pdb/PersonsDB1.ecore");

        if (!ecorePath.toFile().exists()) {
            System.out.println("Skipping test - example file not found: " + ecorePath);
            return;
        }

        MetamodelSummary.Summary summary = EcoreParser.parse(ecorePath);

        assertNotNull(summary);
        assertEquals("pdb1", summary.packageName());
        assertFalse(summary.classes().isEmpty());

        System.out.println("=== PersonsDB1 Metamodel Summary ===");
        System.out.println(summary.toPromptString());
    }

    @Test
    void testParseFamiliesExample() {
        Path ecorePath = Paths.get("examples/f2p/Families.ecore");

        if (!ecorePath.toFile().exists()) {
            System.out.println("Skipping test - example file not found: " + ecorePath);
            return;
        }

        MetamodelSummary.Summary summary = EcoreParser.parse(ecorePath);

        assertNotNull(summary);
        assertEquals("Families", summary.packageName());
        assertTrue(summary.classes().size() >= 2); // FamilyRegister, Family, FamilyMember

        System.out.println("=== Families Metamodel Summary ===");
        System.out.println(summary.toPromptString());
    }

    @Test
    void testParseSetsExample() {
        Path ecorePath = Paths.get("examples/sets/Sets.ecore");

        if (!ecorePath.toFile().exists()) {
            System.out.println("Skipping test - example file not found: " + ecorePath);
            return;
        }

        MetamodelSummary.Summary summary = EcoreParser.parse(ecorePath);

        assertNotNull(summary);
        assertEquals("sets", summary.packageName());

        System.out.println("=== Sets Metamodel Summary ===");
        System.out.println(summary.toPromptString());
    }
}
