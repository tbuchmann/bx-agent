<#if aggregationMappings?has_content>
<#list aggregationMappings as agg>
    // ── Aggregation: ${agg.sourceType()} (${agg.sourcePackageName!sourcePackageName}) → ${agg.targetType()} ──────────────────────────

    /**
     * Phase 1d forward (batch): groups sourceType instances by ${agg.groupBySourceAttr()},
     * creates one target Element per distinct value with multiplicity = group size.
     */
    private static void materializeAggregation${agg.sourceType()}(
            ${sourcePackageName}.${agg.sourceContainerType()} sourceParent,
            ${targetPackageName}.${agg.targetContainerType()} targetParent,
            Map<EObject, EObject> aggObjectMap) {
        java.util.Map<String, java.util.List<${sourcePackageName}.${agg.sourceType()}>> _groups = new java.util.LinkedHashMap<>();
        for (${sourcePackageName}.${agg.sourceType()} _srcElem : sourceParent.get${agg.sourceContainerRef()?cap_first}()) {
            _groups.computeIfAbsent(_srcElem.get${agg.groupBySourceAttr()?cap_first}(), k -> new java.util.ArrayList<>()).add(_srcElem);
        }
        for (java.util.Map.Entry<String, java.util.List<${sourcePackageName}.${agg.sourceType()}>> _entry : _groups.entrySet()) {
            ${targetPackageName}.${agg.targetType()} _tgtElem = ${targetFactory}.eINSTANCE.create${agg.targetType()}();
            _tgtElem.set${agg.groupByTargetAttr()?cap_first}(_entry.getKey());
            _tgtElem.set${agg.countTargetAttr()?cap_first}(_entry.getValue().size());
            targetParent.get${agg.targetContainerRef()?cap_first}().add(_tgtElem);
            for (${sourcePackageName}.${agg.sourceType()} _srcElem : _entry.getValue()) {
                aggObjectMap.put(_srcElem, _tgtElem);
            }
        }
    }

    /**
     * Phase 1d backward (batch): for each target Element, creates multiplicity many source
     * Elements with groupBySourceAttr set; leaves all other source attrs at defaults.
     */
    private static void materializeAggregationBack${agg.targetType()}(
            ${targetPackageName}.${agg.targetContainerType()} targetParent,
            ${sourcePackageName}.${agg.sourceContainerType()} sourceParent,
            Map<EObject, EObject> aggObjectMap) {
        for (${targetPackageName}.${agg.targetType()} _tgtElem : targetParent.get${agg.targetContainerRef()?cap_first}()) {
            for (int _i = 0; _i < _tgtElem.get${agg.countTargetAttr()?cap_first}(); _i++) {
                ${sourcePackageName}.${agg.sourceType()} _srcElem = ${sourceFactory}.eINSTANCE.create${agg.sourceType()}();
                _srcElem.set${agg.groupBySourceAttr()?cap_first}(_tgtElem.get${agg.groupByTargetAttr()?cap_first}());
                sourceParent.get${agg.sourceContainerRef()?cap_first}().add(_srcElem);
                aggObjectMap.put(_srcElem, _tgtElem);
            }
        }
    }

    /**
     * Phase 1d forward (incremental): smart-sync aggregation groups.
     * Preserves existing target elements when the group key and count are unchanged.
     * Handles element additions, deletions, and value changes (group moves).
     */
    @SuppressWarnings("unchecked")
    private static void materializeAggregation${agg.sourceType()}Incremental(
            Resource sourceModel, Resource existingTarget,
            Resource corrResource,
            com.google.common.collect.BiMap<EObject, EObject> corrIndex,
            java.util.Map<EObject, EObject> aggregationIndex,
            List<EObject> _created, List<EObject> _updated) {

        // Step 1: build current groups, organized by (sourceParent, groupByValue)
        java.util.Map<${sourcePackageName}.${agg.sourceContainerType()},
                java.util.Map<String, java.util.List<${sourcePackageName}.${agg.sourceType()}>>> _groupsByParent
                = new java.util.LinkedHashMap<>();
        for (EObject _obj : allSourceObjects(sourceModel)) {
            if (_obj instanceof ${sourcePackageName}.${agg.sourceType()} _srcElem
                    && _srcElem.eContainer() instanceof ${sourcePackageName}.${agg.sourceContainerType()} _srcBag) {
                _groupsByParent.computeIfAbsent(_srcBag, k -> new java.util.LinkedHashMap<>())
                    .computeIfAbsent(_srcElem.get${agg.groupBySourceAttr()?cap_first}(), k -> new java.util.ArrayList<>())
                    .add(_srcElem);
            }
        }

        // Step 2: index existing target elements by (targetParent, groupByValue)
        java.util.Map<${targetPackageName}.${agg.targetContainerType()},
                java.util.Map<String, ${targetPackageName}.${agg.targetType()}>> _existingByParent
                = new java.util.LinkedHashMap<>();
        for (EObject _obj : allSourceObjects(existingTarget)) {
            if (_obj instanceof ${targetPackageName}.${agg.targetType()} _tgtElem
                    && _tgtElem.eContainer() instanceof ${targetPackageName}.${agg.targetContainerType()} _tgtBag) {
                _existingByParent.computeIfAbsent(_tgtBag, k -> new java.util.LinkedHashMap<>())
                    .put(_tgtElem.get${agg.groupByTargetAttr()?cap_first}(), _tgtElem);
            }
        }

        // Step 3: process each current group
        java.util.Set<${targetPackageName}.${agg.targetType()}> _processedTargets = new java.util.HashSet<>();
        for (java.util.Map.Entry<${sourcePackageName}.${agg.sourceContainerType()},
                java.util.Map<String, java.util.List<${sourcePackageName}.${agg.sourceType()}>>> _parentEntry
                : _groupsByParent.entrySet()) {

            ${sourcePackageName}.${agg.sourceContainerType()} _srcBag = _parentEntry.getKey();
            EObject _tgtBagObj = corrIndex.get(_srcBag);
            if (!(_tgtBagObj instanceof ${targetPackageName}.${agg.targetContainerType()} _tgtBag)) continue;

            java.util.Map<String, ${targetPackageName}.${agg.targetType()}> _existingInParent =
                _existingByParent.getOrDefault(_tgtBag, java.util.Collections.emptyMap());

            for (java.util.Map.Entry<String, java.util.List<${sourcePackageName}.${agg.sourceType()}>> _groupEntry
                    : _parentEntry.getValue().entrySet()) {
                String _value = _groupEntry.getKey();
                java.util.List<${sourcePackageName}.${agg.sourceType()}> _srcElems = _groupEntry.getValue();
                int _expectedCount = _srcElems.size();

                ${targetPackageName}.${agg.targetType()} _tgtElem = _existingInParent.get(_value);
                if (_tgtElem != null) {
                    // Existing group: update multiplicity if changed
                    _processedTargets.add(_tgtElem);
                    if (_tgtElem.get${agg.countTargetAttr()?cap_first}() != _expectedCount) {
                        _tgtElem.set${agg.countTargetAttr()?cap_first}(_expectedCount);
                        String _newTgtFp = computeFingerprintBack(_tgtElem);
                        CorrespondenceModel.updateAllAggregationTargetFingerprints(corrResource, _tgtElem, _newTgtFp);
                        _updated.add(_tgtElem);
                    }
                    // Ensure all current source elements have correct corr entries
                    for (${sourcePackageName}.${agg.sourceType()} _srcElem : _srcElems) {
                        EObject _indexedTarget = aggregationIndex.get(_srcElem);
                        if (_indexedTarget == null) {
                            // New source element in existing group
                            String _srcFp = computeFingerprint(_srcElem);
                            String _tgtFp = computeFingerprintBack(_tgtElem);
                            CorrespondenceModel.addAggregationEntry(corrResource, _srcElem, "${agg.sourceType()}", _srcFp, _tgtElem, "${agg.targetType()}", _tgtFp);
                            aggregationIndex.put(_srcElem, _tgtElem);
                        } else if (_indexedTarget != _tgtElem) {
                            // Source element moved from another group (value changed)
                            Optional<EObject> _oldCE = CorrespondenceModel.findBySource(corrResource, _srcElem);
                            _oldCE.ifPresent(ce -> {
                                CorrespondenceModel.updateTargetObject(ce, _tgtElem, "${agg.targetType()}");
                                CorrespondenceModel.updateFingerprint(ce, computeFingerprint(_srcElem));
                                CorrespondenceModel.updateTargetFingerprint(ce, computeFingerprintBack(_tgtElem));
                            });
                            aggregationIndex.put(_srcElem, _tgtElem);
                        }
                        // else: already correctly indexed for this target
                    }
                } else {
                    // Check if all source elements came from the same old target (whole-group rename → update in place)
                    EObject _commonOldTarget = null;
                    boolean _allFromSameOldTarget = !_srcElems.isEmpty();
                    for (${sourcePackageName}.${agg.sourceType()} _srcElem : _srcElems) {
                        EObject _idx = aggregationIndex.get(_srcElem);
                        if (_idx == null) { _allFromSameOldTarget = false; break; }
                        if (_commonOldTarget == null) _commonOldTarget = _idx;
                        else if (_commonOldTarget != _idx) { _allFromSameOldTarget = false; break; }
                    }

                    // Whole-group rename only if ALL sources of the old target moved to this new group.
                    ${targetPackageName}.${agg.targetType()} _reuseTarget = null;
                    if (_allFromSameOldTarget && !_processedTargets.contains(_commonOldTarget)
                            && _commonOldTarget instanceof ${targetPackageName}.${agg.targetType()} _candidate) {
                        long _oldSrcCount = aggregationIndex.values().stream().filter(v -> v == _candidate).count();
                        if (_oldSrcCount == (long) _srcElems.size()) _reuseTarget = _candidate;
                    }
                    if (_reuseTarget != null) {
                        ${targetPackageName}.${agg.targetType()} _oldTgt = _reuseTarget;
                        // Whole-group rename: update existing target element in place (least-change)
                        _oldTgt.set${agg.groupByTargetAttr()?cap_first}(_value);
                        if (_oldTgt.get${agg.countTargetAttr()?cap_first}() != _expectedCount) {
                            _oldTgt.set${agg.countTargetAttr()?cap_first}(_expectedCount);
                        }
                        _processedTargets.add(_oldTgt);
                        String _newTgtFp = computeFingerprintBack(_oldTgt);
                        CorrespondenceModel.updateAllAggregationTargetFingerprints(corrResource, _oldTgt, _newTgtFp);
                        for (${sourcePackageName}.${agg.sourceType()} _srcElem : _srcElems) {
                            String _newSrcFp = computeFingerprint(_srcElem);
                            Optional<EObject> _ceOpt = CorrespondenceModel.findBySource(corrResource, _srcElem);
                            _ceOpt.ifPresent(ce -> {
                                CorrespondenceModel.updateFingerprint(ce, _newSrcFp);
                                CorrespondenceModel.updateTargetFingerprint(ce, _newTgtFp);
                            });
                            aggregationIndex.put(_srcElem, _oldTgt);
                        }
                        _updated.add(_oldTgt);
                    } else {
                        // True new group: create target element
                        ${targetPackageName}.${agg.targetType()} _newTgt = ${targetFactory}.eINSTANCE.create${agg.targetType()}();
                        _newTgt.set${agg.groupByTargetAttr()?cap_first}(_value);
                        _newTgt.set${agg.countTargetAttr()?cap_first}(_expectedCount);
                        _tgtBag.get${agg.targetContainerRef()?cap_first}().add(_newTgt);
                        _processedTargets.add(_newTgt);
                        String _tgtFp = computeFingerprintBack(_newTgt);
                        for (${sourcePackageName}.${agg.sourceType()} _srcElem : _srcElems) {
                            EObject _indexedTarget = aggregationIndex.get(_srcElem);
                            if (_indexedTarget != null && _indexedTarget != _newTgt) {
                                // Element moved from another group
                                Optional<EObject> _oldCE = CorrespondenceModel.findBySource(corrResource, _srcElem);
                                _oldCE.ifPresent(ce -> {
                                    CorrespondenceModel.updateTargetObject(ce, _newTgt, "${agg.targetType()}");
                                    CorrespondenceModel.updateFingerprint(ce, computeFingerprint(_srcElem));
                                    CorrespondenceModel.updateTargetFingerprint(ce, computeFingerprintBack(_newTgt));
                                });
                            } else if (_indexedTarget == null) {
                                String _srcFp = computeFingerprint(_srcElem);
                                CorrespondenceModel.addAggregationEntry(corrResource, _srcElem, "${agg.sourceType()}", _srcFp, _newTgt, "${agg.targetType()}", _tgtFp);
                            }
                            aggregationIndex.put(_srcElem, _newTgt);
                        }
                        _created.add(_newTgt);
                    }
                }
            }

            // Step 4: delete target elements whose group no longer exists in source
            for (${targetPackageName}.${agg.targetType()} _existingTgt : _existingInParent.values()) {
                if (!_processedTargets.contains(_existingTgt)) {
                    EcoreUtil.delete(_existingTgt, true);
                }
            }
        }

        // Step 5: clean up stale aggregation CEs (source element was deleted → null in corr)
        for (EObject _entry : new java.util.ArrayList<>(CorrespondenceModel.findDeletedAggregationSourceEntries(corrResource))) {
            CorrespondenceModel.removeCorrespondenceEntry(corrResource, _entry);
        }
    }

    /**
     * Phase 1d backward (incremental): syncs source element count to target multiplicity.
     * Creates new source elements when multiplicity increases; deletes when it decreases.
     */
    @SuppressWarnings("unchecked")
    private static void materializeAggregationBack${agg.targetType()}Incremental(
            Resource targetModel, Resource sourceModel,
            Resource corrResource,
            com.google.common.collect.BiMap<EObject, EObject> corrIndex,
            java.util.Map<EObject, EObject> aggregationIndex,
            List<EObject> _created, List<EObject> _updated) {

        // Build reverse index: target element → list of existing source elements
        java.util.Map<EObject, java.util.List<EObject>> _reverseIdx =
            CorrespondenceModel.buildReverseAggregationIndex(corrResource);

        for (EObject _obj : allSourceObjects(targetModel)) {
            if (!(_obj instanceof ${targetPackageName}.${agg.targetType()} _tgtElem)) continue;
            EObject _tgtBagObj = _tgtElem.eContainer();
            if (!(_tgtBagObj instanceof ${targetPackageName}.${agg.targetContainerType()} _tgtBag)) continue;
            EObject _srcBagObj = corrIndex.inverse().get(_tgtBag);
            if (!(_srcBagObj instanceof ${sourcePackageName}.${agg.sourceContainerType()} _srcBag)) continue;

            int _expectedCount = _tgtElem.get${agg.countTargetAttr()?cap_first}();
            String _value = _tgtElem.get${agg.groupByTargetAttr()?cap_first}();
            java.util.List<EObject> _existing = _reverseIdx.getOrDefault(_tgtElem, new java.util.ArrayList<>());
            int _currentCount = _existing.size();

            // Update groupBySourceAttr on existing source elements if value changed
            for (EObject _srcElemObj : _existing) {
                if (_srcElemObj instanceof ${sourcePackageName}.${agg.sourceType()} _srcElem) {
                    if (!_value.equals(_srcElem.get${agg.groupBySourceAttr()?cap_first}())) {
                        _srcElem.set${agg.groupBySourceAttr()?cap_first}(_value);
                        Optional<EObject> _ceOpt = CorrespondenceModel.findBySource(corrResource, _srcElem);
                        _ceOpt.ifPresent(ce -> {
                            CorrespondenceModel.updateFingerprint(ce, computeFingerprint(_srcElem));
                            CorrespondenceModel.updateTargetFingerprint(ce, computeFingerprintBack(_tgtElem));
                        });
                        _updated.add(_srcElem);
                    }
                }
            }

            if (_currentCount < _expectedCount) {
                // Create additional source elements
                String _tgtFp = computeFingerprintBack(_tgtElem);
                for (int _i = _currentCount; _i < _expectedCount; _i++) {
                    ${sourcePackageName}.${agg.sourceType()} _newSrc = ${sourceFactory}.eINSTANCE.create${agg.sourceType()}();
                    _newSrc.set${agg.groupBySourceAttr()?cap_first}(_value);
                    _srcBag.get${agg.sourceContainerRef()?cap_first}().add(_newSrc);
                    String _srcFp = computeFingerprint(_newSrc);
                    CorrespondenceModel.addAggregationEntry(corrResource, _newSrc, "${agg.sourceType()}", _srcFp, _tgtElem, "${agg.targetType()}", _tgtFp);
                    aggregationIndex.put(_newSrc, _tgtElem);
                    _created.add(_newSrc);
                }
            } else if (_currentCount > _expectedCount) {
                // Delete excess source elements (remove from the end)
                for (int _i = _expectedCount; _i < _currentCount; _i++) {
                    EObject _toRemove = _existing.get(_i);
                    EcoreUtil.delete(_toRemove, true);
                    CorrespondenceModel.removeEntry(corrResource, _toRemove);
                }
            }
        }

        // Clean up stale aggregation CEs (target element was deleted → null in corr)
        for (EObject _entry : new java.util.ArrayList<>(CorrespondenceModel.findDeletedAggregationTargetEntries(corrResource))) {
            // Cascade: delete the corresponding source element if still present
            EObject _srcObj = CorrespondenceModel.getSourceObject(_entry);
            if (_srcObj != null && _srcObj.eResource() != null) {
                EcoreUtil.delete(_srcObj, true);
            }
            CorrespondenceModel.removeCorrespondenceEntry(corrResource, _entry);
        }
    }

</#list>
</#if>
    /** Creates a new target object for the given source EObject, or null if no mapping exists. */
