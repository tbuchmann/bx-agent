
    // ════════════════════════════════════════════════════════════════════════
    // Batch Forward Path
    // ════════════════════════════════════════════════════════════════════════

    private static void transformBatch(TransformationContext ctx, Options options, PostProcessor postProcessor) {
        Resource sourceResource = ctx.getSourceModel();
        Resource targetResource = ctx.getExistingTarget();

        Map<EObject, EObject> objectMap = new HashMap<>();
        Set<EObject> visited = new HashSet<>();

        // Pre-populate objectMap with existing root element pairs (by index)
        // and apply root-type attribute mappings
        List<EObject> sourceRoots = sourceResource.getContents();
        List<EObject> targetRoots = targetResource.getContents();
        for (int i = 0; i < Math.min(sourceRoots.size(), targetRoots.size()); i++) {
<#if rootSourceType??>
            ${sourcePackageName}.${rootSourceType} source = (${sourcePackageName}.${rootSourceType}) sourceRoots.get(i);
            ${targetPackageName}.${rootTargetType} target = (${targetPackageName}.${rootTargetType}) targetRoots.get(i);
    <#list attributeMappings as mapping>
        <#if mapping.sourceOwnerType()?? && (mapping.sourceOwnerType() == rootSourceType || !typeMappings?filter(tm -> tm.sourceType() == mapping.sourceOwnerType())?has_content) && mapping.forwardExpression()?? && mapping.targetAttr()?? && mapping.targetAttr()?has_content>
            target.set${mapping.targetAttr()?cap_first}(${mapping.forwardExpression()});
        </#if>
    </#list>
    <#list typeMappings as typeMapping>
        <#if typeMapping.sourceType() == rootSourceType && typeMapping.forwardAnnotations()?has_content>
            // Forward annotations for root ${typeMapping.sourceType()} → ${typeMapping.targetType()}
        <#list typeMapping.forwardAnnotations() as ann>
            _addAnnotation(target, "${ann}");
        </#list>
        </#if>
    </#list>
<#else>
            EObject source = sourceRoots.get(i);
            EObject target = targetRoots.get(i);
</#if>
            objectMap.put(source, target);
        }

<#if syntheticObjectMappings?has_content>
        // Phase 0: Synthetic objects (created once per source container instance, no source counterpart)
        for (int i = 0; i < Math.min(sourceRoots.size(), targetRoots.size()); i++) {
            _createSyntheticObjects(sourceRoots.get(i), targetRoots.get(i), objectMap);
        }
</#if>

        // Phase 1: Traverse containment hierarchy and create contained objects
        for (EObject sourceRoot : sourceRoots) {
            createAndMapObjects(sourceRoot, objectMap, visited, options);
        }
<#if roleBasedTypeMappingModels?has_content>

        // Phase 1b: Role-based type mappings (intermediate types without target counterparts)
        for (int i = 0; i < Math.min(sourceRoots.size(), targetRoots.size()); i++) {
            mapRoleBasedTypes(sourceRoots.get(i), targetRoots.get(i), objectMap);
        }
</#if>
<#if edgeMaterializationMappings?has_content>

        // Phase 1c: Edge materialization (references → explicit edge objects)
        materializeEdges(sourceResource, targetResource, objectMap);
</#if>
<#if aggregationMappings?has_content>

        // Phase 1d: Aggregation (many source objects → one target object per group key)
        Map<EObject, EObject> _aggObjectMap = new java.util.LinkedHashMap<>();
        for (EObject _srcObj : allSourceObjects(sourceResource)) {
    <#list aggregationMappings as agg>
            if (_srcObj instanceof ${sourcePackageName}.${agg.sourceContainerType()} _srcContainer) {
                EObject _tgtObj = objectMap.get(_srcObj);
                if (_tgtObj instanceof ${targetPackageName}.${agg.targetContainerType()} _tgtContainer) {
                    materializeAggregation${agg.sourceType()}(_srcContainer, _tgtContainer, _aggObjectMap);
                }
            }
    </#list>
        }
</#if>
<#if structuralDeduplicationMappings?has_content>

        // Phase 1e: Structural deduplication (tree → DAG, equal subtrees → shared target nodes)
        java.util.Map<EObject, EObject> _sdObjectMap = new java.util.LinkedHashMap<>();
        java.util.Map<EObject, String> _sdFpCache = new java.util.HashMap<>();
        for (EObject _srcObj : allSourceObjects(sourceResource)) {
    <#list structuralDeduplicationMappings as sdm>
            if (_srcObj instanceof ${sourcePackageName}.${sdm.sourceContainerType()} _srcContainer) {
                EObject _tgtObj = objectMap.get(_srcObj);
                if (_tgtObj instanceof ${targetPackageName}.${sdm.targetContainerType()} _tgtContainer) {
                    _materializeStructuralDedup${sdm.abstractSourceType()}(_srcContainer, _tgtContainer, _sdObjectMap, _sdFpCache);
                }
            }
    </#list>
        }
</#if>

<#if conditionalTypeMappings?has_content>

        // Phase 1f: Conditional type mappings (one source type → multiple target types by condition)
        Map<EObject, EObject> _ctmObjectMap = new java.util.LinkedHashMap<>();
        for (EObject _ctmOwner : allSourceObjects(sourceResource)) {
    <#list conditionalTypeMappings as ctm>
            if (_ctmOwner instanceof ${sourcePackageName}.${ctm.sourceContainerOwnerType()} _ctmParentObj) {
                EObject _ctmMappedParent = _ctmObjectMap.containsKey(_ctmOwner) ? _ctmObjectMap.get(_ctmOwner) : objectMap.get(_ctmOwner);
                if (_ctmMappedParent == null) continue;
                Object _ctmFeatVal = _ctmOwner.eGet(
                    _ctmOwner.eClass().getEStructuralFeature("${ctm.sourceContainerRef()}"));
                @SuppressWarnings("unchecked")
                java.util.List<Object> _ctmItems = _ctmFeatVal instanceof java.util.List<?> _l
                    ? (java.util.List<Object>) _l
                    : (_ctmFeatVal instanceof EObject _eo ? java.util.List.of((Object) _eo) : java.util.List.of());
                for (Object _ctmItem : _ctmItems) {
                    if (!(_ctmItem instanceof ${sourcePackageName}.${ctm.sourceType()} _ctmSrc)) continue;
                    if (_ctmObjectMap.containsKey((EObject) _ctmItem)) continue;
                    EObject _ctmNewTarget = null;
        <#list ctm.branches() as branch>
            <#if branch.targetType()??>
                    if (_ctmNewTarget == null<#if branch.condition()?? && branch.condition()?has_content> && (${branch.condition()})</#if>) {
                        // Resolve placement: ${branch.targetPlacementType()}
                <#if branch.targetPlacementType() == "PARENT">
                        EObject _ctmContainer = _ctmMappedParent;
                <#elseif branch.targetPlacementType() == "ROOT">
                        EObject _ctmContainer = targetRoots.isEmpty() ? null : targetRoots.get(0);
                <#elseif branch.targetPlacementType() == "REFERENCE_TARGET">
                        Object _ctmRef = _ctmSrc.eGet(_ctmSrc.eClass().getEStructuralFeature("${branch.targetPlacementRef()}"));
                        EObject _ctmContainer = _ctmRef instanceof EObject _ctmRefEObj
                            ? objectMap.get(_ctmRefEObj) : null;
                </#if>
                        if (_ctmContainer != null) {
                            _ctmNewTarget = ${targetFactory}.eINSTANCE.create${branch.targetType()}();
                <#if branch.nameExpression()?? && branch.nameExpression()?has_content>
                            // Set name
                            {
                                ${sourcePackageName}.${ctm.sourceType()} source = _ctmSrc;
                                EObject _ctmParentSrc = _ctmOwner;
                                ((${targetPackageName}.${branch.targetType()}) _ctmNewTarget)
                                    .setName(${branch.nameExpression()});
                            }
                </#if>
                            // Apply attribute mappings for ${ctm.sourceType()} → ${branch.targetType()}
                            {
                                ${sourcePackageName}.${ctm.sourceType()} source = _ctmSrc;
                                ${targetPackageName}.${branch.targetType()} target =
                                    (${targetPackageName}.${branch.targetType()}) _ctmNewTarget;
                <#list ctmAttributeMappings as am>
                <#if am.sourceOwnerType() == ctm.sourceType() && am.targetOwnerType() == branch.targetType() && am.forwardExpression()??>
                                target.set${am.targetAttr()?cap_first}(${am.forwardExpression()});
                </#if>
                </#list>
                            }
                            // Add to container
                            {
                                org.eclipse.emf.ecore.EStructuralFeature _ctmContFeat =
                                    _ctmContainer.eClass().getEStructuralFeature("${branch.targetContainerRef()}");
                                if (_ctmContFeat != null) {
                                    if (_ctmContFeat.isMany()) {
                                        @SuppressWarnings("unchecked")
                                        java.util.List<EObject> _ctmContList =
                                            (java.util.List<EObject>) _ctmContainer.eGet(_ctmContFeat);
                                        _ctmContList.add(_ctmNewTarget);
                                    } else {
                                        _ctmContainer.eSet(_ctmContFeat, _ctmNewTarget);
                                    }
                                }
                            }
                <#list branch.forwardAnnotations() as ann>
                            _addAnnotation(_ctmNewTarget, "${ann}");
                </#list>
                            _ctmObjectMap.put((EObject) _ctmItem, _ctmNewTarget);
                        }
                    }
            <#else>
                    // null targetType branch: mark as handled (skip — produce nothing)
                    if (_ctmNewTarget == null<#if branch.condition()?? && branch.condition()?has_content> && (${branch.condition()})</#if>) {
                        _ctmObjectMap.put((EObject) _ctmItem, null);
                    }
            </#if>
        </#list>
                }
            }
    </#list>
        }
        // Merge non-null CTM entries into main objectMap
        _ctmObjectMap.forEach((k, v) -> { if (v != null) objectMap.put(k, v); });
</#if>
<#assign hasNestedSom = false>
<#if syntheticObjectMappings?has_content>
  <#list syntheticObjectMappings as som>
    <#if som.nestedInMappedTarget()><#assign hasNestedSom = true></#if>
  </#list>
</#if>
<#if hasNestedSom>
        // Phase 1.5: Nested synthetic objects (placed inside the mapped target of each source instance)
        // Iterate objectMap.keySet() — allSourceObjects may not traverse into sub-resources
        for (EObject _nsynSrc : new java.util.ArrayList<>(objectMap.keySet())) {
            _createNestedSyntheticObjects(_nsynSrc, objectMap);
        }
</#if>
<#if targetLinkMappings?has_content && targetLinkMetamodel??>
        // Phase 1.6: Target link generation
        _createTargetLinks(objectMap, targetResource);
</#if>

        // Phase 2: Resolve all cross-references
        resolveReferences(objectMap);

        // After batch: create correspondence model (if resources have persistent URIs)
        if (sourceResource.getURI() != null && targetResource.getURI() != null
                && sourceResource.getResourceSet() != null) {
            org.eclipse.emf.common.util.URI corrURI = CorrespondenceModel.deriveCorrespondenceURI(
                    sourceResource.getURI(), targetResource.getURI());
            Resource corrResource = CorrespondenceModel.loadOrCreate(corrURI, sourceResource.getResourceSet());
            // Batch always rebuilds the correspondence from scratch — clear stale entries first.
            corrResource.getContents().clear();
            for (Map.Entry<EObject, EObject> entry : objectMap.entrySet()) {
                String _srcCR = entry.getKey().eContainmentFeature() != null ? entry.getKey().eContainmentFeature().getName() : "";
                String _tgtCR = entry.getValue().eContainmentFeature() != null ? entry.getValue().eContainmentFeature().getName() : "";
                CorrespondenceModel.addEntry(corrResource,
                        entry.getKey(), entry.getKey().eClass().getName(),
                        computeFingerprint(entry.getKey()),
                        entry.getValue(), entry.getValue().eClass().getName(),
                        computeFingerprintBack(entry.getValue()),
                        _srcCR, _tgtCR);
            }
<#if aggregationMappings?has_content>
            for (Map.Entry<EObject, EObject> entry : _aggObjectMap.entrySet()) {
                CorrespondenceModel.addAggregationEntry(corrResource,
                        entry.getKey(), entry.getKey().eClass().getName(),
                        computeFingerprint(entry.getKey()),
                        entry.getValue(), entry.getValue().eClass().getName(),
                        computeFingerprintBack(entry.getValue()));
            }
</#if>
<#if structuralDeduplicationMappings?has_content>
            for (Map.Entry<EObject, EObject> _sdEntry : _sdObjectMap.entrySet()) {
                String _sdSrcFp = _sdFpCache.getOrDefault(_sdEntry.getKey(), "");
                String _sdTgtFp = computeFingerprintBack(_sdEntry.getValue());
                CorrespondenceModel.addStructuralDedupEntry(corrResource,
                        _sdEntry.getKey(), _sdEntry.getKey().eClass().getName(), _sdSrcFp,
                        _sdEntry.getValue(), _sdEntry.getValue().eClass().getName(), _sdTgtFp);
            }
</#if>
            CorrespondenceModel.saveAndUpdateTimestamp(corrResource);
        }

        // Post-processing: all non-root target objects are "created" in batch mode
        List<EObject> _batchCreated = new ArrayList<>(objectMap.values());
<#if aggregationMappings?has_content>
        java.util.Set<EObject> _aggCreatedSet = new java.util.LinkedHashSet<>(_aggObjectMap.values());
        _batchCreated.addAll(_aggCreatedSet);
</#if>
<#if structuralDeduplicationMappings?has_content>
        java.util.Set<EObject> _sdCreatedSet = new java.util.LinkedHashSet<>(_sdObjectMap.values());
        _batchCreated.addAll(_sdCreatedSet);
</#if>
        targetRoots.forEach(_batchCreated::remove);
        postProcessor.afterTransform(sourceResource, targetResource, objectMap, _batchCreated, List.of());
    }

    // ════════════════════════════════════════════════════════════════════════
    // Incremental Forward Path
    // ════════════════════════════════════════════════════════════════════════

