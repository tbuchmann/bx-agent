    }

    // ════════════════════════════════════════════════════════════════════════
    // Phase 1c: Edge Materialization Methods
    // ════════════════════════════════════════════════════════════════════════

<#if edgeMaterializationMappings?has_content>
    /**
     * Phase 1c forward (batch): for each source reference pair, creates an explicit edge object
     * in the target and adds it to the target root container.
     * Called after Phase 1 (createAndMapObjects) so objectMap is fully populated.
     */
    @SuppressWarnings("unchecked")
    private static void materializeEdges(Resource sourceModel, Resource targetModel, Map<EObject, EObject> objectMap) {
<#list edgeMaterializationMappings as emm>
        // ${emm.sourceOwnerType()}.${emm.sourceRef()} → ${emm.edgeType()}
        for (EObject _srcObj : allSourceObjects(sourceModel)) {
            if (!(_srcObj instanceof ${sourcePackageName}.${emm.sourceOwnerType()})) continue;
            org.eclipse.emf.ecore.EStructuralFeature _srcFeat = _srcObj.eClass().getEStructuralFeature("${emm.sourceRef()}");
            if (_srcFeat == null) continue;
            @SuppressWarnings("unchecked")
            java.util.List<EObject> _srcEnds = _srcFeat.isMany()
                ? (EList<EObject>) _srcObj.eGet(_srcFeat)
                : (_srcObj.eGet(_srcFeat) != null ? java.util.List.of((EObject) _srcObj.eGet(_srcFeat)) : java.util.List.of());
            EObject _tgtOwner = objectMap.get(_srcObj);
            if (_tgtOwner == null) continue;
            for (EObject _srcEnd : _srcEnds) {
                EObject _tgtEnd = objectMap.get(_srcEnd);
                if (_tgtEnd == null) continue;
                ${targetPackageName}.${emm.edgeType()} _edge = ${targetFactory}.eINSTANCE.create${emm.edgeType()}();
                org.eclipse.emf.ecore.EStructuralFeature _fromFeat = _edge.eClass().getEStructuralFeature("${emm.edgeFromRef()}");
                org.eclipse.emf.ecore.EStructuralFeature _toFeat = _edge.eClass().getEStructuralFeature("${emm.edgeToRef()}");
                if (_fromFeat != null) _edge.eSet(_fromFeat, _tgtOwner);
                if (_toFeat != null) _edge.eSet(_toFeat, _tgtEnd);
                for (EObject _tgtRoot : targetModel.getContents()) {
                    org.eclipse.emf.ecore.EStructuralFeature _cFeat = _tgtRoot.eClass().getEStructuralFeature("${emm.edgeContainerRef()}");
                    if (_cFeat != null && _cFeat.isMany()) {
                        ((EList<EObject>) _tgtRoot.eGet(_cFeat)).add(_edge);
                        break;
                    }
                }
            }
        }
</#list>
    }

    /**
     * Phase 1c backward (batch): for each edge object in the target, reconstructs the
     * corresponding source references using the inverse objectMap (target → source).
     */
    @SuppressWarnings("unchecked")
    private static void materializeEdgesBack(Resource targetModel, Resource sourceModel, Map<EObject, EObject> objectMap) {
<#list edgeMaterializationMappings as emm>
        // ${emm.edgeType()} → ${emm.sourceOwnerType()}.${emm.sourceRef()}
        for (EObject _tgtObj : allSourceObjects(targetModel)) {
            if (!(_tgtObj instanceof ${targetPackageName}.${emm.edgeType()})) continue;
            org.eclipse.emf.ecore.EStructuralFeature _fromFeat = _tgtObj.eClass().getEStructuralFeature("${emm.edgeFromRef()}");
            org.eclipse.emf.ecore.EStructuralFeature _toFeat = _tgtObj.eClass().getEStructuralFeature("${emm.edgeToRef()}");
            if (_fromFeat == null || _toFeat == null) continue;
            EObject _tgtOwner = (EObject) _tgtObj.eGet(_fromFeat);
            EObject _tgtEnd = (EObject) _tgtObj.eGet(_toFeat);
            if (_tgtOwner == null || _tgtEnd == null) continue;
            EObject _srcOwner = objectMap.get(_tgtOwner);
            EObject _srcEnd = objectMap.get(_tgtEnd);
            if (!(_srcOwner instanceof ${sourcePackageName}.${emm.sourceOwnerType()})
                    || !(_srcEnd instanceof ${sourcePackageName}.${emm.sourceRefTargetType()})) continue;
            org.eclipse.emf.ecore.EStructuralFeature _srcFeat = _srcOwner.eClass().getEStructuralFeature("${emm.sourceRef()}");
            if (_srcFeat == null) continue;
            if (_srcFeat.isMany()) {
                ((EList<EObject>) _srcOwner.eGet(_srcFeat)).add(_srcEnd);
            } else {
                _srcOwner.eSet(_srcFeat, _srcEnd);
            }
        }
</#list>
    }

    /**
     * Phase 1c forward (incremental): clears all managed edge objects from the target
     * container and re-creates them from source using corrIndex.
     * Edge objects are fully derived — they are not tracked in corrIndex.
     */
    @SuppressWarnings("unchecked")
    private static void materializeEdgesIncremental(
            Resource sourceModel, Resource targetModel,
            com.google.common.collect.BiMap<EObject, EObject> corrIndex) {
<#list edgeMaterializationMappings as emm>
        // Smart-merge ${emm.edgeType()} objects:
        // keep edges whose ${emm.edgeFromRef()}+${emm.edgeToRef()} still match a source pair (preserves user-set attributes like weight),
        // delete stale edges, create new edges for new source pairs.
        {
            // Step 1: build expected (tgtOwner → Set<tgtEnd>) from current source references.
            java.util.Map<EObject, java.util.Set<EObject>> _expected${emm.edgeType()} = new java.util.LinkedHashMap<>();
            for (EObject _srcObj : allSourceObjects(sourceModel)) {
                if (!(_srcObj instanceof ${sourcePackageName}.${emm.sourceOwnerType()})) continue;
                org.eclipse.emf.ecore.EStructuralFeature _srcFeat = _srcObj.eClass().getEStructuralFeature("${emm.sourceRef()}");
                if (_srcFeat == null) continue;
                @SuppressWarnings("unchecked")
                java.util.List<EObject> _srcEnds = _srcFeat.isMany()
                    ? (EList<EObject>) _srcObj.eGet(_srcFeat)
                    : (_srcObj.eGet(_srcFeat) != null ? java.util.List.of((EObject) _srcObj.eGet(_srcFeat)) : java.util.List.of());
                EObject _tgtOwner = corrIndex.get(_srcObj);
                if (_tgtOwner == null) continue;
                java.util.Set<EObject> _ends = _expected${emm.edgeType()}.computeIfAbsent(_tgtOwner, k -> new java.util.LinkedHashSet<>());
                for (EObject _srcEnd : _srcEnds) {
                    EObject _tgtEnd = corrIndex.get(_srcEnd);
                    if (_tgtEnd != null) _ends.add(_tgtEnd);
                }
            }
            // Step 2: scan existing edges; keep matches (removes from expected set), delete stale.
            for (EObject _tgtRoot : targetModel.getContents()) {
                org.eclipse.emf.ecore.EStructuralFeature _cFeat = _tgtRoot.eClass().getEStructuralFeature("${emm.edgeContainerRef()}");
                if (_cFeat == null || !_cFeat.isMany()) continue;
                List<EObject> _edgesToDelete = new ArrayList<>();
                for (EObject _e : (EList<EObject>) _tgtRoot.eGet(_cFeat)) {
                    if (!(_e instanceof ${targetPackageName}.${emm.edgeType()})) continue;
                    EObject _from = (EObject) _e.eGet(_e.eClass().getEStructuralFeature("${emm.edgeFromRef()}"));
                    EObject _to   = (EObject) _e.eGet(_e.eClass().getEStructuralFeature("${emm.edgeToRef()}"));
                    java.util.Set<EObject> _validEnds = _expected${emm.edgeType()}.get(_from);
                    if (_validEnds != null && _validEnds.remove(_to)) {
                        // Still valid — keep in place (preserves user-set attributes).
                    } else {
                        _edgesToDelete.add(_e);
                    }
                }
                // EcoreUtil.delete clears eOpposite back-references as well.
                for (EObject _e : _edgesToDelete) EcoreUtil.delete(_e, true);
            }
            // Step 3: create new edges for remaining (unmatched) expected pairs.
            for (java.util.Map.Entry<EObject, java.util.Set<EObject>> _entry : _expected${emm.edgeType()}.entrySet()) {
                EObject _tgtOwner = _entry.getKey();
                for (EObject _tgtEnd : _entry.getValue()) {
                    ${targetPackageName}.${emm.edgeType()} _edge = ${targetFactory}.eINSTANCE.create${emm.edgeType()}();
                    org.eclipse.emf.ecore.EStructuralFeature _fromFeat = _edge.eClass().getEStructuralFeature("${emm.edgeFromRef()}");
                    org.eclipse.emf.ecore.EStructuralFeature _toFeat   = _edge.eClass().getEStructuralFeature("${emm.edgeToRef()}");
                    if (_fromFeat != null) _edge.eSet(_fromFeat, _tgtOwner);
                    if (_toFeat   != null) _edge.eSet(_toFeat,   _tgtEnd);
                    for (EObject _tgtRoot : targetModel.getContents()) {
                        org.eclipse.emf.ecore.EStructuralFeature _cFeat = _tgtRoot.eClass().getEStructuralFeature("${emm.edgeContainerRef()}");
                        if (_cFeat != null && _cFeat.isMany()) {
                            ((EList<EObject>) _tgtRoot.eGet(_cFeat)).add(_edge);
                            break;
                        }
                    }
                }
            }
        }
</#list>
    }

    /**
     * Phase 1c backward (incremental): clears managed source references and re-populates
     * them from edge objects in the target using corrIndex.inverse().
     */
    @SuppressWarnings("unchecked")
    private static void materializeEdgesIncrementalBack(
            Resource targetModel, Resource sourceModel,
            com.google.common.collect.BiMap<EObject, EObject> corrIndex) {
<#list edgeMaterializationMappings as emm>
        // Clear ${emm.sourceOwnerType()}.${emm.sourceRef()}, then re-populate from ${emm.edgeType()}
        for (EObject _srcObj : allSourceObjects(sourceModel)) {
            if (!(_srcObj instanceof ${sourcePackageName}.${emm.sourceOwnerType()})) continue;
            org.eclipse.emf.ecore.EStructuralFeature _srcFeat = _srcObj.eClass().getEStructuralFeature("${emm.sourceRef()}");
            if (_srcFeat == null) continue;
            if (_srcFeat.isMany()) {
                ((EList<EObject>) _srcObj.eGet(_srcFeat)).clear();
            } else {
                _srcObj.eSet(_srcFeat, null);
            }
        }
        for (EObject _tgtObj : allSourceObjects(targetModel)) {
            if (!(_tgtObj instanceof ${targetPackageName}.${emm.edgeType()})) continue;
            org.eclipse.emf.ecore.EStructuralFeature _fromFeat = _tgtObj.eClass().getEStructuralFeature("${emm.edgeFromRef()}");
            org.eclipse.emf.ecore.EStructuralFeature _toFeat = _tgtObj.eClass().getEStructuralFeature("${emm.edgeToRef()}");
            if (_fromFeat == null || _toFeat == null) continue;
            EObject _tgtOwner = (EObject) _tgtObj.eGet(_fromFeat);
            EObject _tgtEnd = (EObject) _tgtObj.eGet(_toFeat);
            if (_tgtOwner == null || _tgtEnd == null) continue;
            EObject _srcOwner = corrIndex.inverse().get(_tgtOwner);
            EObject _srcEnd = corrIndex.inverse().get(_tgtEnd);
            if (!(_srcOwner instanceof ${sourcePackageName}.${emm.sourceOwnerType()})
                    || !(_srcEnd instanceof ${sourcePackageName}.${emm.sourceRefTargetType()})) continue;
            org.eclipse.emf.ecore.EStructuralFeature _srcFeat = _srcOwner.eClass().getEStructuralFeature("${emm.sourceRef()}");
            if (_srcFeat == null) continue;
            if (_srcFeat.isMany()) {
                ((EList<EObject>) _srcOwner.eGet(_srcFeat)).add(_srcEnd);
            } else {
                _srcOwner.eSet(_srcFeat, _srcEnd);
            }
        }
</#list>
    }

</#if>
