package dev.bxagent.codegen;

import java.nio.file.Path;

/**
 * Configuration for scaffolding a standalone Maven/Eclipse project around a generated transformation.
 */
public record ProjectSpec(
    Path projectDir,
    String projectName,
    String groupId,
    String artifactId,
    String basePackage,
    MavenDep sourceMetamodelDep,
    MavenDep targetMetamodelDep,
    Path benchmarxPath,
    String adapterPackage
) {
    public record MavenDep(String groupId, String artifactId, String version) {
        public static MavenDep parse(String gav) {
            String[] parts = gav.split(":", 3);
            if (parts.length != 3) {
                throw new IllegalArgumentException(
                    "Invalid Maven dependency format '" + gav + "' — expected groupId:artifactId:version");
            }
            return new MavenDep(parts[0], parts[1], parts[2]);
        }
    }
}
