    private static void transformBatchBack(TransformationContext ctx, Options options, PostProcessor postProcessor) {
        // In backward direction: ctx.getSourceModel() = target model (read from)
        //                        ctx.getExistingTarget() = source model (written to)
        Resource targetResource = ctx.getSourceModel();
        Resource sourceResource = ctx.getExistingTarget();

        Map<EObject, EObject> objectMap = new HashMap<>();
        Set<EObject> visited = new HashSet<>();

        // Pre-populate objectMap with existing root element pairs (by index)
        // and apply root-type backward attribute mappings
        List<EObject> targetRoots = targetResource.getContents();
        List<EObject> sourceRoots = sourceResource.getContents();
        for (int i = 0; i < Math.min(targetRoots.size(), sourceRoots.size()); i++) {
<#if rootSourceType??>
            ${targetPackageName}.${rootTargetType} target = (${targetPackageName}.${rootTargetType}) targetRoots.get(i);
            ${sourcePackageName}.${rootSourceType} source = (${sourcePackageName}.${rootSourceType}) sourceRoots.get(i);
    <#list attributeMappings as mapping>
        <#if mapping.targetOwnerType()?? && (mapping.targetOwnerType() == rootTargetType || !typeMappings?filter(tm -> tm.targetType() == mapping.targetOwnerType())?has_content)>
            <#if mapping.backwardExpression()?? && mapping.sourceAttr()?? && mapping.sourceAttr()?has_content>
            source.set${mapping.sourceAttr()?cap_first}(${mapping.backwardExpression()});
            <#elseif mapping.sourceAttr()?? && mapping.sourceAttr()?has_content>
            // ${mapping.targetAttr()} → ${mapping.sourceAttr()}: no backward mapping (skipped)
            </#if>
        </#if>
    </#list>
<#else>
            EObject target = targetRoots.get(i);
            EObject source = sourceRoots.get(i);
</#if>
            objectMap.put(target, source);
        }

        // Phase 1: Traverse containment hierarchy and create contained objects
        for (EObject targetRoot : targetRoots) {
            createAndMapObjectsBack(targetRoot, objectMap, visited, options);
        }
<#if roleBasedTypeMappingModels?has_content>

        // Phase 1b: Role-based type mappings backward
        for (int i = 0; i < Math.min(targetRoots.size(), sourceRoots.size()); i++) {
            mapRoleBasedTypesBack(targetRoots.get(i), sourceRoots.get(i), options);
        }
</#if>
<#if edgeMaterializationMappings?has_content>

        // Phase 1c: Edge materialization backward (edge objects → source references)
        materializeEdgesBack(targetResource, sourceResource, objectMap);
</#if>
<#if aggregationMappings?has_content>

        // Phase 1d: Aggregation backward (batch)
        Map<EObject, EObject> _aggBackObjectMap = new java.util.LinkedHashMap<>();
        for (EObject _tgtObj : allSourceObjects(targetResource)) {
    <#list aggregationMappings as agg>
            if (_tgtObj instanceof ${targetPackageName}.${agg.targetContainerType()} _tgtContainer) {
                EObject _srcObj = objectMap.get(_tgtObj);
                if (_srcObj instanceof ${sourcePackageName}.${agg.sourceContainerType()} _srcContainer) {
                    materializeAggregationBack${agg.targetType()}(_tgtContainer, _srcContainer, _aggBackObjectMap);
                }
            }
    </#list>
        }
</#if>
<#if structuralDeduplicationMappings?has_content>

        // Phase 1e: Structural deduplication backward (batch): clone DAG tree to AST
        java.util.Map<EObject, java.util.List<EObject>> _sdBackDedupMap = new java.util.LinkedHashMap<>();
        for (EObject _tgtObj : allSourceObjects(targetResource)) {
    <#list structuralDeduplicationMappings as sdm>
            if (_tgtObj instanceof ${targetPackageName}.${sdm.targetContainerType()} _tgtContainer) {
                EObject _srcObj = objectMap.get(_tgtObj);
                if (_srcObj instanceof ${sourcePackageName}.${sdm.sourceContainerType()} _srcContainer) {
                    _materializeStructuralDedupBack${sdm.abstractTargetType()}(_tgtContainer, _srcContainer, _sdBackDedupMap);
                }
            }
    </#list>
        }
</#if>

<#if conditionalTypeMappings?has_content>

        // Phase 1f backward: ConditionalTypeMappings — reconstruct source objects from target
        java.util.Map<EObject, EObject> _ctmBwObjectMapInverse = new java.util.HashMap<>();
        for (java.util.Map.Entry<EObject, EObject> _e : objectMap.entrySet())
            if (_e.getValue() != null) _ctmBwObjectMapInverse.put(_e.getValue(), _e.getKey());
        java.util.Map<EObject, EObject> _ctmBwNewPairs = new java.util.LinkedHashMap<>();

        for (EObject _ctmBwTgt : allSourceObjects(targetResource)) {
    <#list conditionalTypeMappings as ctm>
        <#list ctm.branches() as branch>
            <#if branch.targetType()?? && branch.backwardCondition()??>
            if (!_ctmBwObjectMapInverse.containsKey(_ctmBwTgt)
                    && _ctmBwTgt instanceof ${targetPackageName}.${branch.targetType()} _ctmBwTyped
                    && (${branch.backwardCondition()})) {
                // Resolve source container
                EObject _ctmBwSrcContainer;
                <#if branch.backwardParentExpression()??>
                {
                    EObject target = _ctmBwTgt;
                    java.util.Map<EObject, EObject> objectMapInverse = _ctmBwObjectMapInverse;
                    Resource _ctmBwSrcRes = sourceResource;
                    _ctmBwSrcContainer = (EObject)(${branch.backwardParentExpression()});
                }
                <#else>
                _ctmBwSrcContainer = _ctmBwObjectMapInverse.get(_ctmBwTgt.eContainer());
                </#if>
                if (_ctmBwSrcContainer instanceof ${sourcePackageName}.${ctm.sourceContainerOwnerType()} _ctmBwParent) {
                    ${sourcePackageName}.${ctm.sourceType()} _ctmBwNewSrc =
                        ${sourceFactory}.eINSTANCE.create${ctm.sourceType()}();
                    // Apply backward attribute mappings
                    {
                        ${targetPackageName}.${branch.targetType()} target = _ctmBwTyped;
                        ${sourcePackageName}.${ctm.sourceType()} source = _ctmBwNewSrc;
                <#list ctmAttributeMappings as am>
                <#if am.sourceOwnerType() == ctm.sourceType() && am.targetOwnerType() == branch.targetType() && am.backwardExpression()??>
                        source.set${am.sourceAttr()?cap_first}(${am.backwardExpression()});
                </#if>
                </#list>
                    }
                    // Add to source container feature
                    org.eclipse.emf.ecore.EStructuralFeature _ctmBwFeat =
                        _ctmBwParent.eClass().getEStructuralFeature("${ctm.sourceContainerRef()}");
                    if (_ctmBwFeat != null && _ctmBwFeat.isMany()) {
                        @SuppressWarnings("unchecked")
                        java.util.List<EObject> _ctmBwList =
                            (java.util.List<EObject>) _ctmBwParent.eGet(_ctmBwFeat);
                        _ctmBwList.add(_ctmBwNewSrc);
                    }
                    _ctmBwNewPairs.put(_ctmBwNewSrc, _ctmBwTyped);
                    _ctmBwObjectMapInverse.put(_ctmBwTyped, _ctmBwNewSrc);
                }
            }
            </#if>
        </#list>
    </#list>
        }
        _ctmBwNewPairs.forEach((k, v) -> objectMap.put(v, k));

        // Phase 1f.5: Deferred backward attribute mappings — re-apply after all CTM objects exist
        // (needed for attributes that cross-reference other CTM-created objects, e.g. EReference.eType)
        for (java.util.Map.Entry<EObject, EObject> _deferEntry : _ctmBwNewPairs.entrySet()) {
            EObject _deferSrc = _deferEntry.getKey();
            EObject _deferTgt = _deferEntry.getValue();
    <#list conditionalTypeMappings as ctm>
    <#list ctm.branches() as branch>
    <#assign hasDeferredAttrs = false>
    <#list ctmAttributeMappings as am>
    <#if am.sourceOwnerType() == ctm.sourceType() && am.targetOwnerType() == branch.targetType() && am.backwardExpression()?? && am.backwardExpression()?has_content><#assign hasDeferredAttrs = true></#if>
    </#list>
    <#if hasDeferredAttrs>
            if (_deferTgt instanceof ${targetPackageName}.${branch.targetType()} _ctmBwTyped
                    && _deferSrc instanceof ${sourcePackageName}.${ctm.sourceType()} _ctmBwKnownSrc) {
                ${targetPackageName}.${branch.targetType()} target = _ctmBwTyped;
                ${sourcePackageName}.${ctm.sourceType()} source = _ctmBwKnownSrc;
                EObject _ctmBwParent = source.eContainer();
    <#list ctmAttributeMappings as am>
    <#if am.sourceOwnerType() == ctm.sourceType() && am.targetOwnerType() == branch.targetType() && am.backwardExpression()??>                source.set${am.sourceAttr()?cap_first}(${am.backwardExpression()});
    </#if>
    </#list>
            }
    </#if>
    </#list>
    </#list>
        }
</#if>

        // Phase 2: Resolve all cross-references
        resolveReferencesBack(objectMap);

        // Post-processing: all non-root source objects are "created" in batch backward mode
        List<EObject> _batchBackCreated = new ArrayList<>(objectMap.values());
<#if aggregationMappings?has_content>
        java.util.Set<EObject> _aggBackCreatedSet = new java.util.LinkedHashSet<>(_aggBackObjectMap.values());
        _batchBackCreated.addAll(_aggBackCreatedSet);
</#if>
<#if structuralDeduplicationMappings?has_content>
        for (java.util.List<EObject> _sdBackList : _sdBackDedupMap.values()) {
            _batchBackCreated.addAll(_sdBackList);
        }
</#if>
        sourceRoots.forEach(_batchBackCreated::remove);
        postProcessor.afterTransform(targetResource, sourceResource, objectMap, _batchBackCreated, List.of());
    }

    // ════════════════════════════════════════════════════════════════════════
    // Incremental Backward Path
    // ════════════════════════════════════════════════════════════════════════

