    public static SyncResult sync(Resource source, Resource target, Resource corrModel) {
        return sync(source, target, corrModel,
                SyncConflictPolicy.SOURCE_WINS,
                TransformationContext.DeletionPolicy.TOMBSTONE,
                Options.defaults(),
                PostProcessor.NOOP, PostProcessor.NOOP);
    }

    public static SyncResult sync(
            Resource source,
            Resource target,
            Resource corrModel,
            SyncConflictPolicy conflictPolicy,
            TransformationContext.DeletionPolicy deletionPolicy,
            Options options) {
        return sync(source, target, corrModel, conflictPolicy, deletionPolicy, options,
                PostProcessor.NOOP, PostProcessor.NOOP);
    }

    /**
     * Synchronises source and target using separate post-processors per direction.
     * forwardPostProcessor.afterTransform is called with newly created/updated target objects.
     * backwardPostProcessor.afterTransform is called with newly created/updated source objects.
     * Note: beforeDeletions is not invoked by sync() — objects are deleted externally
     * before sync() is called; their absence is detected via null corrEntry references.
     */
    @SuppressWarnings("unchecked")
    public static SyncResult sync(
            Resource source,
            Resource target,
            Resource corrModel,
            SyncConflictPolicy conflictPolicy,
            TransformationContext.DeletionPolicy deletionPolicy,
            Options options,
            PostProcessor forwardPostProcessor,
            PostProcessor backwardPostProcessor) {

        com.google.common.collect.BiMap<EObject, EObject> corrIndex = CorrespondenceModel.buildIndex(corrModel);
        int _updFwd = 0, _updBwd = 0, _crFwd = 0, _crBwd = 0, _del = 0, _linked = 0;
        java.util.List<SyncConflict> _conflicts = new java.util.ArrayList<>();
        java.util.List<EObject> _fwdCreated = new java.util.ArrayList<>();
        java.util.List<EObject> _fwdUpdated = new java.util.ArrayList<>();
        java.util.List<EObject> _bwdCreated = new java.util.ArrayList<>();
        java.util.List<EObject> _bwdUpdated = new java.util.ArrayList<>();

        // Snapshot to avoid ConcurrentModificationException while removing Partition 4 entries.
        java.util.List<EObject> _allEntries = CorrespondenceModel.getAllEntries(corrModel);

        // ── Schritt 1: Partition 1 (src ≠ null, tgt ≠ null) ─────────────────
        for (EObject _ce : _allEntries) {
            EObject _srcObj = CorrespondenceModel.getSourceObject(_ce);
            EObject _tgtObj = CorrespondenceModel.getTargetObject(_ce);
            if (_srcObj == null || _tgtObj == null) continue;

            String _storedSrcFp = CorrespondenceModel.getFingerprint(_ce);
            String _storedTgtFp = CorrespondenceModel.getTargetFingerprint(_ce);
            if (_storedSrcFp == null) _storedSrcFp = "";
            if (_storedTgtFp == null) _storedTgtFp = "";

            String _curSrcFp = computeFingerprint(_srcObj);
            String _curTgtFp = computeFingerprintBack(_tgtObj);
            boolean _srcChg = !_curSrcFp.equals(_storedSrcFp);
            boolean _tgtChg = !_curTgtFp.equals(_storedTgtFp);

            if (!_srcChg && !_tgtChg) continue; // Fall D: nothing to do

            if (!isCoveredByTypeMappingSource(_srcObj)) {
                // Role-based type: steer mapRoleBasedTypesIncremental/Back via fingerprint neutralisation.
                // computeFingerprint() above omits the family prefix and therefore always differs
                // from the composite stored FP — use the composite format here for accurate conflict detection.
<#if roleBasedTypeMappingModels?has_content>
                boolean _rbSrcChg = !computeRoleBasedSourceFingerprint(_srcObj).equals(_storedSrcFp)
                        && !computeFingerprint(_srcObj).equals(_storedSrcFp);
<#else>
                boolean _rbSrcChg = _srcChg;
</#if>
                if (_rbSrcChg && _tgtChg) {
                    switch (conflictPolicy) {
                        case SOURCE_WINS ->
                            // Neutralise backward: stored tgtFp ← current tgtFp
                            CorrespondenceModel.updateTargetFingerprint(_ce, _curTgtFp);
                        case TARGET_WINS ->
                            // Neutralise forward: stored srcFp ← current srcFp
                            CorrespondenceModel.updateFingerprint(_ce, _curSrcFp);
                        case LOG_AND_SKIP -> {
                            CorrespondenceModel.updateFingerprint(_ce, _curSrcFp);
                            CorrespondenceModel.updateTargetFingerprint(_ce, _curTgtFp);
                            _conflicts.add(new SyncConflict(_srcObj, _tgtObj,
                                    _srcObj.eClass().getName(), _tgtObj.eClass().getName(),
                                    _storedSrcFp, _curSrcFp, _storedTgtFp, _curTgtFp));
                        }
                    }
                }
                // Falls A and B are handled naturally by mapRoleBasedTypesIncremental/Back.
                continue;
            }

            // TypeMapping type: handle inline.
            if (_srcChg && !_tgtChg) {
                // Fall A: source changed → forward propagate
                updateTargetAttributes(_srcObj, _tgtObj, options);
                CorrespondenceModel.updateFingerprint(_ce, _curSrcFp);
                CorrespondenceModel.updateTargetFingerprint(_ce, computeFingerprintBack(_tgtObj));
                _updFwd++; _fwdUpdated.add(_tgtObj);
            } else if (!_srcChg && _tgtChg) {
                // Fall B: target changed → backward propagate
                updateSourceAttributes(_tgtObj, _srcObj, options);
                CorrespondenceModel.updateTargetFingerprint(_ce, _curTgtFp);
                CorrespondenceModel.updateFingerprint(_ce, computeFingerprint(_srcObj));
                _updBwd++; _bwdUpdated.add(_srcObj);
            } else {
                // Fall C: conflict
                switch (conflictPolicy) {
                    case SOURCE_WINS -> {
                        updateTargetAttributes(_srcObj, _tgtObj, options);
                        CorrespondenceModel.updateFingerprint(_ce, _curSrcFp);
                        CorrespondenceModel.updateTargetFingerprint(_ce, computeFingerprintBack(_tgtObj));
                        _updFwd++; _fwdUpdated.add(_tgtObj);
                    }
                    case TARGET_WINS -> {
                        updateSourceAttributes(_tgtObj, _srcObj, options);
                        CorrespondenceModel.updateTargetFingerprint(_ce, _curTgtFp);
                        CorrespondenceModel.updateFingerprint(_ce, computeFingerprint(_srcObj));
                        _updBwd++; _bwdUpdated.add(_srcObj);
                    }
                    case LOG_AND_SKIP -> _conflicts.add(new SyncConflict(_srcObj, _tgtObj,
                            _srcObj.eClass().getName(), _tgtObj.eClass().getName(),
                            _storedSrcFp, _curSrcFp, _storedTgtFp, _curTgtFp));
                }
            }
        }

        // ── Schritt 2: Partition 2 (src ≠ null, tgt = null) — TypeMapping ──
        for (EObject _ce : _allEntries) {
            EObject _srcObj = CorrespondenceModel.getSourceObject(_ce);
            if (_srcObj == null || CorrespondenceModel.getTargetObject(_ce) != null) continue;
            if (!isCoveredByTypeMappingSource(_srcObj)) continue;
            EObject _newTgt = createNewTargetObject(_srcObj, options);
            if (_newTgt != null) {
                addToTargetContainment(_srcObj, _newTgt, target, corrIndex);
                String _tgtCR = _newTgt.eContainmentFeature() != null ? _newTgt.eContainmentFeature().getName() : "";
                CorrespondenceModel.updateTargetObject(_ce, _newTgt, _newTgt.eClass().getName());
                CorrespondenceModel.updateTargetFingerprint(_ce, computeFingerprintBack(_newTgt));
                CorrespondenceModel.updateTargetContainmentRole(_ce, _tgtCR);
                corrIndex.put(_srcObj, _newTgt);
                _crFwd++; _fwdCreated.add(_newTgt);
            }
        }

        // ── Schritt 3: Partition 3 (src = null, tgt ≠ null) — TypeMapping ──
        for (EObject _ce : _allEntries) {
            EObject _tgtObj = CorrespondenceModel.getTargetObject(_ce);
            if (_tgtObj == null || CorrespondenceModel.getSourceObject(_ce) != null) continue;
            if (!isCoveredByTypeMappingTarget(_tgtObj)) continue;
            EObject _newSrc = createNewSourceObject(_tgtObj, options);
            if (_newSrc != null) {
                addToSourceContainment(_tgtObj, _newSrc, source, corrIndex);
                String _srcCR = _newSrc.eContainmentFeature() != null ? _newSrc.eContainmentFeature().getName() : "";
                CorrespondenceModel.updateSourceObject(_ce, _newSrc, _newSrc.eClass().getName());
                CorrespondenceModel.updateFingerprint(_ce, computeFingerprint(_newSrc));
                CorrespondenceModel.updateSourceContainmentRole(_ce, _srcCR);
                corrIndex.put(_newSrc, _tgtObj);
                _crBwd++; _bwdCreated.add(_newSrc);
            }
        }
<#if roleBasedTypeMappingModels?has_content>
        // Partition 3 role-based (src=null, tgt≠null): source was deleted → propagate to target.
        // Removes the stale corr entry so mapRoleBasedTypesIncrementalBack does not re-create
        // the deleted source object.
        for (EObject _ce : _allEntries) {
            EObject _tgtObj = CorrespondenceModel.getTargetObject(_ce);
            if (_tgtObj == null || CorrespondenceModel.getSourceObject(_ce) != null) continue;
            if (!isCoveredByRoleBasedTarget(_tgtObj)) continue;
            if (deletionPolicy == TransformationContext.DeletionPolicy.CASCADE) {
                EcoreUtil.delete(_tgtObj, true);
            }
            CorrespondenceModel.removeCorrespondenceEntry(corrModel, _ce);
            _del++;
        }
</#if>

        // ── Schritt 2: Partition 2 (src ≠ null, tgt = null) — role-based ──
<#if roleBasedTypeMappingModels?has_content>
        // Partition 2 role-based (src≠null, tgt=null): target was deleted → propagate to source.
        // Removes the stale corr entry so mapRoleBasedTypesIncremental does not re-create
        // the deleted target object.
        for (EObject _ce : _allEntries) {
            EObject _srcObj = CorrespondenceModel.getSourceObject(_ce);
            if (_srcObj == null || CorrespondenceModel.getTargetObject(_ce) != null) continue;
            if (!isCoveredByRoleBasedSource(_srcObj)) continue;
            if (deletionPolicy == TransformationContext.DeletionPolicy.CASCADE) {
                EcoreUtil.delete(_srcObj, true);
            }
            CorrespondenceModel.removeCorrespondenceEntry(corrModel, _ce);
            _del++;
        }
</#if>

        // ── Schritt 4: Partition 4 (both null) ───────────────────────────────
        for (EObject _ce : _allEntries) {
            if (CorrespondenceModel.getSourceObject(_ce) == null
                    && CorrespondenceModel.getTargetObject(_ce) == null) {
                CorrespondenceModel.removeCorrespondenceEntry(corrModel, _ce);
                _del++;
            }
        }

        // ── Schritt 5a: Fuzzy-Match ──────────────────────────────────────────
        // Versuche, ungematchte Objekte beider Seiten durch Fingerprint-Vergleich
        // zu verbinden, bevor neue Objekte erstellt werden.
        // Vorwärts: Index ungematchter Zielobjekte nach Backward-Fingerprint.
        {
            java.util.Map<String, EObject> _unmatchedTgtByFP = new java.util.HashMap<>();
<#if roleBasedTypeMappingModels?has_content>
            java.util.Map<String, EObject> _unmatchedTgtByNameFP = new java.util.HashMap<>();
</#if>
            for (EObject _obj : allSourceObjects(target)) {
                if (!corrIndex.containsValue(_obj)) {
                    String _fp = computeFingerprintBack(_obj);
                    if (_fp != null) _unmatchedTgtByFP.putIfAbsent(_fp, _obj);
<#if roleBasedTypeMappingModels?has_content>
                    String _nameFp = computeNameOnlyFingerprintBack(_obj);
                    if (_nameFp != null) _unmatchedTgtByNameFP.putIfAbsent(_nameFp, _obj);
</#if>
                }
            }
            for (EObject _srcObj : allSourceObjects(source)) {
                if (corrIndex.containsKey(_srcObj)) continue;
                String _eFP = computeExpectedTargetFingerprint(_srcObj);
                boolean _fwdMatched = false;
                if (_eFP != null && _unmatchedTgtByFP.containsKey(_eFP)) {
                    EObject _tgtObj = _unmatchedTgtByFP.remove(_eFP);
                    String _srcCR = _srcObj.eContainmentFeature() != null ? _srcObj.eContainmentFeature().getName() : "";
                    String _tgtCR = _tgtObj.eContainmentFeature() != null ? _tgtObj.eContainmentFeature().getName() : "";
                    CorrespondenceModel.addEntry(corrModel,
                            _srcObj, _srcObj.eClass().getName(), computeFingerprint(_srcObj),
                            _tgtObj, _tgtObj.eClass().getName(), computeFingerprintBack(_tgtObj), _srcCR, _tgtCR);
                    corrIndex.put(_srcObj, _tgtObj);
                    _linked++;
                    _fwdMatched = true;
                }
<#if roleBasedTypeMappingModels?has_content>
                // Fallback: for role-based types, try name-only match to handle concurrently
                // created target objects that have target-only attributes set (e.g. birthday)
                // or lack the container-name prefix.
                if (!_fwdMatched) {
                    String _altFP = computeAlternativeTargetFingerprint(_srcObj);
                    if (_altFP != null && _unmatchedTgtByNameFP.containsKey(_altFP)) {
                        EObject _tgtObj = _unmatchedTgtByNameFP.remove(_altFP);
                        _unmatchedTgtByFP.values().remove(_tgtObj);
                        String _srcCR = _srcObj.eContainmentFeature() != null ? _srcObj.eContainmentFeature().getName() : "";
                        String _tgtCR = _tgtObj.eContainmentFeature() != null ? _tgtObj.eContainmentFeature().getName() : "";
                        CorrespondenceModel.addEntry(corrModel,
                                _srcObj, _srcObj.eClass().getName(), computeFingerprint(_srcObj),
                                _tgtObj, _tgtObj.eClass().getName(), computeFingerprintBack(_tgtObj), _srcCR, _tgtCR);
                        corrIndex.put(_srcObj, _tgtObj);
                        _linked++;
                    }
                }
</#if>
            }
            // Rückwärts: Index ungematchter Quellobjekte nach Forward-Fingerprint.
            java.util.Map<String, EObject> _unmatchedSrcByFP = new java.util.HashMap<>();
            for (EObject _obj : allSourceObjects(source)) {
                if (!corrIndex.containsKey(_obj)) {
                    String _fp = computeFingerprint(_obj);
                    if (_fp != null) _unmatchedSrcByFP.putIfAbsent(_fp, _obj);
                }
            }
            for (EObject _tgtObj : allSourceObjects(target)) {
                if (corrIndex.containsValue(_tgtObj)) continue;
                String _eFP = computeExpectedSourceFingerprint(_tgtObj);
                if (_eFP != null && _unmatchedSrcByFP.containsKey(_eFP)) {
                    EObject _srcObj = _unmatchedSrcByFP.remove(_eFP);
                    String _srcCR = _srcObj.eContainmentFeature() != null ? _srcObj.eContainmentFeature().getName() : "";
                    String _tgtCR = _tgtObj.eContainmentFeature() != null ? _tgtObj.eContainmentFeature().getName() : "";
                    CorrespondenceModel.addEntry(corrModel,
                            _srcObj, _srcObj.eClass().getName(), computeFingerprint(_srcObj),
                            _tgtObj, _tgtObj.eClass().getName(), computeFingerprintBack(_tgtObj), _srcCR, _tgtCR);
                    corrIndex.put(_srcObj, _tgtObj);
                    _linked++;
                }
            }
        }

        // ── Schritt 5: New objects without corrEntry — TypeMapping ───────────
        for (EObject _srcObj : allSourceObjects(source)) {
            if (!isCoveredByTypeMappingSource(_srcObj)) continue;
            if (corrIndex.containsKey(_srcObj)) continue;
            EObject _newTgt = createNewTargetObject(_srcObj, options);
            if (_newTgt != null) {
                addToTargetContainment(_srcObj, _newTgt, target, corrIndex);
                String _srcCR = _srcObj.eContainmentFeature() != null ? _srcObj.eContainmentFeature().getName() : "";
                String _tgtCR = _newTgt.eContainmentFeature() != null ? _newTgt.eContainmentFeature().getName() : "";
                CorrespondenceModel.addEntry(corrModel,
                        _srcObj, _srcObj.eClass().getName(), computeFingerprint(_srcObj),
                        _newTgt, _newTgt.eClass().getName(), computeFingerprintBack(_newTgt), _srcCR, _tgtCR);
                corrIndex.put(_srcObj, _newTgt);
                _crFwd++; _fwdCreated.add(_newTgt);
            }
        }
        for (EObject _tgtObj : allSourceObjects(target)) {
            if (!isCoveredByTypeMappingTarget(_tgtObj)) continue;
            if (corrIndex.inverse().containsKey(_tgtObj)) continue;
            EObject _newSrc = createNewSourceObject(_tgtObj, options);
            if (_newSrc != null) {
                addToSourceContainment(_tgtObj, _newSrc, source, corrIndex);
                String _srcCR = _newSrc.eContainmentFeature() != null ? _newSrc.eContainmentFeature().getName() : "";
                String _tgtCR = _tgtObj.eContainmentFeature() != null ? _tgtObj.eContainmentFeature().getName() : "";
                CorrespondenceModel.addEntry(corrModel,
                        _newSrc, _newSrc.eClass().getName(), computeFingerprint(_newSrc),
                        _tgtObj, _tgtObj.eClass().getName(), computeFingerprintBack(_tgtObj), _srcCR, _tgtCR);
                corrIndex.put(_newSrc, _tgtObj);
                _crBwd++; _bwdCreated.add(_newSrc);
            }
        }

<#if roleBasedTypeMappingModels?has_content>
        // ── Schritt 1b+2+3+5 for role-based types ───────────────────────────
        // Fingerprint neutralisation in Schritt 1 ensures that the appropriate
        // direction fires for each entry (SOURCE_WINS / TARGET_WINS / LOG_AND_SKIP).
        java.util.List<EObject> _srcRoots = source.getContents();
        java.util.List<EObject> _tgtRoots = target.getContents();
        for (int _i = 0; _i < Math.min(_srcRoots.size(), _tgtRoots.size()); _i++) {
            mapRoleBasedTypesIncremental(_srcRoots.get(_i), _tgtRoots.get(_i), corrModel, corrIndex, options);
            mapRoleBasedTypesIncrementalBack(_tgtRoots.get(_i), _srcRoots.get(_i), corrModel, corrIndex, options);
        }
</#if>

        // ── Phase 1c: Edge materialization ──────────────────────────────────
<#if edgeMaterializationMappings?has_content>
        materializeEdgesIncremental(source, target, corrIndex);
        materializeEdgesIncrementalBack(target, source, corrIndex);
</#if>

<#if aggregationMappings?has_content>
        // ── Aggregation: forward + backward for concurrently added elements ──
        // New source elements (no corr entry) are grouped and propagated forward;
        // new target elements (no corr entry) are expanded and propagated backward.
<#list aggregationMappings as agg>
        {
            java.util.List<EObject> _aggFwdCreated = new java.util.ArrayList<>();
            java.util.List<EObject> _aggFwdUpdated = new java.util.ArrayList<>();
            java.util.Map<EObject, EObject> _aggFwdIdx = CorrespondenceModel.buildAggregationIndex(corrModel);
            materializeAggregation${agg.sourceType()}Incremental(
                    source, target, corrModel, corrIndex, _aggFwdIdx, _aggFwdCreated, _aggFwdUpdated);
            _fwdCreated.addAll(_aggFwdCreated); _crFwd += _aggFwdCreated.size();
            _fwdUpdated.addAll(_aggFwdUpdated); _updFwd += _aggFwdUpdated.size();

            java.util.List<EObject> _aggBwdCreated = new java.util.ArrayList<>();
            java.util.List<EObject> _aggBwdUpdated = new java.util.ArrayList<>();
            java.util.Map<EObject, EObject> _aggBwdIdx = CorrespondenceModel.buildAggregationIndex(corrModel);
            materializeAggregationBack${agg.targetType()}Incremental(
                    target, source, corrModel, corrIndex, _aggBwdIdx, _aggBwdCreated, _aggBwdUpdated);
            _bwdCreated.addAll(_aggBwdCreated); _crBwd += _aggBwdCreated.size();
            _bwdUpdated.addAll(_aggBwdUpdated); _updBwd += _aggBwdUpdated.size();
        }
</#list>
</#if>

        // ── Schritt 6: Cross-references ──────────────────────────────────────
        // Rebuild corrIndex after all structural changes.
        corrIndex = CorrespondenceModel.buildIndex(corrModel);
<#assign syncHasCrossRefs = false>
<#list referenceMappings as refMapping>
    <#if !refMapping.sourceIsContainment() && !refMapping.sourceIsEOpposite()>
        <#assign syncHasCrossRefs = true>
    </#if>
</#list>
<#if syncHasCrossRefs>
        switch (conflictPolicy) {
            case SOURCE_WINS, LOG_AND_SKIP -> resolveReferencesIncremental(source, target, corrIndex);
            case TARGET_WINS -> resolveReferencesIncrementalBack(target, source, corrIndex);
        }
</#if>

        CorrespondenceModel.saveAndUpdateTimestamp(corrModel);

        // Post-processing: rebuild corrIndex so hooks see final cross-reference state.
        com.google.common.collect.BiMap<EObject, EObject> _finalIndex = CorrespondenceModel.buildIndex(corrModel);
        forwardPostProcessor.afterTransform(source, target, _finalIndex, _fwdCreated, _fwdUpdated);
        backwardPostProcessor.afterTransform(target, source, _finalIndex.inverse(), _bwdCreated, _bwdUpdated);

        return new SyncResult(_updFwd, _updBwd, _crFwd, _crBwd, _del, _linked, _conflicts);
    }

