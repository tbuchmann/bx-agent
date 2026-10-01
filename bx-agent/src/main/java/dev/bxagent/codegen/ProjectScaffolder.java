package dev.bxagent.codegen;

import dev.bxagent.mapping.MappingModel;
import dev.bxagent.service.BXAgentService;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates a standalone Maven/Eclipse project directory around a generated transformation.
 */
public class ProjectScaffolder {

    private final Configuration ftl;

    public ProjectScaffolder() {
        ftl = new Configuration(Configuration.VERSION_2_3_32);
        ftl.setClassForTemplateLoading(this.getClass(), "/templates/project");
        ftl.setDefaultEncoding("UTF-8");
    }

    public void scaffold(BXAgentService.Session session, ProjectSpec spec) throws IOException {
        MappingModel.TransformationSpec tspec = session.spec();
        String className = session.generatedTransformation().fileName().replace(".java", "");

        // Verify the immediate parent exists and is writable before attempting to create anything
        Path parent = spec.projectDir().getParent();
        if (parent != null && !Files.exists(parent)) {
            throw new IOException(
                "Parent directory does not exist: " + parent
                + "\nCreate it first or choose a different --project-dir");
        }
        if (parent != null && !Files.isWritable(parent)) {
            throw new IOException(
                "No write permission on: " + parent);
        }

        String pkgPath = spec.basePackage().replace('.', '/');
        Path srcDir = spec.projectDir().resolve("src").resolve(pkgPath);
        Files.createDirectories(srcDir);

        // Write transformation class into src/<package>/
        Files.writeString(srcDir.resolve(session.generatedTransformation().fileName()),
            session.generatedTransformation().content());

        Map<String, Object> model = buildProjectModel(session, spec, className, null);

        renderTemplate("pom.xml.ftl",       model, spec.projectDir().resolve("pom.xml"));
        renderTemplate("dotproject.ftl",    model, spec.projectDir().resolve(".project"));
        renderTemplate("dotclasspath.ftl",  model, spec.projectDir().resolve(".classpath"));

        if (spec.benchmarxPath() != null) {
            ProjectSpec.MavenDep bxDep = readPomCoordinates(spec.benchmarxPath());

            // Auto-derive metamodel deps from BenchmarX project if not explicitly specified.
            // Metamodels follow the convention: groupId=<bx.groupId>, artifactId=<CapPackageName>, version=<bx.version>
            if (model.get("sourceMetamodelDep") == null) {
                model.put("sourceMetamodelDep", new ProjectSpec.MavenDep(
                    bxDep.groupId(), capitalize(tspec.sourcePackageName()), bxDep.version()));
            }
            if (model.get("targetMetamodelDep") == null) {
                model.put("targetMetamodelDep", new ProjectSpec.MavenDep(
                    bxDep.groupId(), capitalize(tspec.targetPackageName()), bxDep.version()));
            }

            String decisionsPackage = findDecisionsPackage(spec.benchmarxPath(), tspec);
            Map<String, Object> adapterModel = buildProjectModel(session, spec, className, bxDep);
            adapterModel.put("benchmarxDep", bxDep);
            adapterModel.put("decisionsPackage", decisionsPackage);
            adapterModel.put("sourceComparatorClass", capitalize(tspec.sourcePackageName()) + "Comparator");
            adapterModel.put("targetComparatorClass", capitalize(tspec.targetPackageName()) + "Comparator");
            adapterModel.put("sourceComparatorPackage",
                bxDep.groupId() + "." + tspec.sourcePackageName().toLowerCase() + ".core");
            adapterModel.put("targetComparatorPackage",
                bxDep.groupId() + "." + tspec.targetPackageName().toLowerCase() + ".core");

            String adapterPkgPath = spec.adapterPackage().replace('.', '/');
            Path adapterDir = spec.projectDir().resolve("src-test").resolve(adapterPkgPath);
            Files.createDirectories(adapterDir);

            String adapterName = "BXAgent" + className.replace("Transformation", "");
            adapterModel.put("adapterClassName", adapterName);
            renderTemplate("BXAgentAdapter.java.ftl", adapterModel, adapterDir.resolve(adapterName + ".java"));
        }
    }

