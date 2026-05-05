package dev.bxagent.cli;

import dev.bxagent.correspondence.SyncConflict;
import dev.bxagent.correspondence.SyncConflictPolicy;
import dev.bxagent.correspondence.SyncResult;
import dev.bxagent.correspondence.TransformationContext.DeletionPolicy;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.Callable;

/**
 * CLI sub-command for synchronising two independently modified models.
 *
 * <pre>
 *   java -jar bx-agent.jar sync \
 *     --src families.xmi \
 *     --tgt persons.xmi \
 *     --transformation-class dev.bxagent.generated.Families2PersonsTransformation \
 *     --conflict-policy SOURCE_WINS \
 *     --deletion-policy TOMBSTONE
 * </pre>
 *
 * The generated transformation class must be on the runtime classpath.
 * The correspondence model is derived automatically from the source and
 * target file names (&lt;src&gt;_&lt;tgt&gt;.corr.xmi in the same directory).
 */
@Command(
    name = "sync",
    mixinStandardHelpOptions = true,
    description = "Synchronise two independently modified models using a generated transformation"
)
public class SyncCommand implements Callable<Integer> {

    @Option(names = {"--src"}, required = true, description = "Path to the source model (.xmi)")
    Path srcPath;

    @Option(names = {"--tgt"}, required = true, description = "Path to the target model (.xmi)")
    Path tgtPath;

    @Option(
        names = {"--corr"},
        description = "Path to the correspondence model (.corr.xmi); derived from --src/--tgt if omitted"
    )
    Path corrPath;

    @Option(
        names = {"--transformation-class"},
        required = true,
        description = "Fully qualified name of the generated transformation class"
    )
    String transformationClass;

    @Option(
        names = {"--conflict-policy"},
        description = "Conflict resolution policy: SOURCE_WINS (default), TARGET_WINS, LOG_AND_SKIP",
        defaultValue = "SOURCE_WINS"
    )
    SyncConflictPolicy conflictPolicy;

    @Option(
        names = {"--deletion-policy"},
        description = "Deletion policy: CASCADE (default), ORPHAN, TOMBSTONE",
        defaultValue = "CASCADE"
    )
    DeletionPolicy deletionPolicy;

    @Override
    public Integer call() {
        // Validate files
        if (!Files.exists(srcPath)) {
            System.err.println("ERROR: Source model not found: " + srcPath);
            return 1;
        }
        if (!Files.exists(tgtPath)) {
            System.err.println("ERROR: Target model not found: " + tgtPath);
            return 1;
        }

        // Derive corr path if not given
        if (corrPath == null) {
            String srcName = srcPath.getFileName().toString().replaceAll("\\.xmi$", "");
            String tgtName = tgtPath.getFileName().toString().replaceAll("\\.xmi$", "");
            corrPath = srcPath.resolveSibling(srcName + "_" + tgtName + ".corr.xmi");
        }
        if (!Files.exists(corrPath)) {
            System.err.println("ERROR: Correspondence model not found: " + corrPath);
            System.err.println("       Run a batch or incremental transform first to create it.");
            return 1;
        }

        System.out.println("Synchronising " + srcPath.getFileName() + " ↔ " + tgtPath.getFileName());
        System.out.println("  Conflict policy : " + conflictPolicy);
        System.out.println("  Deletion policy : " + deletionPolicy);
        System.out.println("  Corr model      : " + corrPath);

        try {
            // Load EMF models
            ResourceSet rs = new ResourceSetImpl();
            rs.getResourceFactoryRegistry().getExtensionToFactoryMap()
                    .put("xmi", new XMIResourceFactoryImpl());
            // Force correspondence package registration
            Class.forName("dev.bxagent.correspondence.CorrespondenceModel");

            Resource source = loadResource(rs, srcPath);
            Resource target = loadResource(rs, tgtPath);
            Resource corr   = loadResource(rs, corrPath);

            // Resolve transformation class and call sync()
            Class<?> txClass = Class.forName(transformationClass);

            // Try full overload first; fall back to 3-arg convenience overload.
            SyncResult result;
            Method fullSync = findMethod(txClass, "sync",
                    Resource.class, Resource.class, Resource.class,
                    SyncConflictPolicy.class, DeletionPolicy.class);
            if (fullSync != null) {
                result = (SyncResult) fullSync.invoke(null,
                        source, target, corr, conflictPolicy, deletionPolicy);
            } else {
                Method shortSync = txClass.getMethod("sync",
                        Resource.class, Resource.class, Resource.class);
                result = (SyncResult) shortSync.invoke(null, source, target, corr);
            }

            // Print summary
            System.out.println();
            System.out.println("Sync complete:");
            System.out.println("  Updated forward  : " + result.objectsUpdatedForward());
            System.out.println("  Updated backward : " + result.objectsUpdatedBackward());
            System.out.println("  Created forward  : " + result.objectsCreatedForward());
            System.out.println("  Created backward : " + result.objectsCreatedBackward());
            System.out.println("  Deleted          : " + result.objectsDeleted());
            System.out.println("  Linked (fuzzy)   : " + result.objectsLinked());

            if (!result.conflicts().isEmpty()) {
                System.out.println();
                System.out.println("Conflicts (" + result.conflicts().size() + "):");
                for (SyncConflict c : result.conflicts()) {
                    System.out.printf("  [CONFLICT] %s \"%s\"%n             ↔ %s \"%s\"%n             → unresolved (LOG_AND_SKIP)%n",
                            c.sourceType(), c.currentSourceFingerprint(),
                            c.targetType(), c.currentTargetFingerprint());
                }
            }

            return 0;

        } catch (ClassNotFoundException e) {
            System.err.println("ERROR: Transformation class not found: " + transformationClass);
            System.err.println("       Add the generated transformation JAR to your classpath.");
            return 1;
        } catch (NoSuchMethodException e) {
            System.err.println("ERROR: sync() method not found in " + transformationClass);
            System.err.println("       Regenerate the transformation class with a version that supports sync().");
            return 1;
        } catch (Exception e) {
            System.err.println("ERROR: Sync failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Resource loadResource(ResourceSet rs, Path path) throws Exception {
        URI uri = URI.createFileURI(path.toAbsolutePath().toString());
        Resource r = rs.createResource(uri);
        r.load(Collections.emptyMap());
        return r;
    }
}
