    private static void transformIncrementalBack(TransformationContext ctx, Options options, PostProcessor postProcessor) {
        // In backward direction:
        // ctx.getSourceModel()    = target model (what we read from, in forward naming = "target")
        // ctx.getExistingTarget() = source model (what we write to, in forward naming = "source")
        Resource targetModel  = ctx.getSourceModel();
        Resource sourceModel  = ctx.getExistingTarget();
        Resource corrResource = ctx.getCorrModel();
        // corrIndex is forward: source→target.  corrIndex.inverse() is target→source.
        com.google.common.collect.BiMap<EObject, EObject> corrIndex = ctx.getCorrIndex();
        List<EObject> _createdBack = new ArrayList<>();
        List<EObject> _updatedBack = new ArrayList<>();

        // Bootstrap: ensure root element pairs are in the corr model before Phase 1.
        {
            List<EObject> _tRoots = targetModel.getContents();
            List<EObject> _sRoots = sourceModel.getContents();
            for (int _ri = 0; _ri < Math.min(_tRoots.size(), _sRoots.size()); _ri++) {
                EObject _tRoot = _tRoots.get(_ri);
                EObject _sRoot = _sRoots.get(_ri);
                if (!corrIndex.inverse().containsKey(_tRoot)) {
                    updateSourceAttributes(_tRoot, _sRoot, options);
                    String _rfp  = computeFingerprint(_sRoot);
                    String _rbfp = computeFingerprintBack(_tRoot);
                    CorrespondenceModel.addEntry(corrResource,
                            _sRoot, _sRoot.eClass().getName(), _rfp,
                            _tRoot, _tRoot.eClass().getName(), _rbfp, "", "");
                    corrIndex.put(_sRoot, _tRoot);
                }
            }
        }

        // Phase 1: All typed objects — TypeMappings AND role-based target types.
        // For TypeMapping objects: update attributes + fingerprint here.
        // For role-based objects (e.g. Male/Female): update simple attributes here but
        //   do NOT update the fingerprint — Phase 1b (mapRoleBasedTypesIncrementalBack)
        //   needs to see the mismatch to apply structural changes (e.g. family moves)
        //   before committing the new fingerprint.
        for (EObject tgtObj : allSourceObjects(targetModel)) {
            if (!corrIndex.inverse().containsKey(tgtObj)) {
                // New target object — only create if it's a TypeMapping type.
                // New role-based objects are handled in Phase 1b.
                if (!isCoveredByTypeMappingTarget(tgtObj)) continue;
<#if syntheticObjectMappings?has_content>
                // Synthetic objects have no source counterpart — skip in all backward directions.
                if (isSyntheticObject(tgtObj)) continue;
</#if>
                EObject newSource = createNewSourceObject(tgtObj, options);
                if (newSource != null) {
                    addToSourceContainment(tgtObj, newSource, sourceModel, corrIndex);
                    String srcFp = computeFingerprint(newSource);
                    String tgtFp = computeFingerprintBack(tgtObj);
                    String _srcCR = newSource.eContainmentFeature() != null ? newSource.eContainmentFeature().getName() : "";
                    String _tgtCR = tgtObj.eContainmentFeature() != null ? tgtObj.eContainmentFeature().getName() : "";
                    CorrespondenceModel.addEntry(corrResource,
                            newSource, newSource.eClass().getName(), srcFp,
                            tgtObj, tgtObj.eClass().getName(), tgtFp, _srcCR, _tgtCR);
                    corrIndex.put(newSource, tgtObj);
                    _createdBack.add(newSource);
                }
            } else {
                // Known: check containment role and target fingerprint for changes
                EObject srcObj = corrIndex.inverse().get(tgtObj);
                if (srcObj != null) {
                    Optional<EObject> entryOpt = CorrespondenceModel.findBySource(corrResource, srcObj);
                    // Re-attach source to source resource if detached (e.g. source was reset but corr model persists).
                    // Prefer the stored source containment role so cross-named containment features
                    // (e.g. target "ownedTables" → source "eClassifiers") are matched correctly.
                    if (srcObj.eResource() == null) {
                        String _storedSrcRole = entryOpt.map(CorrespondenceModel::getSourceContainmentRole).orElse("");
                        if (_storedSrcRole != null && !_storedSrcRole.isEmpty()) {
                            EObject _tgtParent = tgtObj.eContainer();
                            EObject _srcParent = _tgtParent != null ? corrIndex.inverse().get(_tgtParent) : null;
                            if (_srcParent != null) {
                                org.eclipse.emf.ecore.EStructuralFeature _raFeat =
                                    _srcParent.eClass().getEStructuralFeature(_storedSrcRole);
                                if (_raFeat != null && srcObj.eContainer() == null) {
                                    if (_raFeat.isMany()) {
                                        @SuppressWarnings("unchecked")
                                        EList<EObject> _raList = (EList<EObject>) _srcParent.eGet(_raFeat);
                                        _raList.add(srcObj);
                                    } else {
                                        _srcParent.eSet(_raFeat, srcObj);
                                    }
                                }
                            } else {
                                sourceModel.getContents().add(srcObj);
                            }
                        } else {
                            addToSourceContainment(tgtObj, srcObj, sourceModel, corrIndex);
                        }
                    }
                    if (entryOpt.isPresent()) {
                        String _currentTgtRole = tgtObj.eContainmentFeature() != null ? tgtObj.eContainmentFeature().getName() : "";
                        String _storedTgtRole  = CorrespondenceModel.getTargetContainmentRole(entryOpt.get());
                        if (_storedTgtRole == null) _storedTgtRole = "";
                        if (!_currentTgtRole.equals(_storedTgtRole)) {
                            // Target containment role changed for a TypeMapping object.
                            // Update stored role; for role-based types this is handled in Phase 1b.
                            CorrespondenceModel.updateTargetContainmentRole(entryOpt.get(), _currentTgtRole);
                        }
                        String storedFp = CorrespondenceModel.getTargetFingerprint(entryOpt.get());
                        String currentFp = computeFingerprintBack(tgtObj);
                        if (storedFp == null || storedFp.isEmpty() || !currentFp.equals(storedFp)) {
                            // Apply backward attribute mappings for this type
                            updateSourceAttributes(tgtObj, srcObj, options);
                            // Only commit the fingerprint for TypeMapping types.
                            // Role-based types: Phase 1b commits the fingerprint after structural updates.
                            if (isCoveredByTypeMappingTarget(tgtObj)) {
                                CorrespondenceModel.updateTargetFingerprint(entryOpt.get(), currentFp);
                                // Also update source fingerprint so forward direction sees a consistent baseline.
                                CorrespondenceModel.updateFingerprint(entryOpt.get(), computeFingerprint(srcObj));
                                _updatedBack.add(srcObj);
                            }
                        }
                    }
                }
            }
        }
<#if roleBasedTypeMappingModels?has_content>

        // Phase 1b: Role-based types (backward incremental)
        List<EObject> targetRoots = targetModel.getContents();
        List<EObject> sourceRoots = sourceModel.getContents();
        for (int i = 0; i < Math.min(targetRoots.size(), sourceRoots.size()); i++) {
            mapRoleBasedTypesIncrementalBack(targetRoots.get(i), sourceRoots.get(i), corrResource, corrIndex, options);
        }
</#if>
<#if edgeMaterializationMappings?has_content>

        // Phase 1c: Edge materialization backward (incremental)
        materializeEdgesIncrementalBack(targetModel, sourceModel, corrIndex);
</#if>
<#if aggregationMappings?has_content>

        // Phase 1d: Aggregation backward (incremental)
        java.util.Map<EObject, EObject> _aggregationIndexBack = CorrespondenceModel.buildAggregationIndex(corrResource);
    <#list aggregationMappings as agg>
        materializeAggregationBack${agg.targetType()}Incremental(targetModel, sourceModel, corrResource, corrIndex, _aggregationIndexBack, _createdBack, _updatedBack);
    </#list>
</#if>
<#if structuralDeduplicationMappings?has_content>

        // Phase 1e: Structural deduplication backward (incremental): sync DAG changes to AST copies
    <#list structuralDeduplicationMappings as sdm>
        _materializeStructuralDedupBack${sdm.abstractTargetType()}Incremental(targetModel, sourceModel, corrResource, corrIndex, _createdBack, _updatedBack);
    </#list>
</#if>
<#if conditionalTypeMappings?has_content>

        // Phase 1f backward: Conditional type mappings (incremental)
        List<EObject[]> _ctmBwIncrDeferredPairs = new ArrayList<>();
        for (EObject _ctmBwTgt : allSourceObjects(targetModel)) {
    <#list conditionalTypeMappings as ctm>
        <#list ctm.branches() as branch>
            <#if branch.targetType()??>
            if (_ctmBwTgt instanceof ${targetPackageName}.${branch.targetType()} _ctmBwTyped
<#if branch.backwardCondition()?? && branch.backwardCondition()?has_content>
                    && (${branch.backwardCondition()})
</#if>
            ) {
<#if syntheticObjectMappings?has_content>
                if (isSyntheticObject(_ctmBwTgt)) continue;
</#if>
                // Option-A type-mismatch fix: if CE entry exists but stored source has wrong type
                // (e.g. column annotations changed branch), remove stale source and CE entry
                // so the new-object branch below creates the correctly-typed replacement.
                if (corrIndex.inverse().containsKey(_ctmBwTgt)) {
                    EObject _ctmBwExisting = corrIndex.inverse().get(_ctmBwTgt);
                    if (!(_ctmBwExisting instanceof ${sourcePackageName}.${ctm.sourceType()})) {
                        EcoreUtil.remove(_ctmBwExisting);
                        CorrespondenceModel.removeEntry(corrResource, _ctmBwExisting);
                        corrIndex.inverse().remove(_ctmBwTgt);
                    }
                }
                if (!corrIndex.inverse().containsKey(_ctmBwTgt)) {
                    // New CTM target object: reconstruct source
                    EObject _ctmBwSrcContainer;
                    <#if branch.backwardParentExpression()?? && branch.backwardParentExpression()?has_content>
                    {
                        EObject target = _ctmBwTgt;
                        java.util.Map<EObject, EObject> objectMapInverse = new java.util.HashMap<>(corrIndex.inverse());
                        Resource _ctmBwSrcRes = sourceModel;
                        _ctmBwSrcContainer = (EObject)(${branch.backwardParentExpression()});
                    }
                    <#else>
                    _ctmBwSrcContainer = corrIndex.inverse().get(_ctmBwTgt.eContainer());
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
                        org.eclipse.emf.ecore.EStructuralFeature _ctmBwFeat =
                            _ctmBwParent.eClass().getEStructuralFeature("${ctm.sourceContainerRef()}");
                        if (_ctmBwFeat != null && _ctmBwFeat.isMany()) {
                            @SuppressWarnings("unchecked")
                            java.util.List<EObject> _ctmBwList =
                                (java.util.List<EObject>) _ctmBwParent.eGet(_ctmBwFeat);
                            _ctmBwList.add(_ctmBwNewSrc);
                        }
                        String _ctmBwSrcCR = _ctmBwNewSrc.eContainmentFeature() != null ? _ctmBwNewSrc.eContainmentFeature().getName() : "";
                        String _ctmBwTgtCR = _ctmBwTgt.eContainmentFeature() != null ? _ctmBwTgt.eContainmentFeature().getName() : "";
                        CorrespondenceModel.addEntry(corrResource,
                                _ctmBwNewSrc, _ctmBwNewSrc.eClass().getName(), computeFingerprint(_ctmBwNewSrc),
                                _ctmBwTgt, _ctmBwTgt.eClass().getName(), computeFingerprintBack(_ctmBwTgt),
                                _ctmBwSrcCR, _ctmBwTgtCR);
                        corrIndex.put(_ctmBwNewSrc, _ctmBwTgt);
                        _createdBack.add(_ctmBwNewSrc);
                        _ctmBwIncrDeferredPairs.add(new EObject[]{_ctmBwNewSrc, _ctmBwTgt});
                    }
                } else {
                    // Known CTM target: check fingerprint and update backward attributes
                    EObject _ctmBwSrcObj = corrIndex.inverse().get(_ctmBwTgt);
                    if (_ctmBwSrcObj instanceof ${sourcePackageName}.${ctm.sourceType()} _ctmBwKnownSrc) {
                        Optional<EObject> _ctmBwEntry = CorrespondenceModel.findBySource(corrResource, _ctmBwSrcObj);
                        if (_ctmBwEntry.isPresent()) {
                            // Re-attach source if detached
                            if (_ctmBwSrcObj.eResource() == null) {
                                String _ctmBwStoredSrcRole = CorrespondenceModel.getSourceContainmentRole(_ctmBwEntry.get());
                                if (_ctmBwStoredSrcRole != null && !_ctmBwStoredSrcRole.isEmpty()) {
                                    EObject _ctmBwTgtParent = _ctmBwTgt.eContainer();
                                    EObject _ctmBwSrcParent = _ctmBwTgtParent != null ? corrIndex.inverse().get(_ctmBwTgtParent) : null;
                                    if (_ctmBwSrcParent != null) {
                                        org.eclipse.emf.ecore.EStructuralFeature _ctmBwRaFeat =
                                            _ctmBwSrcParent.eClass().getEStructuralFeature(_ctmBwStoredSrcRole);
                                        if (_ctmBwRaFeat != null && _ctmBwSrcObj.eContainer() == null) {
                                            if (_ctmBwRaFeat.isMany()) {
                                                @SuppressWarnings("unchecked")
                                                EList<EObject> _ctmBwRaList = (EList<EObject>) _ctmBwSrcParent.eGet(_ctmBwRaFeat);
                                                _ctmBwRaList.add(_ctmBwSrcObj);
                                            } else {
                                                _ctmBwSrcParent.eSet(_ctmBwRaFeat, _ctmBwSrcObj);
                                            }
                                        }
                                    }
                                }
                            }
                            // Check if source's parent container has changed; delete orphaned source if so
                            EObject _ctmBwExpectedParent;
                <#if branch.backwardParentExpression()?? && branch.backwardParentExpression()?has_content>
                            {
                                EObject target = _ctmBwTgt;
                                java.util.Map<EObject, EObject> objectMapInverse = new java.util.HashMap<>(corrIndex.inverse());
                                Resource _ctmBwSrcRes = sourceModel;
                                _ctmBwExpectedParent = (EObject)(${branch.backwardParentExpression()});
                            }
                <#else>
                            _ctmBwExpectedParent = corrIndex.inverse().get(_ctmBwTgt.eContainer());
                </#if>
                            if (_ctmBwExpectedParent != null && !_ctmBwExpectedParent.equals(_ctmBwKnownSrc.eContainer())) {
                                // Parent changed — relocate source object to new parent
                                EcoreUtil.remove(_ctmBwSrcObj);
                                org.eclipse.emf.ecore.EStructuralFeature _ctmBwRelocFeat =
                                    _ctmBwExpectedParent.eClass().getEStructuralFeature("${ctm.sourceContainerRef()}");
                                if (_ctmBwRelocFeat != null) {
                                    if (_ctmBwRelocFeat.isMany()) {
                                        @SuppressWarnings("unchecked")
                                        java.util.List<EObject> _ctmBwRelocList =
                                            (java.util.List<EObject>) _ctmBwExpectedParent.eGet(_ctmBwRelocFeat);
                                        _ctmBwRelocList.add(_ctmBwSrcObj);
                                    } else {
                                        _ctmBwExpectedParent.eSet(_ctmBwRelocFeat, _ctmBwSrcObj);
                                    }
                                }
                                CorrespondenceModel.updateSourceContainmentRole(_ctmBwEntry.get(),
                                    _ctmBwSrcObj.eContainmentFeature() != null ? _ctmBwSrcObj.eContainmentFeature().getName() : "");
                            }
                            String _ctmBwCurrFp = computeFingerprintBack(_ctmBwTgt);
                            String _ctmBwStoredFp = CorrespondenceModel.getTargetFingerprint(_ctmBwEntry.get());
                            if (_ctmBwStoredFp == null || _ctmBwStoredFp.isEmpty() || !_ctmBwCurrFp.equals(_ctmBwStoredFp)) {
                                {
                                    ${targetPackageName}.${branch.targetType()} target = _ctmBwTyped;
                                    ${sourcePackageName}.${ctm.sourceType()} source = _ctmBwKnownSrc;
                                    EObject _ctmBwParent = source.eContainer();
                <#list ctmAttributeMappings as am>
                <#if am.sourceOwnerType() == ctm.sourceType() && am.targetOwnerType() == branch.targetType() && am.backwardExpression()??>
                                    source.set${am.sourceAttr()?cap_first}(${am.backwardExpression()});
                </#if>
                </#list>
                                }
                                CorrespondenceModel.updateTargetFingerprint(_ctmBwEntry.get(), _ctmBwCurrFp);
                                CorrespondenceModel.updateFingerprint(_ctmBwEntry.get(), computeFingerprint(_ctmBwSrcObj));
                                _updatedBack.add(_ctmBwSrcObj);
                                _ctmBwIncrDeferredPairs.add(new EObject[]{_ctmBwSrcObj, _ctmBwTgt});
                            }
                        }
                    }
                }
            }
            </#if>
        </#list>
    </#list>
        }

        // Phase 1f.5 incremental: Deferred backward attribute mappings — re-apply after all CTM objects exist
        // (needed for attributes that cross-reference other CTM-created objects, e.g. EReference.eType)
        for (EObject[] _deferPair : _ctmBwIncrDeferredPairs) {
            EObject _deferSrc = _deferPair[0];
            EObject _deferTgt = _deferPair[1];
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

        // Phase 2: Detect and handle deleted target objects
        // EMF nullifies CE_TARGET_OBJECT when the object is deleted from the resource,
        // so we scan for entries with null targetObject rather than comparing allTargetObjects with corrIndex.
        List<EObject> _deletedBwdEntries = new ArrayList<>(CorrespondenceModel.findDeletedTargetEntries(corrResource));
        // Notify post-processor before actual deletion (CASCADE only)
        if (ctx.getDeletionPolicy() == TransformationContext.DeletionPolicy.CASCADE) {
            List<EObject> _toDeleteBack = new ArrayList<>();
            for (EObject _corrEntry : _deletedBwdEntries) {
                EObject srcObj = CorrespondenceModel.getSourceObject(_corrEntry);
                if (srcObj != null) _toDeleteBack.add(srcObj);
            }
            postProcessor.beforeDeletions(_toDeleteBack);
        }
        for (EObject _corrEntry : _deletedBwdEntries) {
            EObject srcObj = CorrespondenceModel.getSourceObject(_corrEntry);
            switch (ctx.getDeletionPolicy()) {
                case CASCADE -> {
                    if (srcObj != null) {
                        EcoreUtil.delete(srcObj, true);
                    }
                    CorrespondenceModel.removeCorrespondenceEntry(corrResource, _corrEntry);
                }
                case ORPHAN -> CorrespondenceModel.removeCorrespondenceEntry(corrResource, _corrEntry);
                case TOMBSTONE -> CorrespondenceModel.markOrphaned(_corrEntry);
            }
        }

        // Phase 3: Resolve cross-references incrementally (backward direction).
        resolveReferencesIncrementalBack(targetModel, sourceModel, corrIndex);

        CorrespondenceModel.saveAndUpdateTimestamp(corrResource);

        postProcessor.afterTransform(targetModel, sourceModel, corrIndex.inverse(), _createdBack, _updatedBack);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Phase 1: Object Creation and Attribute Mapping (Forward)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Recursively creates target objects for source object tree (containments only).
     * Maps attributes and stores source→target mapping.
     * Pre-existing entries in objectMap (e.g. root elements) are traversed but not recreated.
     */