    private Map<String, Object> buildProjectModel(BXAgentService.Session session, ProjectSpec spec,
                                                   String className, ProjectSpec.MavenDep benchmarxDep) {
        MappingModel.TransformationSpec tspec = session.spec();
        Map<String, Object> model = new HashMap<>();

        model.put("projectName",       spec.projectName());
        model.put("groupId",           spec.groupId());
        model.put("artifactId",        spec.artifactId());
        model.put("basePackage",       spec.basePackage());
        model.put("adapterPackage",    spec.adapterPackage());
        model.put("generatedClassName", className);

        model.put("sourcePackageName", tspec.sourcePackageName());
        model.put("targetPackageName", tspec.targetPackageName());
        model.put("sourceFactory",     capitalize(tspec.sourcePackageName()) + "Factory");
        model.put("targetFactory",     capitalize(tspec.targetPackageName()) + "Factory");
        model.put("backwardConfigs",   tspec.backwardConfigs());
        model.put("hasOptions",        !tspec.backwardConfigs().isEmpty() || !tspec.transformationOptions().isEmpty());

        model.put("sourceMetamodelDep", spec.sourceMetamodelDep());
        model.put("targetMetamodelDep", spec.targetMetamodelDep());
        model.put("benchmarxDep",       benchmarxDep);

        // Root types: first TypeMapping not covered by role-based mappings
        Set<String> rbmSourceTypes = tspec.roleBasedTypeMappings().stream()
            .map(MappingModel.RoleBasedTypeMapping::sourceType)
            .collect(Collectors.toSet());
        List<MappingModel.TypeMapping> roots = tspec.typeMappings().stream()
            .filter(tm -> !rbmSourceTypes.contains(tm.sourceType()))
            .toList();
        if (!roots.isEmpty()) {
            model.put("rootSourceType", roots.get(0).sourceType());
            model.put("rootTargetType", roots.get(0).targetType());
        } else if (!tspec.typeMappings().isEmpty()) {
            model.put("rootSourceType", tspec.typeMappings().get(0).sourceType());
            model.put("rootTargetType", tspec.typeMappings().get(0).targetType());
        }

        return model;
    }

    private void renderTemplate(String templateName, Map<String, Object> model, Path out) throws IOException {
        try {
            Template template = ftl.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(model, writer);
            Files.writeString(out, writer.toString());
        } catch (TemplateException e) {
            throw new IOException("Template rendering failed for " + templateName + ": " + e.getMessage(), e);
        }
    }

    private ProjectSpec.MavenDep readPomCoordinates(Path projectPath) throws IOException {
        Path pomPath = projectPath.resolve("pom.xml");
        if (!Files.exists(pomPath)) throw new IOException("No pom.xml found at: " + pomPath);
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(pomPath.toFile());
            doc.getDocumentElement().normalize();
            return new ProjectSpec.MavenDep(
                firstText(doc, "groupId"),
                firstText(doc, "artifactId"),
                firstText(doc, "version")
            );
        } catch (Exception e) {
            throw new IOException("Failed to parse pom.xml at " + pomPath + ": " + e.getMessage(), e);
        }
    }

    private String firstText(Document doc, String tag) {
        NodeList nl = doc.getElementsByTagName(tag);
        return nl.getLength() > 0 ? nl.item(0).getTextContent().trim() : "unknown";
    }

    private String findDecisionsPackage(Path benchmarxPath, MappingModel.TransformationSpec tspec) {
        String srcLower = tspec.sourcePackageName().toLowerCase();
        String tgtLower = tspec.targetPackageName().toLowerCase();
        try (Stream<Path> walk = Files.walk(benchmarxPath)) {
            Optional<Path> found = walk
                .filter(p -> p.getFileName().toString().equals("Decisions.java"))
                .filter(p -> {
                    String abs = p.toString().replace('\\', '/');
                    return abs.contains(srcLower) || abs.contains(tgtLower);
                })
                .findFirst();
            if (found.isPresent()) {
                try (Stream<String> lines = Files.lines(found.get())) {
                    return lines
                        .filter(l -> l.trim().startsWith("package "))
                        .map(l -> l.trim().replace("package ", "").replace(";", "").trim())
                        .findFirst()
                        .orElse("org.benchmarx.examples.testsuite");
                }
            }
        } catch (IOException e) {
            // fall through to default
        }
        return "org.benchmarx.examples." + srcLower + "to" + tgtLower + ".testsuite";
    }

    private String capitalize(String s) {
        return s == null || s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
