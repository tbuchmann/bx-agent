package dev.bxagent.cli;

import dev.bxagent.codegen.GeneratedFile;
import dev.bxagent.mapping.MappingModel;
import dev.bxagent.metamodel.MetamodelSummary;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Mutable session state for the interactive REPL. */
class SessionState {

    // Configuration
    Path configPath = Paths.get("config/agent.properties");
    String backendOverride;  // null = use config file value
    String modelOverride;    // null = use config file value

    // Metamodels
    Path sourceEcore;
    Path targetEcore;
    MetamodelSummary.Summary sourceSummary;  // set when /source is loaded
    MetamodelSummary.Summary targetSummary;  // set when /target is loaded

    // Transformation spec
    String description;
    List<String> excludedAttributes = new ArrayList<>();
    MappingModel.TransformationSpec spec;
    String rawMappingJson;

    // Generated artifacts
    GeneratedFile generatedTransformation;
    GeneratedFile generatedTest;

    // Output
    Path outputDir = Paths.get("generated");

    // Options
    boolean debugLog   = false;
    boolean interactive = true;
    boolean validate   = false;
}
