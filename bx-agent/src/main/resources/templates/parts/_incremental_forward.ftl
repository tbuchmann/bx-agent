    private static void transformIncremental(TransformationContext ctx, Options options, PostProcessor postProcessor) {
        Resource sourceModel   = ctx.getSourceModel();
        Resource existingTarget = ctx.getExistingTarget();
        Resource corrResource  = ctx.getCorrModel();
        com.google.common.collect.BiMap<EObject, EObject> corrIndex = ctx.getCorrIndex();
        List<EObject> _created = new ArrayList<>();
        List<EObject> _updated = new ArrayList<>();

        // Bootstrap: ensure root element pairs are in the corr model before Phase 1.
        // Without this, root elements (eContainer == null) would be treated as new objects
        // on the first incremental call and a duplicate root would be added to the target resource.
        {
            List<EObject> _sRoots = sourceModel.getContents();
            List<EObject> _tRoots = existingTarget.getContents();
            for (int _ri = 0; _ri < Math.min(_sRoots.size(), _tRoots.size()); _ri++) {
                EObject _sRoot = _sRoots.get(_ri);
                EObject _tRoot = _tRoots.get(_ri);
                if (CorrespondenceModel.findBySource(corrResource, _sRoot).isEmpty()) {
                    updateTargetAttributes(_sRoot, _tRoot, options);
                    String _rfp  = computeFingerprint(_sRoot);
                    String _rbfp = computeFingerprintBack(_tRoot);
                    CorrespondenceModel.addEntry(corrResource,
                            _sRoot, _sRoot.eClass().getName(), _rfp,
                            _tRoot, _tRoot.eClass().getName(), _rbfp, "", "");
                    corrIndex.put(_sRoot, _tRoot);
                }
            }
        }

        // Phase 1: Regular typed objects (TypeMappings only).
        // Role-based source types (e.g. FamilyMember) are handled exclusively in Phase 1b
        // to avoid consuming their change signal before mapRoleBasedTypesIncremental runs.
        for (EObject srcObj : allSourceObjects(sourceModel)) {
            if (!isCoveredByTypeMappingSource(srcObj)) continue;
            String currentFp = computeFingerprint(srcObj);
            Optional<EObject> existingEntry = CorrespondenceModel.findBySource(corrResource, srcObj);

            if (existingEntry.isEmpty()) {
                // New object: create and add to target containment
                EObject newTarget = createNewTargetObject(srcObj, options);
                if (newTarget != null) {
                    addToTargetContainment(srcObj, newTarget, existingTarget, corrIndex);
                    String _srcCR = srcObj.eContainmentFeature() != null ? srcObj.eContainmentFeature().getName() : "";
                    String _tgtCR = newTarget.eContainmentFeature() != null ? newTarget.eContainmentFeature().getName() : "";
                    CorrespondenceModel.addEntry(corrResource,
                            srcObj, srcObj.eClass().getName(), currentFp,
                            newTarget, newTarget.eClass().getName(), computeFingerprintBack(newTarget), _srcCR, _tgtCR);
                    corrIndex.put(srcObj, newTarget);
                    _created.add(newTarget);
                }
            } else {
                // Known: check containment role and fingerprint for changes
                EObject targetObj = corrIndex.get(srcObj);
                // Re-attach to target resource if detached (e.g. target was reset but corr model persists).
                // Prefer the stored target containment role so cross-named containment features
                // (e.g. source "eClassifiers" → target "ownedTables") are matched correctly.
                if (targetObj != null && targetObj.eResource() == null) {
                    String _storedTgtRole = CorrespondenceModel.getTargetContainmentRole(existingEntry.get());
                    if (_storedTgtRole != null && !_storedTgtRole.isEmpty()) {
                        EObject _srcParent = srcObj.eContainer();
                        EObject _tgtParent = _srcParent != null ? corrIndex.get(_srcParent) : null;
                        if (_tgtParent != null) {
                            org.eclipse.emf.ecore.EStructuralFeature _raFeat =
                                _tgtParent.eClass().getEStructuralFeature(_storedTgtRole);
                            if (_raFeat != null && targetObj.eContainer() == null) {
                                if (_raFeat.isMany()) {
                                    @SuppressWarnings("unchecked")
                                    EList<EObject> _raList = (EList<EObject>) _tgtParent.eGet(_raFeat);
                                    _raList.add(targetObj);
                                } else {
                                    _tgtParent.eSet(_raFeat, targetObj);
                                }
                            }
                        } else {
                            existingTarget.getContents().add(targetObj);
                        }
                    } else {
                        addToTargetContainment(srcObj, targetObj, existingTarget, corrIndex);
                    }
                }
                String _currentSrcRole = srcObj.eContainmentFeature() != null ? srcObj.eContainmentFeature().getName() : "";
                String _storedSrcRole  = CorrespondenceModel.getSourceContainmentRole(existingEntry.get());
                if (_storedSrcRole == null) _storedSrcRole = "";
                if (!_currentSrcRole.equals(_storedSrcRole)) {
                    // Containment role changed for a TypeMapping object.
                    // Update the stored role; for role-based types this is handled in Phase 1b.
                    CorrespondenceModel.updateSourceContainmentRole(existingEntry.get(), _currentSrcRole);
                }
                String storedFp = CorrespondenceModel.getFingerprint(existingEntry.get());
                if (!currentFp.equals(storedFp)) {
                    if (targetObj != null) {
                        updateTargetAttributes(srcObj, targetObj, options);
                        CorrespondenceModel.updateFingerprint(existingEntry.get(), currentFp);
                        // Also update target fingerprint so backward direction sees a consistent baseline.
                        CorrespondenceModel.updateTargetFingerprint(existingEntry.get(), computeFingerprintBack(targetObj));
                        _updated.add(targetObj);
                    }
                }
            }
        }
<#if roleBasedTypeMappingModels?has_content>

        // Phase 1b: Role-based types (incremental)
        List<EObject> sourceRoots = sourceModel.getContents();
        List<EObject> targetRoots = existingTarget.getContents();
        for (int i = 0; i < Math.min(sourceRoots.size(), targetRoots.size()); i++) {
            mapRoleBasedTypesIncremental(sourceRoots.get(i), targetRoots.get(i), corrResource, corrIndex, options);
        }
</#if>
<#if edgeMaterializationMappings?has_content>

        // Phase 1c: Edge materialization (incremental)
        materializeEdgesIncremental(sourceModel, existingTarget, corrIndex);
</#if>
<#if aggregationMappings?has_content>

        // Phase 1d: Aggregation (incremental)
        java.util.Map<EObject, EObject> _aggregationIndex = CorrespondenceModel.buildAggregationIndex(corrResource);
    <#list aggregationMappings as agg>
        materializeAggregation${agg.sourceType()}Incremental(sourceModel, existingTarget, corrResource, corrIndex, _aggregationIndex, _created, _updated);
    </#list>
</#if>
<#if structuralDeduplicationMappings?has_content>

        // Phase 1e: Structural deduplication (incremental)
        java.util.Map<EObject, EObject> _sdDedupIndex = CorrespondenceModel.buildStructuralDedupIndex(corrResource);
    <#list structuralDeduplicationMappings as sdm>
        _materializeStructuralDedup${sdm.abstractSourceType()}Incremental(sourceModel, existingTarget, corrResource, corrIndex, _sdDedupIndex, _created, _updated);
    </#list>
</#if>
<#if conditionalTypeMappings?has_content>

        // Phase 1f: Conditional type mappings (incremental)
        for (EObject _ctmOwner : allSourceObjects(sourceModel)) {
    <#list conditionalTypeMappings as ctm>
            if (_ctmOwner instanceof ${sourcePackageName}.${ctm.sourceContainerOwnerType()} _ctmParentObj) {
                EObject _ctmMappedParent = corrIndex.get(_ctmOwner);
                if (_ctmMappedParent == null) continue;
                Object _ctmFeatVal = _ctmOwner.eGet(
                    _ctmOwner.eClass().getEStructuralFeature("${ctm.sourceContainerRef()}"));
                @SuppressWarnings("unchecked")
                java.util.List<Object> _ctmItems = _ctmFeatVal instanceof java.util.List<?> _l
                    ? (java.util.List<Object>) _l
                    : (_ctmFeatVal instanceof EObject _eo ? java.util.List.of((Object) _eo) : java.util.List.of());
                for (Object _ctmItem : _ctmItems) {
                    if (!(_ctmItem instanceof ${sourcePackageName}.${ctm.sourceType()} _ctmSrc)) continue;
                    String _ctmFp = computeFingerprint((EObject) _ctmItem);
                    Optional<EObject> _ctmExistingEntry = CorrespondenceModel.findBySource(corrResource, (EObject) _ctmItem);
                    if (_ctmExistingEntry.isEmpty()) {
                        // New CTM object: dispatch and create
                        EObject _ctmNewTarget = null;
        <#list ctm.branches() as branch>
            <#if branch.targetType()??>
                        if (_ctmNewTarget == null<#if branch.condition()?? && branch.condition()?has_content> && (${branch.condition()})</#if>) {
                            // Resolve placement: ${branch.targetPlacementType()}
                <#if branch.targetPlacementType() == "PARENT">
                            EObject _ctmContainer = _ctmMappedParent;
                <#elseif branch.targetPlacementType() == "ROOT">
                            EObject _ctmContainer = existingTarget.getContents().isEmpty() ? null : existingTarget.getContents().get(0);
                <#elseif branch.targetPlacementType() == "REFERENCE_TARGET">
                            Object _ctmRef = _ctmSrc.eGet(_ctmSrc.eClass().getEStructuralFeature("${branch.targetPlacementRef()}"));
                            EObject _ctmContainer = _ctmRef instanceof EObject _ctmRefEObj
                                ? corrIndex.get(_ctmRefEObj) : null;
                </#if>
                            if (_ctmContainer != null) {
                                _ctmNewTarget = ${targetFactory}.eINSTANCE.create${branch.targetType()}();
                <#if branch.nameExpression()?? && branch.nameExpression()?has_content>
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
                                String _ctmSrcCR = ((EObject) _ctmItem).eContainmentFeature() != null ? ((EObject) _ctmItem).eContainmentFeature().getName() : "";
                                String _ctmTgtCR = _ctmNewTarget.eContainmentFeature() != null ? _ctmNewTarget.eContainmentFeature().getName() : "";
                                CorrespondenceModel.addEntry(corrResource,
                                        (EObject) _ctmItem, _ctmSrc.eClass().getName(), _ctmFp,
                                        _ctmNewTarget, _ctmNewTarget.eClass().getName(), computeFingerprintBack(_ctmNewTarget),
                                        _ctmSrcCR, _ctmTgtCR);
                                corrIndex.put((EObject) _ctmItem, _ctmNewTarget);
                                _created.add(_ctmNewTarget);
                            }
                        }
            <#else>
                        // null targetType branch: mark as handled
                        if (_ctmNewTarget == null<#if branch.condition()?? && branch.condition()?has_content> && (${branch.condition()})</#if>) {
                            // produce nothing for this branch
                        }
            </#if>
        </#list>
                    } else {
                        // Known CTM object: check fingerprint and re-attach if detached
                        EObject _ctmTargetObj = corrIndex.get((EObject) _ctmItem);
                        if (_ctmTargetObj != null && _ctmTargetObj.eResource() == null) {
                            String _ctmStoredTgtRole = CorrespondenceModel.getTargetContainmentRole(_ctmExistingEntry.get());
                            if (_ctmStoredTgtRole != null && !_ctmStoredTgtRole.isEmpty()) {
                                EObject _ctmTgtParent = corrIndex.get(_ctmOwner);
                                if (_ctmTgtParent != null) {
                                    org.eclipse.emf.ecore.EStructuralFeature _ctmRaFeat =
                                        _ctmTgtParent.eClass().getEStructuralFeature(_ctmStoredTgtRole);
                                    if (_ctmRaFeat != null && _ctmTargetObj.eContainer() == null) {
                                        if (_ctmRaFeat.isMany()) {
                                            @SuppressWarnings("unchecked")
                                            EList<EObject> _ctmRaList = (EList<EObject>) _ctmTgtParent.eGet(_ctmRaFeat);
                                            _ctmRaList.add(_ctmTargetObj);
                                        } else {
                                            _ctmTgtParent.eSet(_ctmRaFeat, _ctmTargetObj);
                                        }
                                    }
                                }
                            }
                        }
                        // Check if source moved to a different container; relocate target if so
        <#list ctm.branches() as branch>
            <#if branch.targetPlacementType() == "PARENT" && branch.targetType()??>
                        if (_ctmTargetObj instanceof ${targetPackageName}.${branch.targetType()} && _ctmMappedParent != null && _ctmTargetObj.eContainer() != _ctmMappedParent) {
                            org.eclipse.emf.ecore.util.EcoreUtil.remove(_ctmTargetObj);
                            org.eclipse.emf.ecore.EStructuralFeature _ctmMoveContFeat =
                                _ctmMappedParent.eClass().getEStructuralFeature("${branch.targetContainerRef()}");
                            if (_ctmMoveContFeat != null) {
                                if (_ctmMoveContFeat.isMany()) {
                                    @SuppressWarnings("unchecked")
                                    java.util.List<EObject> _ctmMoveList = (java.util.List<EObject>) _ctmMappedParent.eGet(_ctmMoveContFeat);
                                    if (!_ctmMoveList.contains(_ctmTargetObj)) _ctmMoveList.add(_ctmTargetObj);
                                } else {
                                    _ctmMappedParent.eSet(_ctmMoveContFeat, _ctmTargetObj);
                                }
                            }
                            CorrespondenceModel.updateTargetContainmentRole(_ctmExistingEntry.get(),
                                _ctmTargetObj.eContainmentFeature() != null ? _ctmTargetObj.eContainmentFeature().getName() : "");
                        }
            </#if>
        </#list>
                        // Check if source still matches any branch condition; delete orphaned target if not
                        boolean _ctmConditionStillMatches = false;
        <#list ctm.branches() as branch>
            <#if branch.targetType()??>
                        if (!_ctmConditionStillMatches && _ctmTargetObj instanceof ${targetPackageName}.${branch.targetType()}<#if branch.condition()?? && branch.condition()?has_content> && (${branch.condition()})</#if>) {
                            _ctmConditionStillMatches = true;
                        }
            </#if>
        </#list>
                        if (!_ctmConditionStillMatches && _ctmTargetObj != null) {
                            EcoreUtil.delete(_ctmTargetObj, true);
                            corrIndex.remove((EObject) _ctmItem);
                            CorrespondenceModel.removeCorrespondenceEntry(corrResource, _ctmExistingEntry.get());
                        } else if (_ctmConditionStillMatches) {
                            String _ctmStoredFp = CorrespondenceModel.getFingerprint(_ctmExistingEntry.get());
                            if (!_ctmFp.equals(_ctmStoredFp) && _ctmTargetObj != null) {
                                // Update attributes for changed CTM object
        <#list ctm.branches() as branch>
            <#if branch.targetType()??>
                                if (_ctmTargetObj instanceof ${targetPackageName}.${branch.targetType()} _ctmUpdTgt) {
                                    ${sourcePackageName}.${ctm.sourceType()} source = _ctmSrc;
                                    EObject _ctmParentSrc = _ctmOwner;
                                    ${targetPackageName}.${branch.targetType()} target = _ctmUpdTgt;
                <#if branch.nameExpression()?? && branch.nameExpression()?has_content>
                                    _ctmUpdTgt.setName(${branch.nameExpression()});
                </#if>
                <#list ctmAttributeMappings as am>
                <#if am.sourceOwnerType() == ctm.sourceType() && am.targetOwnerType() == branch.targetType() && am.forwardExpression()??>
                                    target.set${am.targetAttr()?cap_first}(${am.forwardExpression()});
                </#if>
                </#list>
                                }
            </#if>
        </#list>
                                CorrespondenceModel.updateFingerprint(_ctmExistingEntry.get(), _ctmFp);
                                CorrespondenceModel.updateTargetFingerprint(_ctmExistingEntry.get(), computeFingerprintBack(_ctmTargetObj));
                                _updated.add(_ctmTargetObj);
                            }
                        }
                    }
                }
            }
    </#list>
        }
</#if>

<#if hasNestedSom>
        // Phase 1.5: Nested synthetic objects (incremental — iterate corrIndex for mapped sources)
        for (EObject _nsynSrc : new java.util.ArrayList<>(corrIndex.keySet())) {
            _createNestedSyntheticObjects(_nsynSrc, corrIndex);
        }
</#if>
<#if targetLinkMappings?has_content && targetLinkMetamodel??>
        // Phase 1.6: Target link generation (incremental)
        _createTargetLinks(corrIndex, existingTarget);
</#if>
        // Phase 2: Detect and handle deleted source objects
        // EMF nullifies CE_SOURCE_OBJECT when the object is deleted from the resource,
        // so we scan for entries with null sourceObject rather than comparing allSourceObjects with corrIndex.
        List<EObject> _deletedFwdEntries = new ArrayList<>(CorrespondenceModel.findDeletedSourceEntries(corrResource));
        // Notify post-processor before actual deletion (CASCADE only) so derived structure can be repaired first
        if (ctx.getDeletionPolicy() == TransformationContext.DeletionPolicy.CASCADE) {
            List<EObject> _toDelete = new ArrayList<>();
            for (EObject _corrEntry : _deletedFwdEntries) {
                EObject targetObj = CorrespondenceModel.getTargetObject(_corrEntry);
                if (targetObj != null) _toDelete.add(targetObj);
            }
            postProcessor.beforeDeletions(_toDelete);
        }
        for (EObject _corrEntry : _deletedFwdEntries) {
            EObject targetObj = CorrespondenceModel.getTargetObject(_corrEntry);
            switch (ctx.getDeletionPolicy()) {
                case CASCADE -> {
                    if (targetObj != null) {
                        EcoreUtil.delete(targetObj, true);
                    }
                    CorrespondenceModel.removeCorrespondenceEntry(corrResource, _corrEntry);
                }
                case ORPHAN -> CorrespondenceModel.removeCorrespondenceEntry(corrResource, _corrEntry);
                case TOMBSTONE -> CorrespondenceModel.markOrphaned(_corrEntry);
            }
        }

<#if targetLinkMappings?has_content && targetLinkMetamodel??>
        // Phase 2.5: Remove TLM link objects (FKs) whose slot (column) was nullified by Phase 2 deletions.
        // EcoreUtil.delete(slot, true) nullifies FK.column cross-references but leaves the FK in ownedForeignKeys.
        {
            final String _c2Nodes = "${targetLinkMetamodel.nodeContainerFeature()}";
            final String _c2Links = "${targetLinkMetamodel.linkContainerFeature()}";
            final String _c2Src   = "${targetLinkMetamodel.linkSourceFeature()}";
            for (EObject _c2Root : existingTarget.getContents()) {
                for (EObject _c2Node : new ArrayList<>(_tlmList(_c2Root, _c2Nodes))) {
                    _tlmList(_c2Node, _c2Links).removeIf(lk -> _tlmGetRef(lk, _c2Src) == null);
                }
            }
        }
</#if>
        // Phase 3: Resolve cross-references incrementally using corrIndex as lookup.
        // Must run after Phase 1 so all new objects are in corrIndex.
        // Iterates all source objects (not just changed ones) because cross-reference changes
        // are not captured by the fingerprint.
        resolveReferencesIncremental(sourceModel, existingTarget, corrIndex);

        CorrespondenceModel.saveAndUpdateTimestamp(corrResource);

        postProcessor.afterTransform(sourceModel, existingTarget, corrIndex, _created, _updated);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Batch Backward Path
    // ════════════════════════════════════════════════════════════════════════

