
    // ════════════════════════════════════════════════════════════════════════
    // Transformation Options
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Configuration options for non-deterministic transformation steps.
     * Includes both attribute-level options and structural backward-direction parameters.
     * Pass to transform() or transformBack() to control ambiguous mappings.
     */
    public record Options(
<#list transformationOptions as opt>
        ${opt.type()} ${opt.name()}<#if opt?has_next || backwardConfigs?has_content>,</#if>  // ${opt.description()!""}
</#list>
<#list backwardConfigs as cfg>
        ${cfg.parameterType()} ${cfg.parameterName()}<#if cfg?has_next>,</#if>  // ${cfg.description()!""}
</#list>
    ) {
        /** Default options. */
        public static Options defaults() {
            return new Options(
<#list transformationOptions as opt>
    <#if opt.type() == "boolean">
                ${opt.defaultValue()}<#if opt?has_next || backwardConfigs?has_content>,</#if>
    <#else>
                "${opt.defaultValue()}"<#if opt?has_next || backwardConfigs?has_content>,</#if>
    </#if>
</#list>
<#list backwardConfigs as cfg>
    <#if cfg.parameterType() == "boolean">
                ${cfg.defaultValue()}<#if cfg?has_next>,</#if>
    <#else>
                "${cfg.defaultValue()}"<#if cfg?has_next>,</#if>
    </#if>
</#list>
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Post-Processor Hook
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Optional post-processing hook for derived structure that cannot be expressed
     * as attribute or reference mappings (e.g. building a linked list from containment order).
     * Pass to any transform() or transformBack() overload.
     */
    public interface PostProcessor {
        /**
         * Called before target objects are deleted (incremental mode only, CASCADE policy).
         * Use to repair derived relationships (e.g. re-link neighbors in a linked list)
         * while the objects are still reachable in the model.
         */
        default void beforeDeletions(List<EObject> targetObjectsToDelete) {}

        /**
         * Called after all phases of the transformation complete (batch and incremental).
         *
         * @param readFrom  the resource that was read (source model in forward, target model in backward)
         * @param writeTo   the resource that was written (target model in forward, source model in backward)
         * @param objectMap correspondence map: readFrom object → writeTo object
         * @param created   newly created writeTo objects (in batch mode: all non-root objects)
         * @param updated   attribute-updated writeTo objects (empty in batch mode)
         */
        default void afterTransform(Resource readFrom, Resource writeTo,
                                     Map<EObject, EObject> objectMap,
                                     List<EObject> created,
                                     List<EObject> updated) {}

        /** No-op implementation used by all overloads that do not supply a PostProcessor. */
        PostProcessor NOOP = new PostProcessor() {};
    }

    // ════════════════════════════════════════════════════════════════════════
    // Annotation Helpers (generated only when target metamodel supports annotations)
    // ════════════════════════════════════════════════════════════════════════
<#if annotationContainerRef?? && annotationEClass?? && annotationTextAttr??>

    /**
     * Attaches an annotation tag string to any target EObject that supports annotations.
     * Walks the EClass hierarchy to find the annotation container feature, then creates
     * an ${annotationEClass} object and adds it.
     */
    @SuppressWarnings("unchecked")
    private static void _addAnnotation(EObject target, String tag) {
        org.eclipse.emf.ecore.EStructuralFeature _af = null;
        org.eclipse.emf.ecore.EClass _c = target.eClass();
        while (_c != null && _af == null) {
            _af = _c.getEStructuralFeature("${annotationContainerRef}");
            _c = _c.getESuperTypes().isEmpty() ? null : _c.getESuperTypes().get(0);
        }
        if (_af == null) return;
        EObject _ann = ${targetFactory}.eINSTANCE.create(
            (org.eclipse.emf.ecore.EClass)
                ${targetFactory}.eINSTANCE.getEPackage().getEClassifier("${annotationEClass}"));
        if (_ann == null) return;
        _ann.eSet(_ann.eClass().getEStructuralFeature("${annotationTextAttr}"), tag);
        ((java.util.List<EObject>) target.eGet(_af)).add(_ann);
    }

    /**
     * Returns true if target has an annotation with the given tag string.
     * Used in backward direction to identify ConditionalTypeMapping branches.
     */
    private static boolean _hasAnnotation(EObject target, String tag) {
        org.eclipse.emf.ecore.EStructuralFeature _af = null;
        org.eclipse.emf.ecore.EClass _c = target.eClass();
        while (_c != null && _af == null) {
            _af = _c.getEStructuralFeature("${annotationContainerRef}");
            _c = _c.getESuperTypes().isEmpty() ? null : _c.getESuperTypes().get(0);
        }
        if (_af == null) return false;
        Object _anns = target.eGet(_af);
        if (!(_anns instanceof java.util.List<?> _annList)) return false;
        for (Object _a : _annList) {
            if (_a instanceof EObject _aObj) {
                org.eclipse.emf.ecore.EStructuralFeature _tf =
                    _aObj.eClass().getEStructuralFeature("${annotationTextAttr}");
                if (_tf != null && tag.equals(_aObj.eGet(_tf))) return true;
            }
        }
        return false;
    }
</#if>

    // ════════════════════════════════════════════════════════════════════════
    // Public Entry Points — Forward
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Transforms the contents of sourceResource into targetResource using default options.
     * Both resources must already exist and root elements must be pre-created.
     */
