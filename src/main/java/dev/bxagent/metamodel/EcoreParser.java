package dev.bxagent.metamodel;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Parses .ecore files in standalone mode (no Eclipse required).
 * Extracts a MetamodelSummary suitable for LLM consumption.
 */
public class EcoreParser {

    static {
        // Register XMI resource factory for .ecore files
        Resource.Factory.Registry.INSTANCE
            .getExtensionToFactoryMap()
            .put("ecore", new XMIResourceFactoryImpl());

        // Initialize Ecore package
        EcorePackage.eINSTANCE.eClass();
    }

    /**
     * Parses an .ecore file and returns a summary.
     *
     * @param ecorePath Path to the .ecore file
     * @return MetamodelSummary
     * @throws RuntimeException if parsing fails
     */
    public static MetamodelSummary.Summary parse(Path ecorePath) {
        try {
            // Create resource set and load the .ecore file
            ResourceSet resourceSet = new ResourceSetImpl();
            URI fileURI = URI.createFileURI(ecorePath.toAbsolutePath().toString());
            Resource resource = resourceSet.getResource(fileURI, true);

            if (resource == null || resource.getContents().isEmpty()) {
                throw new RuntimeException("Failed to load .ecore file: " + ecorePath);
            }

            // Get the root EPackage
            EPackage ePackage = (EPackage) resource.getContents().get(0);

            // Extract package metadata
            String packageName = ePackage.getName();
            String nsURI = ePackage.getNsURI();
            String nsPrefix = ePackage.getNsPrefix();

            // Extract all EClasses
            List<MetamodelSummary.EClassSummary> classes = new ArrayList<>();
            for (EClassifier classifier : ePackage.getEClassifiers()) {
                if (classifier instanceof EClass) {
                    classes.add(extractEClass((EClass) classifier));
                }
            }

            return new MetamodelSummary.Summary(packageName, nsURI, nsPrefix, classes);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse .ecore file: " + ecorePath, e);
        }
    }

    /**
     * Extracts summary information from an EClass.
     */
    private static MetamodelSummary.EClassSummary extractEClass(EClass eClass) {
        String name = eClass.getName();
        boolean isAbstract = eClass.isAbstract();

        // Extract supertypes
        List<String> superTypes = eClass.getESuperTypes().stream()
            .map(EClass::getName)
            .collect(Collectors.toList());

        // Extract attributes
        List<MetamodelSummary.AttributeSummary> attributes = new ArrayList<>();
        for (EAttribute attr : eClass.getEAttributes()) {
            attributes.add(extractAttribute(attr));
        }

        // Extract references
        List<MetamodelSummary.ReferenceSummary> references = new ArrayList<>();
        for (EReference ref : eClass.getEReferences()) {
            references.add(extractReference(ref));
        }

        return new MetamodelSummary.EClassSummary(
            name, isAbstract, superTypes, attributes, references
        );
    }

    /**
     * Extracts summary information from an EAttribute.
     */
    private static MetamodelSummary.AttributeSummary extractAttribute(EAttribute attr) {
        String name = attr.getName();
        String type = attr.getEType() != null ? attr.getEType().getName() : "Unknown";
        boolean required = attr.getLowerBound() >= 1;
        boolean many = attr.getUpperBound() > 1 || attr.getUpperBound() == -1;

        return new MetamodelSummary.AttributeSummary(name, type, required, many);
    }

    /**
     * Extracts summary information from an EReference.
     */
    private static MetamodelSummary.ReferenceSummary extractReference(EReference ref) {
        String name = ref.getName();
        String targetType = ref.getEReferenceType() != null
            ? ref.getEReferenceType().getName()
            : "Unknown";
        boolean containment = ref.isContainment();
        boolean many = ref.getUpperBound() > 1 || ref.getUpperBound() == -1;
        boolean required = ref.getLowerBound() >= 1;

        return new MetamodelSummary.ReferenceSummary(
            name, targetType, containment, many, required
        );
    }
}
