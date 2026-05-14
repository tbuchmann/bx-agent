package dev.bxagent.correspondence;

import com.google.common.collect.BiMap;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * Context object for running a generated transformation.
 * Supports both batch mode (no corrModel) and incremental mode (with corrModel).
 *
 * <pre>
 *   // Batch (equivalent to old API):
 *   TransformationContext ctx = TransformationContext.builder()
 *       .sourceModel(source).existingTarget(target).build();
 *
 *   // Incremental:
 *   TransformationContext ctx = TransformationContext.builder()
 *       .sourceModel(source).existingTarget(target)
 *       .corrModel(corr).build();
 * </pre>
 */
public class TransformationContext {

    public enum Direction {
        SOURCE_TO_TARGET, TARGET_TO_SOURCE
    }

    public enum DeletionPolicy {
        CASCADE, ORPHAN, TOMBSTONE
    }

    private final Resource sourceModel;
    private final Direction direction;
    private final Resource existingTarget;
    private final Resource corrModel;
    private final BiMap<EObject, EObject> corrIndex;
    private final DeletionPolicy deletionPolicy;

    private TransformationContext(Builder b) {
        this.sourceModel = b.sourceModel;
        this.direction = b.direction;
        this.existingTarget = b.existingTarget;
        this.corrModel = b.corrModel;
        this.corrIndex = b.corrIndex;
        this.deletionPolicy = b.deletionPolicy;
    }

    /** Returns true if a correspondence model is present (incremental mode). */
    public boolean isIncremental() {
        return corrModel != null;
    }

    public Resource getSourceModel()   { return sourceModel; }
    public Direction getDirection()    { return direction; }
    public Resource getExistingTarget(){ return existingTarget; }
    public Resource getCorrModel()     { return corrModel; }
    public BiMap<EObject, EObject> getCorrIndex() { return corrIndex; }
    public DeletionPolicy getDeletionPolicy() { return deletionPolicy; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Resource sourceModel;
        private Direction direction = Direction.SOURCE_TO_TARGET;
        private Resource existingTarget;
        private Resource corrModel;
        private BiMap<EObject, EObject> corrIndex;
        private DeletionPolicy deletionPolicy = DeletionPolicy.CASCADE;

        public Builder sourceModel(Resource r)    { this.sourceModel = r; return this; }
        public Builder direction(Direction d)     { this.direction = d; return this; }
        public Builder existingTarget(Resource r) { this.existingTarget = r; return this; }
        public Builder corrModel(Resource r)      { this.corrModel = r; return this; }
        public Builder corrIndex(BiMap<EObject, EObject> idx) { this.corrIndex = idx; return this; }
        public Builder deletionPolicy(DeletionPolicy p) { this.deletionPolicy = p; return this; }

        public TransformationContext build() {
            if (sourceModel == null) {
                throw new IllegalStateException("sourceModel is required");
            }
            if (corrModel != null) {
                if (existingTarget == null) {
                    throw new IllegalStateException("existingTarget is required in incremental mode");
                }
                if (corrIndex == null) {
                    corrIndex = CorrespondenceModel.buildIndex(corrModel);
                }
            }
            return new TransformationContext(this);
        }
    }
}
