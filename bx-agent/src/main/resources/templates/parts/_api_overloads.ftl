    public static void transform(Resource sourceResource, Resource targetResource) {
        transform(sourceResource, targetResource, Options.defaults(), PostProcessor.NOOP);
    }

    /** Batch transform with post-processing hook. */
    public static void transform(Resource sourceResource, Resource targetResource, PostProcessor postProcessor) {
        transform(sourceResource, targetResource, Options.defaults(), postProcessor);
    }

    /**
     * Transforms the contents of sourceResource into targetResource.
     * Both resources must already exist and root elements must be pre-created.
     * After a successful batch transformation a correspondence model is created automatically.
     */
    public static void transform(Resource sourceResource, Resource targetResource, Options options) {
        transform(sourceResource, targetResource, options, PostProcessor.NOOP);
    }

    /** Batch transform with options and post-processing hook. */
    public static void transform(Resource sourceResource, Resource targetResource, Options options, PostProcessor postProcessor) {
        if (sourceResource == null || targetResource == null) {
            return;
        }
        TransformationContext ctx = TransformationContext.builder()
                .sourceModel(sourceResource)
                .existingTarget(targetResource)
                .direction(TransformationContext.Direction.SOURCE_TO_TARGET)
                .build();
        transform(ctx, options, postProcessor);
    }

    /**
     * Incrementally transforms sourceResource into targetResource using the supplied correspondence model.
     * The correspondence model must have been created by a previous batch transformation.
     */
    public static void transform(Resource sourceResource, Resource targetResource, Resource corrResource) {
        transform(sourceResource, targetResource, corrResource, Options.defaults(), PostProcessor.NOOP);
    }

    /** Incremental transform with post-processing hook. */
    public static void transform(Resource sourceResource, Resource targetResource, Resource corrResource, PostProcessor postProcessor) {
        transform(sourceResource, targetResource, corrResource, Options.defaults(), postProcessor);
    }

    /**
     * Incrementally transforms sourceResource into targetResource using the supplied correspondence model.
     * The correspondence model must have been created by a previous batch transformation.
     */
    public static void transform(Resource sourceResource, Resource targetResource, Resource corrResource, Options options) {
        transform(sourceResource, targetResource, corrResource, options, PostProcessor.NOOP);
    }

    /** Incremental transform with options and post-processing hook. */
    public static void transform(Resource sourceResource, Resource targetResource, Resource corrResource, Options options, PostProcessor postProcessor) {
        transform(sourceResource, targetResource, corrResource, TransformationContext.DeletionPolicy.TOMBSTONE, options, postProcessor);
    }

    /** Incremental transform with explicit deletion policy. */
    public static void transform(Resource sourceResource, Resource targetResource, Resource corrResource, TransformationContext.DeletionPolicy deletionPolicy) {
        transform(sourceResource, targetResource, corrResource, deletionPolicy, Options.defaults(), PostProcessor.NOOP);
    }

    /** Incremental transform with explicit deletion policy and post-processing hook. */
    public static void transform(Resource sourceResource, Resource targetResource, Resource corrResource, TransformationContext.DeletionPolicy deletionPolicy, PostProcessor postProcessor) {
        transform(sourceResource, targetResource, corrResource, deletionPolicy, Options.defaults(), postProcessor);
    }

    /** Incremental transform with explicit deletion policy and options. */
    public static void transform(Resource sourceResource, Resource targetResource, Resource corrResource, TransformationContext.DeletionPolicy deletionPolicy, Options options) {
        transform(sourceResource, targetResource, corrResource, deletionPolicy, options, PostProcessor.NOOP);
    }

    /** Incremental transform with explicit deletion policy, options and post-processing hook. */
    public static void transform(Resource sourceResource, Resource targetResource, Resource corrResource, TransformationContext.DeletionPolicy deletionPolicy, Options options, PostProcessor postProcessor) {
        if (sourceResource == null || targetResource == null || corrResource == null) {
            return;
        }
        TransformationContext ctx = TransformationContext.builder()
                .sourceModel(sourceResource)
                .existingTarget(targetResource)
                .corrModel(corrResource)
                .direction(TransformationContext.Direction.SOURCE_TO_TARGET)
                .deletionPolicy(deletionPolicy)
                .build();
        transform(ctx, options, postProcessor);
    }

    /** Incremental-aware transform using default options. */
    public static void transform(TransformationContext ctx) {
        transform(ctx, Options.defaults(), PostProcessor.NOOP);
    }

    /** Incremental-aware transform. Dispatches to batch or incremental path based on ctx. */
    public static void transform(TransformationContext ctx, Options options) {
        transform(ctx, options, PostProcessor.NOOP);
    }

    /** Incremental-aware transform with post-processing hook. */
    public static void transform(TransformationContext ctx, PostProcessor postProcessor) {
        transform(ctx, Options.defaults(), postProcessor);
    }

    /** Incremental-aware transform with options and post-processing hook. */
    public static void transform(TransformationContext ctx, Options options, PostProcessor postProcessor) {
        if (ctx.isIncremental()) {
            transformIncremental(ctx, options, postProcessor);
        } else {
            transformBatch(ctx, options, postProcessor);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Public Entry Points — Backward
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Transforms the contents of targetResource back into sourceResource using default options.
     */
    public static void transformBack(Resource targetResource, Resource sourceResource) {
        transformBack(targetResource, sourceResource, Options.defaults(), PostProcessor.NOOP);
    }

    /** Batch backward transform with post-processing hook. */
    public static void transformBack(Resource targetResource, Resource sourceResource, PostProcessor postProcessor) {
        transformBack(targetResource, sourceResource, Options.defaults(), postProcessor);
    }

    /**
     * Transforms the contents of targetResource back into sourceResource.
     */
    public static void transformBack(Resource targetResource, Resource sourceResource, Options options) {
        transformBack(targetResource, sourceResource, options, PostProcessor.NOOP);
    }

    /** Batch backward transform with options and post-processing hook. */
    public static void transformBack(Resource targetResource, Resource sourceResource, Options options, PostProcessor postProcessor) {
        if (targetResource == null || sourceResource == null) {
            return;
        }
        TransformationContext ctx = TransformationContext.builder()
                .sourceModel(targetResource)
                .existingTarget(sourceResource)
                .direction(TransformationContext.Direction.TARGET_TO_SOURCE)
                .build();
        transformBack(ctx, options, postProcessor);
    }

    /**
     * Incrementally transforms targetResource back into sourceResource using the supplied correspondence model.
     * The correspondence model must have been created by a previous batch transformation.
     */
    public static void transformBack(Resource targetResource, Resource sourceResource, Resource corrResource) {
        transformBack(targetResource, sourceResource, corrResource, Options.defaults(), PostProcessor.NOOP);
    }

    /** Incremental backward transform with post-processing hook. */
    public static void transformBack(Resource targetResource, Resource sourceResource, Resource corrResource, PostProcessor postProcessor) {
        transformBack(targetResource, sourceResource, corrResource, Options.defaults(), postProcessor);
    }

    /**
     * Incrementally transforms targetResource back into sourceResource using the supplied correspondence model.
     * The correspondence model must have been created by a previous batch transformation.
     */
    public static void transformBack(Resource targetResource, Resource sourceResource, Resource corrResource, Options options) {
        transformBack(targetResource, sourceResource, corrResource, options, PostProcessor.NOOP);
    }

    /** Incremental backward transform with options and post-processing hook. */
    public static void transformBack(Resource targetResource, Resource sourceResource, Resource corrResource, Options options, PostProcessor postProcessor) {
        transformBack(targetResource, sourceResource, corrResource, TransformationContext.DeletionPolicy.TOMBSTONE, options, postProcessor);
    }

    /** Incremental backward transform with explicit deletion policy. */
    public static void transformBack(Resource targetResource, Resource sourceResource, Resource corrResource, TransformationContext.DeletionPolicy deletionPolicy) {
        transformBack(targetResource, sourceResource, corrResource, deletionPolicy, Options.defaults(), PostProcessor.NOOP);
    }

    /** Incremental backward transform with explicit deletion policy and post-processing hook. */
    public static void transformBack(Resource targetResource, Resource sourceResource, Resource corrResource, TransformationContext.DeletionPolicy deletionPolicy, PostProcessor postProcessor) {
        transformBack(targetResource, sourceResource, corrResource, deletionPolicy, Options.defaults(), postProcessor);
    }

    /** Incremental backward transform with explicit deletion policy and options. */
    public static void transformBack(Resource targetResource, Resource sourceResource, Resource corrResource, TransformationContext.DeletionPolicy deletionPolicy, Options options) {
        transformBack(targetResource, sourceResource, corrResource, deletionPolicy, options, PostProcessor.NOOP);
    }

    /** Incremental backward transform with explicit deletion policy, options and post-processing hook. */
    public static void transformBack(Resource targetResource, Resource sourceResource, Resource corrResource, TransformationContext.DeletionPolicy deletionPolicy, Options options, PostProcessor postProcessor) {
        if (targetResource == null || sourceResource == null || corrResource == null) {
            return;
        }
        TransformationContext ctx = TransformationContext.builder()
                .sourceModel(targetResource)
                .existingTarget(sourceResource)
                .corrModel(corrResource)
                .direction(TransformationContext.Direction.TARGET_TO_SOURCE)
                .deletionPolicy(deletionPolicy)
                .build();
        transformBack(ctx, options, postProcessor);
    }

    /** Incremental-aware backward transform using default options. */
    public static void transformBack(TransformationContext ctx) {
        transformBack(ctx, Options.defaults(), PostProcessor.NOOP);
    }

    /** Incremental-aware backward transform. */
    public static void transformBack(TransformationContext ctx, Options options) {
        transformBack(ctx, options, PostProcessor.NOOP);
    }

    /** Incremental-aware backward transform with post-processing hook. */
    public static void transformBack(TransformationContext ctx, PostProcessor postProcessor) {
        transformBack(ctx, Options.defaults(), postProcessor);
    }

    /** Incremental-aware backward transform with options and post-processing hook. */
    public static void transformBack(TransformationContext ctx, Options options, PostProcessor postProcessor) {
        if (ctx.isIncremental()) {
            transformIncrementalBack(ctx, options, postProcessor);
        } else {
            transformBatchBack(ctx, options, postProcessor);
        }
    }
