    private static void resolveReferences(Map<EObject, EObject> objectMap) {
<#assign hasCrossRefs = false>
<#list referenceMappings as refMapping>
    <#if !refMapping.sourceIsContainment() && !refMapping.sourceIsEOpposite()>
        <#assign hasCrossRefs = true>
        // Cross-reference: ${refMapping.sourceRefOwnerType()}.${refMapping.sourceRef()} → ${refMapping.targetRefOwnerType()}.${refMapping.targetRef()}
        for (Map.Entry<EObject, EObject> entry : objectMap.entrySet()) {
            EObject source = entry.getKey();
            EObject target = entry.getValue();
            if (source instanceof ${sourcePackageName}.${refMapping.sourceRefOwnerType()} && target instanceof ${targetPackageName}.${refMapping.targetRefOwnerType()}) {
                org.eclipse.emf.ecore.EStructuralFeature _srcFeat = source.eClass().getEStructuralFeature("${refMapping.sourceRef()}");
                org.eclipse.emf.ecore.EStructuralFeature _tgtFeat = target.eClass().getEStructuralFeature("${refMapping.targetRef()}");
                if (_srcFeat != null && _tgtFeat != null) {
                    java.util.List<EObject> _srcRefs = _srcFeat.isMany()
                        ? (EList<EObject>) source.eGet(_srcFeat)
                        : (source.eGet(_srcFeat) != null ? java.util.List.of((EObject) source.eGet(_srcFeat)) : java.util.List.of());
                    for (EObject refSrc : _srcRefs) {
                        EObject refTgt = objectMap.get(refSrc);
                        if (refTgt instanceof ${targetPackageName}.${refMapping.targetRefTargetType()}) {
                            if (_tgtFeat.isMany()) {
                                ((EList<EObject>) target.eGet(_tgtFeat)).add(refTgt);
                            } else {
                                target.eSet(_tgtFeat, refTgt);
                            }
                        }
                    }
                }
            }
        }

    </#if>
</#list>
<#if !hasCrossRefs>
        // No cross-references defined
</#if>
    }

    // ════════════════════════════════════════════════════════════════════════
    // Phase 1: Object Creation and Attribute Mapping (Backward)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Recursively creates source objects for target object tree (containments only).
     * Maps attributes and stores target→source mapping.
     * Pre-existing entries in objectMap (e.g. root elements) are traversed but not recreated.
     */
    private static EObject createAndMapObjectsBack(EObject target, Map<EObject, EObject> objectMap, Set<EObject> visited, Options options) {
        if (target == null) {
            return null;
        }

        // Use visited set to prevent infinite loops (objectMap alone is not enough
        // since root elements are pre-seeded and must still be traversed)
        if (visited.contains(target)) {
            return objectMap.get(target);
        }
        visited.add(target);

        EObject source;
        if (objectMap.containsKey(target)) {
            // Pre-existing entry (e.g. root element) — use it, still traverse children
            source = objectMap.get(target);
        } else {
<#if syntheticObjectMappings?has_content>
            // Skip synthetic objects — they have no source counterpart
            if (isSyntheticObject(target)) return null;
</#if>
            source = null;

<#list typeMappingGroups as group>
    <#if group.hasMultiple>
            // Handle ${group.targetType} → multiple source types (m:1 mapping, discriminated by backwardCondition)
            <#if !group?is_first>else </#if>if (target instanceof ${targetPackageName}.${group.targetType} _typed) {
        <#list group.mappings as tm>
                <#if !tm?is_first>} else </#if>if (${tm.backwardCondition()?replace("target", "_typed")}) {
                    source = transformBack${group.targetType}As${tm.sourceType()}(_typed, options);
        </#list>
                }
            }
    <#else>
            // Handle ${group.targetType} → ${group.mappings[0].sourceType()}
            <#if !group?is_first>else </#if>if (target instanceof ${targetPackageName}.${group.targetType} _typed) {
                source = transformBack${group.targetType}As${group.mappings[0].sourceType()}(_typed, options);
            }
    </#if>
</#list>

            if (source != null) {
                objectMap.put(target, source);
            }
        }

        if (source != null) {

            // Recursively transform containment references
            // Cardinality resolved at runtime via EMF feature metadata.
<#assign hasContainments = false>
<#list referenceMappings as refMapping>
    <#if refMapping.targetIsContainment()>
        <#assign hasContainments = true>
            // Containment (backward): ${refMapping.targetRefOwnerType()}.${refMapping.targetRef()} → ${refMapping.sourceRefOwnerType()}.${refMapping.sourceRef()}
            if (target instanceof ${targetPackageName}.${refMapping.targetRefOwnerType()} && source instanceof ${sourcePackageName}.${refMapping.sourceRefOwnerType()}) {
                org.eclipse.emf.ecore.EStructuralFeature _tgtFeat = target.eClass().getEStructuralFeature("${refMapping.targetRef()}");
                org.eclipse.emf.ecore.EStructuralFeature _srcFeat = source.eClass().getEStructuralFeature("${refMapping.sourceRef()}");
                if (_tgtFeat != null && _srcFeat != null) {
                    @SuppressWarnings("unchecked")
                    java.util.List<EObject> _tgtItems = _tgtFeat.isMany()
                        ? (EList<EObject>) target.eGet(_tgtFeat)
                        : (target.eGet(_tgtFeat) != null ? java.util.List.of((EObject) target.eGet(_tgtFeat)) : java.util.List.of());
                    for (EObject _rawChild : _tgtItems) {
                        if (_rawChild instanceof ${targetPackageName}.${refMapping.targetRefTargetType()} child) {
                            EObject transformedChild = createAndMapObjectsBack(child, objectMap, visited, options);
                            if (transformedChild instanceof ${sourcePackageName}.${refMapping.sourceRefTargetType()}) {
                                if (_srcFeat.isMany()) {
                                    @SuppressWarnings("unchecked")
                                    EList<EObject> _srcList = (EList<EObject>) source.eGet(_srcFeat);
                                    _srcList.add(transformedChild);
                                } else {
                                    source.eSet(_srcFeat, transformedChild);
                                }
                            }
                        }
                    }
                }
            }
    </#if>
</#list>
<#if !hasContainments>
            // No containment references defined
</#if>
        }

        return source;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Phase 2: Cross-Reference Resolution (Backward)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Resolves all cross-references (non-containment) using the object map.
     */
    @SuppressWarnings("unchecked")
    private static void resolveReferencesBack(Map<EObject, EObject> objectMap) {
<#assign hasCrossRefs = false>
<#list referenceMappings as refMapping>
    <#if !refMapping.targetIsContainment() && !refMapping.targetIsEOpposite()>
        <#assign hasCrossRefs = true>
        // Cross-reference (backward): ${refMapping.targetRefOwnerType()}.${refMapping.targetRef()} → ${refMapping.sourceRefOwnerType()}.${refMapping.sourceRef()}
        for (Map.Entry<EObject, EObject> entry : objectMap.entrySet()) {
            EObject target = entry.getKey();
            EObject source = entry.getValue();
            if (target instanceof ${targetPackageName}.${refMapping.targetRefOwnerType()} && source instanceof ${sourcePackageName}.${refMapping.sourceRefOwnerType()}) {
                org.eclipse.emf.ecore.EStructuralFeature _tgtFeat = target.eClass().getEStructuralFeature("${refMapping.targetRef()}");
                org.eclipse.emf.ecore.EStructuralFeature _srcFeat = source.eClass().getEStructuralFeature("${refMapping.sourceRef()}");
                if (_tgtFeat != null && _srcFeat != null) {
                    java.util.List<EObject> _tgtRefs = _tgtFeat.isMany()
                        ? (EList<EObject>) target.eGet(_tgtFeat)
                        : (target.eGet(_tgtFeat) != null ? java.util.List.of((EObject) target.eGet(_tgtFeat)) : java.util.List.of());
                    for (EObject refTgt : _tgtRefs) {
                        EObject refSrc = objectMap.get(refTgt);
                        if (refSrc instanceof ${sourcePackageName}.${refMapping.sourceRefTargetType()}) {
                            if (_srcFeat.isMany()) {
                                ((EList<EObject>) source.eGet(_srcFeat)).add(refSrc);
                            } else {
                                source.eSet(_srcFeat, refSrc);
                            }
                        }
                    }
                }
            }
        }

    </#if>
</#list>
<#if !hasCrossRefs>
        // No cross-references defined
</#if>
<#if targetLinkMappings?has_content && targetLinkMetamodel??>
        final String _bwTLM_LINK    = "${targetLinkMetamodel.linkEClass()}";
        final String _bwTLM_LINKS_F = "${targetLinkMetamodel.linkContainerFeature()}";
        final String _bwTLM_SRC_F   = "${targetLinkMetamodel.linkSourceFeature()}";
        final String _bwTLM_TGT_F   = "${targetLinkMetamodel.linkTargetFeature()}";
        final String _bwTLM_NODES_F = "${targetLinkMetamodel.nodeContainerFeature()}";
        final String _bwTLM_SLOTS_F = "${targetLinkMetamodel.slotContainerFeature()}";
        final String _bwTLM_NNAME_F = "${targetLinkMetamodel.nodeNameFeature()}";
        final String _bwTLM_SNAME_F = "${targetLinkMetamodel.slotNameFeature()}";
<#list targetLinkMappings as tlm>
<#if tlm.fkType() == "INHERITANCE">
        // INHERITANCE backward: resolve eSuperTypes from link annotations
        for (EObject _inhTgt : new ArrayList<>(objectMap.keySet())) {
            if (!_tlmIsNodeOf(_inhTgt, _bwTLM_NODES_F)) continue;
            EObject _inhSrc = objectMap.get(_inhTgt);
            if (!(_inhSrc instanceof ${sourcePackageName}.EClass _inhSrcEClass)) continue;
            _inhSrcEClass.getESuperTypes().clear();
            for (EObject _inhFk : _tlmList(_inhTgt, _bwTLM_LINKS_F)) {
                if (_hasAnnotation(_inhFk, "${tlm.fkAnnotations()?first}")) {
                    EObject _superNode = _tlmGetRef(_inhFk, _bwTLM_TGT_F);
                    if (_superNode != null) {
                        EObject _superSrc = objectMap.get(_superNode);
                        if (_superSrc instanceof ${sourcePackageName}.EClass _superSrcEClass) {
                            _inhSrcEClass.getESuperTypes().add(_superSrcEClass);
                        }
                    }
                }
            }
        }
</#if>
<#if tlm.fkType() == "BIDIRECTIONAL_CROSS_REF">
        // BIDIRECTIONAL_CROSS_REF backward: create paired EReferences with eOpposite
        for (EObject _bxrNode : new ArrayList<>(objectMap.keySet())) {
            if (!(_hasAnnotation(_bxrNode, "bidirectional") && _hasAnnotation(_bxrNode, "cross"))) continue;
            EObject _bxrSrcFk = _tlmList(_bxrNode, _bwTLM_LINKS_F).stream()
                .filter(lk -> "source".equals(_tlmGetStr(_tlmGetRef(lk, _bwTLM_SRC_F), _bwTLM_SNAME_F)))
                .findFirst().orElse(null);
            EObject _bxrTgtFk = _tlmList(_bxrNode, _bwTLM_LINKS_F).stream()
                .filter(lk -> "target".equals(_tlmGetStr(_tlmGetRef(lk, _bwTLM_SRC_F), _bwTLM_SNAME_F)))
                .findFirst().orElse(null);
            if (_bxrSrcFk == null || _bxrTgtFk == null) continue;
            EObject _bxrSrcRefNode = _tlmGetRef(_bxrSrcFk, _bwTLM_TGT_F);
            EObject _bxrTgtRefNode = _tlmGetRef(_bxrTgtFk, _bwTLM_TGT_F);
            if (_bxrSrcRefNode == null || _bxrTgtRefNode == null) continue;
            EObject _bxrSrcEO = objectMap.get(_bxrSrcRefNode);
            EObject _bxrTgtEO = objectMap.get(_bxrTgtRefNode);
            if (!(_bxrSrcEO instanceof ${sourcePackageName}.EClass _bxrSrcEClass)) continue;
            if (!(_bxrTgtEO instanceof ${sourcePackageName}.EClass _bxrTgtEClass)) continue;
            String _bxrNodeName = _tlmGetStr(_bxrNode, _bwTLM_NNAME_F);
            if (_bxrNodeName == null) continue;
            String _bxrSrcTN = _tlmGetStr(_bxrSrcRefNode, _bwTLM_NNAME_F);
            String _bxrTgtTN = _tlmGetStr(_bxrTgtRefNode, _bwTLM_NNAME_F);
            String _bxrFwdName = null, _bxrBwdName = null;
            if (_bxrNodeName.contains("_inverse_")) {
                String[] _bxrParts = _bxrNodeName.split("_inverse_", 2);
                if (_bxrSrcTN != null && _bxrParts[0].startsWith(_bxrSrcTN + "_"))
                    _bxrFwdName = _bxrParts[0].substring(_bxrSrcTN.length() + 1);
                if (_bxrTgtTN != null && _bxrParts[1].startsWith(_bxrTgtTN + "_"))
                    _bxrBwdName = _bxrParts[1].substring(_bxrTgtTN.length() + 1);
            }
            if (_bxrFwdName == null || _bxrBwdName == null) continue;
            final String _bxrFwdNameF = _bxrFwdName, _bxrBwdNameF = _bxrBwdName;
            ${sourcePackageName}.EReference _bxrFwdExisting = (${sourcePackageName}.EReference) _bxrSrcEClass.getEStructuralFeatures().stream()
                .filter(f -> f instanceof ${sourcePackageName}.EReference && _bxrFwdNameF.equals(f.getName())).findFirst().orElse(null);
            ${sourcePackageName}.EReference _bxrBwdExisting = (${sourcePackageName}.EReference) _bxrTgtEClass.getEStructuralFeatures().stream()
                .filter(f -> f instanceof ${sourcePackageName}.EReference && _bxrBwdNameF.equals(f.getName())).findFirst().orElse(null);
            if (_bxrFwdExisting != null && _bxrBwdExisting != null) {
                _bxrFwdExisting.setEOpposite(_bxrBwdExisting);
                _bxrBwdExisting.setEOpposite(_bxrFwdExisting);
                continue;
            }
            ${sourcePackageName}.EReference _bxrFwdRef = ${sourceFactory}.eINSTANCE.createEReference();
            _bxrFwdRef.setName(_bxrFwdName);
            _bxrFwdRef.setEType(_bxrTgtEClass);
            _bxrFwdRef.setUpperBound(_hasAnnotation(_bxrNode, "forwardSingle") ? 1 : -1);
            _bxrSrcEClass.getEStructuralFeatures().add(_bxrFwdRef);
            ${sourcePackageName}.EReference _bxrBwdRef = ${sourceFactory}.eINSTANCE.createEReference();
            _bxrBwdRef.setName(_bxrBwdName);
            _bxrBwdRef.setEType(_bxrSrcEClass);
            _bxrBwdRef.setUpperBound(_hasAnnotation(_bxrNode, "backwardSingle") ? 1 : -1);
            _bxrTgtEClass.getEStructuralFeatures().add(_bxrBwdRef);
            _bxrFwdRef.setEOpposite(_bxrBwdRef);
            _bxrBwdRef.setEOpposite(_bxrFwdRef);
        }
</#if>
<#if tlm.fkType() == "CONTAINMENT_SINGLE">
        // CONTAINMENT_SINGLE backward: slot with "containment"+"unidirectional" → EReference (containment) in parent EClass
        for (EObject _csBwSlot : new ArrayList<>(objectMap.keySet())) {
            if (!(_hasAnnotation(_csBwSlot, "containment") && _hasAnnotation(_csBwSlot, "unidirectional"))) continue;
            EObject _csOwnerNode = _csBwSlot.eContainer();
            EObject _csFk = _tlmList(_csOwnerNode, _bwTLM_LINKS_F).stream()
                .filter(lk -> _tlmGetRef(lk, _bwTLM_SRC_F) == _csBwSlot && _hasAnnotation(lk, "containment") && _hasAnnotation(lk, "unidirectional"))
                .findFirst().orElse(null);
            if (_csFk == null) continue;
            EObject _csParentEO = objectMap.get(_tlmGetRef(_csFk, _bwTLM_TGT_F));
            if (!(_csParentEO instanceof ${sourcePackageName}.EClass _csParentEClass)) continue;
            EObject _csChildEO = objectMap.get(_csOwnerNode);
            if (!(_csChildEO instanceof ${sourcePackageName}.EClass _csChildEClass)) continue;
            String _csSlotName = _tlmGetStr(_csBwSlot, _bwTLM_SNAME_F);
            String _csRefName = _csSlotName != null && _csSlotName.endsWith("_inverse")
                ? _csSlotName.substring(0, _csSlotName.length() - "_inverse".length())
                : _csSlotName;
            final String _csRefNameF = _csRefName;
            if (_csParentEClass.getEStructuralFeatures().stream().anyMatch(f -> _csRefNameF.equals(f.getName()))) continue;
            ${sourcePackageName}.EReference _csRef = ${sourceFactory}.eINSTANCE.createEReference();
            _csRef.setName(_csRefName);
            _csRef.setEType(_csChildEClass);
            _csRef.setContainment(true);
            _csRef.setUpperBound(_hasAnnotation(_csBwSlot, "single") ? 1 : -1);
            _csParentEClass.getEStructuralFeatures().add(_csRef);
        }
</#if>
<#if tlm.fkType() == "CONTAINMENT_MULTI_BIDIRECTIONAL">
        // CONTAINMENT_MULTI_BIDIRECTIONAL backward: slot with "containment"+"bidirectional" → EReference pair
        for (EObject _cmbBwSlot : new ArrayList<>(objectMap.keySet())) {
            if (!(_hasAnnotation(_cmbBwSlot, "containment") && _hasAnnotation(_cmbBwSlot, "bidirectional"))) continue;
            EObject _cmbOwnerNode = _cmbBwSlot.eContainer();
            EObject _cmbFk = _tlmList(_cmbOwnerNode, _bwTLM_LINKS_F).stream()
                .filter(lk -> _tlmGetRef(lk, _bwTLM_SRC_F) == _cmbBwSlot && _hasAnnotation(lk, "containment") && _hasAnnotation(lk, "bidirectional"))
                .findFirst().orElse(null);
            if (_cmbFk == null) continue;
            EObject _cmbParentEO = objectMap.get(_tlmGetRef(_cmbFk, _bwTLM_TGT_F));
            if (!(_cmbParentEO instanceof ${sourcePackageName}.EClass _cmbParentEClass)) continue;
            EObject _cmbChildEO = objectMap.get(_cmbOwnerNode);
            if (!(_cmbChildEO instanceof ${sourcePackageName}.EClass _cmbChildEClass)) continue;
            String _cmbSlotName = _tlmGetStr(_cmbBwSlot, _bwTLM_SNAME_F);
            String _cmbFwdName = null, _cmbBwdName = null;
            if (_cmbSlotName != null && _cmbSlotName.contains("_inverse_")) {
                String[] _cmbParts = _cmbSlotName.split("_inverse_", 2);
                _cmbBwdName = _cmbParts[0];
                _cmbFwdName = _cmbParts[1];
            }
            if (_cmbFwdName == null || _cmbBwdName == null) continue;
            final String _cmbFwdNameF = _cmbFwdName, _cmbBwdNameF = _cmbBwdName;
            boolean _cmbFwdExists = _cmbParentEClass.getEStructuralFeatures().stream().anyMatch(f -> _cmbFwdNameF.equals(f.getName()));
            boolean _cmbBwdExists = _cmbChildEClass.getEStructuralFeatures().stream().anyMatch(f -> _cmbBwdNameF.equals(f.getName()));
            if (_cmbFwdExists && _cmbBwdExists) {
                ${sourcePackageName}.EReference _cmbFwdRef2 = (${sourcePackageName}.EReference) _cmbParentEClass.getEStructuralFeatures().stream()
                    .filter(f -> f instanceof ${sourcePackageName}.EReference && _cmbFwdNameF.equals(f.getName())).findFirst().orElse(null);
                ${sourcePackageName}.EReference _cmbBwdRef2 = (${sourcePackageName}.EReference) _cmbChildEClass.getEStructuralFeatures().stream()
                    .filter(f -> f instanceof ${sourcePackageName}.EReference && _cmbBwdNameF.equals(f.getName())).findFirst().orElse(null);
                if (_cmbFwdRef2 != null && _cmbBwdRef2 != null) { _cmbFwdRef2.setEOpposite(_cmbBwdRef2); _cmbBwdRef2.setEOpposite(_cmbFwdRef2); }
                continue;
            }
            ${sourcePackageName}.EReference _cmbFwdRef = ${sourceFactory}.eINSTANCE.createEReference();
            _cmbFwdRef.setName(_cmbFwdName);
            _cmbFwdRef.setEType(_cmbChildEClass);
            _cmbFwdRef.setContainment(true);
            _cmbFwdRef.setUpperBound(_hasAnnotation(_cmbBwSlot, "single") ? 1 : -1);
            _cmbParentEClass.getEStructuralFeatures().add(_cmbFwdRef);
            ${sourcePackageName}.EReference _cmbBwdRef = ${sourceFactory}.eINSTANCE.createEReference();
            _cmbBwdRef.setName(_cmbBwdName);
            _cmbBwdRef.setEType(_cmbParentEClass);
            _cmbChildEClass.getEStructuralFeatures().add(_cmbBwdRef);
            _cmbFwdRef.setEOpposite(_cmbBwdRef);
            _cmbBwdRef.setEOpposite(_cmbFwdRef);
        }
</#if>
</#list>
</#if>
    }

    // ════════════════════════════════════════════════════════════════════════
    // Phase 3: Incremental Cross-Reference Resolution
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Resolves all cross-references (non-containment) incrementally.
     * Uses corrIndex as lookup (not objectMap which doesn't exist in incremental mode).
     * Iterates ALL source objects — cross-reference changes are not captured by fingerprint.
     * For isMany references: clears the target list before refilling.
     */
    @SuppressWarnings("unchecked")
    private static void resolveReferencesIncremental(
            Resource sourceModel,
            Resource existingTarget,
            com.google.common.collect.BiMap<EObject, EObject> corrIndex) {
<#assign hasCrossRefs = false>
<#list referenceMappings as refMapping>
    <#if !refMapping.sourceIsContainment() && !refMapping.sourceIsEOpposite()>
        <#assign hasCrossRefs = true>
        // Cross-reference (incremental): ${refMapping.sourceRefOwnerType()}.${refMapping.sourceRef()} → ${refMapping.targetRefOwnerType()}.${refMapping.targetRef()}
        for (EObject srcObj : allSourceObjects(sourceModel)) {
            if (!(srcObj instanceof ${sourcePackageName}.${refMapping.sourceRefOwnerType()})) continue;
            EObject tgtObj = corrIndex.get(srcObj);
            if (tgtObj == null || !(tgtObj instanceof ${targetPackageName}.${refMapping.targetRefOwnerType()})) continue;
            org.eclipse.emf.ecore.EStructuralFeature _srcFeat = srcObj.eClass().getEStructuralFeature("${refMapping.sourceRef()}");
            org.eclipse.emf.ecore.EStructuralFeature _tgtFeat = tgtObj.eClass().getEStructuralFeature("${refMapping.targetRef()}");
            if (_srcFeat == null || _tgtFeat == null) continue;
            if (_tgtFeat.isMany()) {
                // isMany target → clear and refill
                @SuppressWarnings("unchecked")
                EList<EObject> _tgtList = (EList<EObject>) tgtObj.eGet(_tgtFeat);
                if (_tgtList != null) {
                    _tgtList.clear();
                    if (_srcFeat.isMany()) {
                        @SuppressWarnings("unchecked")
                        EList<EObject> _srcItems = (EList<EObject>) srcObj.eGet(_srcFeat);
                        for (EObject refSrc : _srcItems) {
                            EObject refTgt = corrIndex.get(refSrc);
                            if (refTgt instanceof ${targetPackageName}.${refMapping.targetRefTargetType()}) {
                                _tgtList.add(refTgt);
                            }
                        }
                    } else {
                        EObject refSrc = (EObject) srcObj.eGet(_srcFeat);
                        if (refSrc != null) {
                            EObject refTgt = corrIndex.get(refSrc);
                            if (refTgt instanceof ${targetPackageName}.${refMapping.targetRefTargetType()}) {
                                _tgtList.add(refTgt);
                            }
                        }
                    }
                }
            } else {
                // !isMany target → set single value
                EObject refSrc = _srcFeat.isMany() ? null : (EObject) srcObj.eGet(_srcFeat);
                if (refSrc == null) {
                    tgtObj.eSet(_tgtFeat, null);
                } else {
                    EObject refTgt = corrIndex.get(refSrc);
                    if (refTgt instanceof ${targetPackageName}.${refMapping.targetRefTargetType()}) {
                        tgtObj.eSet(_tgtFeat, refTgt);
                    }
                }
            }
        }

    </#if>
</#list>
<#if !hasCrossRefs>
        // No cross-references defined
</#if>
    }

    /**
     * Resolves all cross-references (non-containment) incrementally in the backward direction.
     * Uses corrIndex.inverse() as lookup.
     * For isMany references: clears the source list before refilling.
     */
    @SuppressWarnings("unchecked")
    private static void resolveReferencesIncrementalBack(
            Resource targetModel,
            Resource sourceModel,
            com.google.common.collect.BiMap<EObject, EObject> corrIndex) {
<#assign hasCrossRefsBack = false>
<#list referenceMappings as refMapping>
    <#if !refMapping.targetIsContainment() && !refMapping.targetIsEOpposite()>
        <#assign hasCrossRefsBack = true>
        // Cross-reference (incremental backward): ${refMapping.targetRefOwnerType()}.${refMapping.targetRef()} → ${refMapping.sourceRefOwnerType()}.${refMapping.sourceRef()}
        for (EObject tgtObj : allSourceObjects(targetModel)) {
            if (!(tgtObj instanceof ${targetPackageName}.${refMapping.targetRefOwnerType()})) continue;
            EObject srcObj = corrIndex.inverse().get(tgtObj);
            if (srcObj == null || !(srcObj instanceof ${sourcePackageName}.${refMapping.sourceRefOwnerType()})) continue;
            org.eclipse.emf.ecore.EStructuralFeature _tgtFeat = tgtObj.eClass().getEStructuralFeature("${refMapping.targetRef()}");
            org.eclipse.emf.ecore.EStructuralFeature _srcFeat = srcObj.eClass().getEStructuralFeature("${refMapping.sourceRef()}");
            if (_tgtFeat == null || _srcFeat == null) continue;
            if (_srcFeat.isMany()) {
                // isMany source → clear and refill
                @SuppressWarnings("unchecked")
                EList<EObject> _srcList = (EList<EObject>) srcObj.eGet(_srcFeat);
                if (_srcList != null) {
                    _srcList.clear();
                    if (_tgtFeat.isMany()) {
                        @SuppressWarnings("unchecked")
                        EList<EObject> _tgtItems = (EList<EObject>) tgtObj.eGet(_tgtFeat);
                        for (EObject refTgt : _tgtItems) {
                            EObject refSrc = corrIndex.inverse().get(refTgt);
                            if (refSrc instanceof ${sourcePackageName}.${refMapping.sourceRefTargetType()}) {
                                _srcList.add(refSrc);
                            }
                        }
                    } else {
                        EObject refTgt = (EObject) tgtObj.eGet(_tgtFeat);
                        if (refTgt != null) {
                            EObject refSrc = corrIndex.inverse().get(refTgt);
                            if (refSrc instanceof ${sourcePackageName}.${refMapping.sourceRefTargetType()}) {
                                _srcList.add(refSrc);
                            }
                        }
                    }
                }
            } else {
                // !isMany source → set single value
                EObject refTgt = _tgtFeat.isMany() ? null : (EObject) tgtObj.eGet(_tgtFeat);
                if (refTgt == null) {
                    srcObj.eSet(_srcFeat, null);
                } else {
                    EObject refSrc = corrIndex.inverse().get(refTgt);
                    if (refSrc instanceof ${sourcePackageName}.${refMapping.sourceRefTargetType()}) {
                        srcObj.eSet(_srcFeat, refSrc);
                    }
                }
            }
        }

    </#if>
</#list>
<#if !hasCrossRefsBack>
        // No cross-references defined
</#if>
<#if targetLinkMappings?has_content && targetLinkMetamodel??>
        final String _bwTLM_LINK    = "${targetLinkMetamodel.linkEClass()}";
        final String _bwTLM_LINKS_F = "${targetLinkMetamodel.linkContainerFeature()}";
        final String _bwTLM_SRC_F   = "${targetLinkMetamodel.linkSourceFeature()}";
        final String _bwTLM_TGT_F   = "${targetLinkMetamodel.linkTargetFeature()}";
        final String _bwTLM_NODES_F = "${targetLinkMetamodel.nodeContainerFeature()}";
        final String _bwTLM_SLOTS_F = "${targetLinkMetamodel.slotContainerFeature()}";
        final String _bwTLM_NNAME_F = "${targetLinkMetamodel.nodeNameFeature()}";
        final String _bwTLM_SNAME_F = "${targetLinkMetamodel.slotNameFeature()}";
<#list targetLinkMappings as tlm>
<#if tlm.fkType() == "INHERITANCE">
        // INHERITANCE backward: resolve eSuperTypes from link annotations
        for (EObject _inhTgt : allSourceObjects(targetModel)) {
            if (!_tlmIsNodeOf(_inhTgt, _bwTLM_NODES_F)) continue;
            EObject _inhSrc = corrIndex.inverse().get(_inhTgt);
            if (!(_inhSrc instanceof ${sourcePackageName}.EClass _inhSrcEClass)) continue;
            _inhSrcEClass.getESuperTypes().clear();
            for (EObject _inhFk : _tlmList(_inhTgt, _bwTLM_LINKS_F)) {
                if (_hasAnnotation(_inhFk, "${tlm.fkAnnotations()?first}")) {
                    EObject _superNode = _tlmGetRef(_inhFk, _bwTLM_TGT_F);
                    if (_superNode != null) {
                        EObject _superSrc = corrIndex.inverse().get(_superNode);
                        if (_superSrc instanceof ${sourcePackageName}.EClass _superSrcEClass) {
                            _inhSrcEClass.getESuperTypes().add(_superSrcEClass);
                        }
                    }
                }
            }
        }
</#if>
<#if tlm.fkType() == "BIDIRECTIONAL_CROSS_REF">
        // BIDIRECTIONAL_CROSS_REF backward incremental: remove stale EReference pairs no longer backed by TLM
        {
            java.util.Set<String> _bxrLiveNodeNames = new java.util.HashSet<>();
            for (EObject _ln : allSourceObjects(targetModel)) {
                if (_hasAnnotation(_ln, "bidirectional") && _hasAnnotation(_ln, "cross")) {
                    String _lnn = _tlmGetStr(_ln, _bwTLM_NNAME_F);
                    if (_lnn != null) _bxrLiveNodeNames.add(_lnn);
                }
            }
            for (EObject _bxrStaleObj : allSourceObjects(sourceModel)) {
                if (!(_bxrStaleObj instanceof ${sourcePackageName}.EClass _bxrStaleEC)) continue;
                for (org.eclipse.emf.ecore.EStructuralFeature _bxrStaleF : new java.util.ArrayList<>(_bxrStaleEC.getEStructuralFeatures())) {
                    if (!(_bxrStaleF instanceof ${sourcePackageName}.EReference _bxrStaleRef)) continue;
                    if (_bxrStaleRef.isContainment()) continue;
                    ${sourcePackageName}.EReference _bxrStaleOpp = _bxrStaleRef.getEOpposite();
                    if (_bxrStaleOpp == null) continue;
                    if (_bxrStaleOpp.isContainment()) continue; // backward half of CONTAINMENT_MULTI_BIDIRECTIONAL, not BXRF
                    if (_bxrStaleOpp.eContainer() == null) continue; // already cleaned up from the other side
                    if (_bxrStaleRef.getEType() == null) continue;
                    String _bxrStaleTgtName = _bxrStaleRef.getEType().getName();
                    String _bxrStaleN1 = _bxrStaleEC.getName() + "_" + _bxrStaleRef.getName() + "_inverse_" + _bxrStaleTgtName + "_" + _bxrStaleOpp.getName();
                    String _bxrStaleN2 = _bxrStaleTgtName + "_" + _bxrStaleOpp.getName() + "_inverse_" + _bxrStaleEC.getName() + "_" + _bxrStaleRef.getName();
                    if (!_bxrLiveNodeNames.contains(_bxrStaleN1) && !_bxrLiveNodeNames.contains(_bxrStaleN2)) {
                        EcoreUtil.remove(_bxrStaleOpp);
                        EcoreUtil.remove(_bxrStaleRef);
                    }
                }
            }
        }
        // BIDIRECTIONAL_CROSS_REF backward: create paired EReferences with eOpposite
        for (EObject _bxrNode : allSourceObjects(targetModel)) {
            if (!(_hasAnnotation(_bxrNode, "bidirectional") && _hasAnnotation(_bxrNode, "cross"))) continue;
            EObject _bxrSrcFk = _tlmList(_bxrNode, _bwTLM_LINKS_F).stream()
                .filter(lk -> "source".equals(_tlmGetStr(_tlmGetRef(lk, _bwTLM_SRC_F), _bwTLM_SNAME_F)))
                .findFirst().orElse(null);
            EObject _bxrTgtFk = _tlmList(_bxrNode, _bwTLM_LINKS_F).stream()
                .filter(lk -> "target".equals(_tlmGetStr(_tlmGetRef(lk, _bwTLM_SRC_F), _bwTLM_SNAME_F)))
                .findFirst().orElse(null);
            if (_bxrSrcFk == null || _bxrTgtFk == null) continue;
            EObject _bxrSrcRefNode = _tlmGetRef(_bxrSrcFk, _bwTLM_TGT_F);
            EObject _bxrTgtRefNode = _tlmGetRef(_bxrTgtFk, _bwTLM_TGT_F);
            if (_bxrSrcRefNode == null || _bxrTgtRefNode == null) continue;
            EObject _bxrSrcEO = corrIndex.inverse().get(_bxrSrcRefNode);
            EObject _bxrTgtEO = corrIndex.inverse().get(_bxrTgtRefNode);
            if (!(_bxrSrcEO instanceof ${sourcePackageName}.EClass _bxrSrcEClass)) continue;
            if (!(_bxrTgtEO instanceof ${sourcePackageName}.EClass _bxrTgtEClass)) continue;
            String _bxrNodeName = _tlmGetStr(_bxrNode, _bwTLM_NNAME_F);
            if (_bxrNodeName == null) continue;
            String _bxrSrcTN = _tlmGetStr(_bxrSrcRefNode, _bwTLM_NNAME_F);
            String _bxrTgtTN = _tlmGetStr(_bxrTgtRefNode, _bwTLM_NNAME_F);
            String _bxrFwdName = null, _bxrBwdName = null;
            if (_bxrNodeName.contains("_inverse_")) {
                String[] _bxrParts = _bxrNodeName.split("_inverse_", 2);
                if (_bxrSrcTN != null && _bxrParts[0].startsWith(_bxrSrcTN + "_"))
                    _bxrFwdName = _bxrParts[0].substring(_bxrSrcTN.length() + 1);
                if (_bxrTgtTN != null && _bxrParts[1].startsWith(_bxrTgtTN + "_"))
                    _bxrBwdName = _bxrParts[1].substring(_bxrTgtTN.length() + 1);
            }
            if (_bxrFwdName == null || _bxrBwdName == null) continue;
            final String _bxrFwdNameF = _bxrFwdName, _bxrBwdNameF = _bxrBwdName;
            ${sourcePackageName}.EReference _bxrFwdExisting = (${sourcePackageName}.EReference) _bxrSrcEClass.getEStructuralFeatures().stream()
                .filter(f -> f instanceof ${sourcePackageName}.EReference && _bxrFwdNameF.equals(f.getName())).findFirst().orElse(null);
            ${sourcePackageName}.EReference _bxrBwdExisting = (${sourcePackageName}.EReference) _bxrTgtEClass.getEStructuralFeatures().stream()
                .filter(f -> f instanceof ${sourcePackageName}.EReference && _bxrBwdNameF.equals(f.getName())).findFirst().orElse(null);
            if (_bxrFwdExisting != null && _bxrBwdExisting != null) {
                _bxrFwdExisting.setEOpposite(_bxrBwdExisting);
                _bxrBwdExisting.setEOpposite(_bxrFwdExisting);
                continue;
            }
            ${sourcePackageName}.EReference _bxrFwdRef = ${sourceFactory}.eINSTANCE.createEReference();
            _bxrFwdRef.setName(_bxrFwdName);
            _bxrFwdRef.setEType(_bxrTgtEClass);
            _bxrFwdRef.setUpperBound(_hasAnnotation(_bxrNode, "forwardSingle") ? 1 : -1);
            _bxrSrcEClass.getEStructuralFeatures().add(_bxrFwdRef);
            ${sourcePackageName}.EReference _bxrBwdRef = ${sourceFactory}.eINSTANCE.createEReference();
            _bxrBwdRef.setName(_bxrBwdName);
            _bxrBwdRef.setEType(_bxrSrcEClass);
            _bxrBwdRef.setUpperBound(_hasAnnotation(_bxrNode, "backwardSingle") ? 1 : -1);
            _bxrTgtEClass.getEStructuralFeatures().add(_bxrBwdRef);
            _bxrFwdRef.setEOpposite(_bxrBwdRef);
            _bxrBwdRef.setEOpposite(_bxrFwdRef);
        }
</#if>
<#if tlm.fkType() == "CONTAINMENT_SINGLE">
        // CONTAINMENT_SINGLE backward incremental: remove stale containment EReferences no longer backed by TLM
        {
            java.util.Set<String> _csLiveSlotNames = new java.util.HashSet<>();
            for (EObject _ls : allSourceObjects(targetModel)) {
                if (_hasAnnotation(_ls, "containment") && _hasAnnotation(_ls, "unidirectional")) {
                    String _lsn = _tlmGetStr(_ls, _bwTLM_SNAME_F);
                    if (_lsn != null) _csLiveSlotNames.add(_lsn);
                }
            }
            for (EObject _csStaleObj : allSourceObjects(sourceModel)) {
                if (!(_csStaleObj instanceof ${sourcePackageName}.EClass _csStaleEC)) continue;
                for (org.eclipse.emf.ecore.EStructuralFeature _csStaleF : new java.util.ArrayList<>(_csStaleEC.getEStructuralFeatures())) {
                    if (!(_csStaleF instanceof ${sourcePackageName}.EReference _csStaleRef)) continue;
                    if (!_csStaleRef.isContainment()) continue;
                    if (_csStaleRef.getEOpposite() != null) continue; // handled by CONTAINMENT_MULTI_BIDIRECTIONAL
                    String _csExpSlot = _csStaleRef.getName() + "_inverse";
                    if (!_csLiveSlotNames.contains(_csExpSlot)) {
                        EcoreUtil.remove(_csStaleRef);
                    }
                }
            }
        }
        // CONTAINMENT_MULTI_BIDIRECTIONAL backward incremental: remove stale containment EReference pairs
        // (run here before CS creation so a bidirectional ref that changed to unidirectional is
        // removed before the CS creation guard checks for an existing ref by the same name)
        {
            java.util.Set<String> _cmbLiveSlotNames2 = new java.util.HashSet<>();
            for (EObject _ls2 : allSourceObjects(targetModel)) {
                if (_hasAnnotation(_ls2, "containment") && _hasAnnotation(_ls2, "bidirectional")) {
                    String _lsn2 = _tlmGetStr(_ls2, _bwTLM_SNAME_F);
                    if (_lsn2 != null) _cmbLiveSlotNames2.add(_lsn2);
                }
            }
            for (EObject _cmbStaleObj2 : allSourceObjects(sourceModel)) {
                if (!(_cmbStaleObj2 instanceof ${sourcePackageName}.EClass _cmbStaleEC2)) continue;
                for (org.eclipse.emf.ecore.EStructuralFeature _cmbStaleF2 : new java.util.ArrayList<>(_cmbStaleEC2.getEStructuralFeatures())) {
                    if (!(_cmbStaleF2 instanceof ${sourcePackageName}.EReference _cmbStaleRef2)) continue;
                    if (!_cmbStaleRef2.isContainment()) continue;
                    ${sourcePackageName}.EReference _cmbStaleOpp2 = _cmbStaleRef2.getEOpposite();
                    if (_cmbStaleOpp2 == null) continue;
                    if (_cmbStaleOpp2.eContainer() == null) continue;
                    String _cmbExpSlot2 = _cmbStaleOpp2.getName() + "_inverse_" + _cmbStaleRef2.getName();
                    if (!_cmbLiveSlotNames2.contains(_cmbExpSlot2)) {
                        EcoreUtil.remove(_cmbStaleOpp2);
                        EcoreUtil.remove(_cmbStaleRef2);
                    }
                }
            }
        }
        // CONTAINMENT_SINGLE backward: slot with "containment"+"unidirectional" → EReference (containment) in parent EClass
        for (EObject _csBwSlot : allSourceObjects(targetModel)) {
            if (!(_hasAnnotation(_csBwSlot, "containment") && _hasAnnotation(_csBwSlot, "unidirectional"))) continue;
            EObject _csOwnerNode = _csBwSlot.eContainer();
            EObject _csFk = _tlmList(_csOwnerNode, _bwTLM_LINKS_F).stream()
                .filter(lk -> _tlmGetRef(lk, _bwTLM_SRC_F) == _csBwSlot && _hasAnnotation(lk, "containment") && _hasAnnotation(lk, "unidirectional"))
                .findFirst().orElse(null);
            if (_csFk == null) continue;
            EObject _csParentEO = corrIndex.inverse().get(_tlmGetRef(_csFk, _bwTLM_TGT_F));
            if (!(_csParentEO instanceof ${sourcePackageName}.EClass _csParentEClass)) continue;
            EObject _csChildEO = corrIndex.inverse().get(_csOwnerNode);
            if (!(_csChildEO instanceof ${sourcePackageName}.EClass _csChildEClass)) continue;
            String _csSlotName = _tlmGetStr(_csBwSlot, _bwTLM_SNAME_F);
            String _csRefName = _csSlotName != null && _csSlotName.endsWith("_inverse")
                ? _csSlotName.substring(0, _csSlotName.length() - "_inverse".length())
                : _csSlotName;
            final String _csRefNameF = _csRefName;
            if (_csParentEClass.getEStructuralFeatures().stream().anyMatch(f -> _csRefNameF.equals(f.getName()))) continue;
            ${sourcePackageName}.EReference _csRef = ${sourceFactory}.eINSTANCE.createEReference();
            _csRef.setName(_csRefName);
            _csRef.setEType(_csChildEClass);
            _csRef.setContainment(true);
            _csRef.setUpperBound(_hasAnnotation(_csBwSlot, "single") ? 1 : -1);
            _csParentEClass.getEStructuralFeatures().add(_csRef);
        }
</#if>
<#if tlm.fkType() == "CONTAINMENT_MULTI_BIDIRECTIONAL">
        // CONTAINMENT_MULTI_BIDIRECTIONAL backward incremental: remove stale containment EReference pairs
        {
            java.util.Set<String> _cmbLiveSlotNames = new java.util.HashSet<>();
            for (EObject _ls : allSourceObjects(targetModel)) {
                if (_hasAnnotation(_ls, "containment") && _hasAnnotation(_ls, "bidirectional")) {
                    String _lsn = _tlmGetStr(_ls, _bwTLM_SNAME_F);
                    if (_lsn != null) _cmbLiveSlotNames.add(_lsn);
                }
            }
            for (EObject _cmbStaleObj : allSourceObjects(sourceModel)) {
                if (!(_cmbStaleObj instanceof ${sourcePackageName}.EClass _cmbStaleEC)) continue;
                for (org.eclipse.emf.ecore.EStructuralFeature _cmbStaleF : new java.util.ArrayList<>(_cmbStaleEC.getEStructuralFeatures())) {
                    if (!(_cmbStaleF instanceof ${sourcePackageName}.EReference _cmbStaleRef)) continue;
                    if (!_cmbStaleRef.isContainment()) continue;
                    ${sourcePackageName}.EReference _cmbStaleOpp = _cmbStaleRef.getEOpposite();
                    if (_cmbStaleOpp == null) continue;
                    if (_cmbStaleOpp.eContainer() == null) continue; // already cleaned up
                    // Expected slot: {bwdRefName}_inverse_{fwdRefName}
                    String _cmbExpSlot = _cmbStaleOpp.getName() + "_inverse_" + _cmbStaleRef.getName();
                    if (!_cmbLiveSlotNames.contains(_cmbExpSlot)) {
                        EcoreUtil.remove(_cmbStaleOpp);
                        EcoreUtil.remove(_cmbStaleRef);
                    }
                }
            }
        }
        // CONTAINMENT_MULTI_BIDIRECTIONAL backward: slot with "containment"+"bidirectional" → EReference pair
        for (EObject _cmbBwSlot : allSourceObjects(targetModel)) {
            if (!(_hasAnnotation(_cmbBwSlot, "containment") && _hasAnnotation(_cmbBwSlot, "bidirectional"))) continue;
            EObject _cmbOwnerNode = _cmbBwSlot.eContainer();
            EObject _cmbFk = _tlmList(_cmbOwnerNode, _bwTLM_LINKS_F).stream()
                .filter(lk -> _tlmGetRef(lk, _bwTLM_SRC_F) == _cmbBwSlot && _hasAnnotation(lk, "containment") && _hasAnnotation(lk, "bidirectional"))
                .findFirst().orElse(null);
            if (_cmbFk == null) continue;
            EObject _cmbParentEO = corrIndex.inverse().get(_tlmGetRef(_cmbFk, _bwTLM_TGT_F));
            if (!(_cmbParentEO instanceof ${sourcePackageName}.EClass _cmbParentEClass)) continue;
            EObject _cmbChildEO = corrIndex.inverse().get(_cmbOwnerNode);
            if (!(_cmbChildEO instanceof ${sourcePackageName}.EClass _cmbChildEClass)) continue;
            String _cmbSlotName = _tlmGetStr(_cmbBwSlot, _bwTLM_SNAME_F);
            String _cmbFwdName = null, _cmbBwdName = null;
            if (_cmbSlotName != null && _cmbSlotName.contains("_inverse_")) {
                String[] _cmbParts = _cmbSlotName.split("_inverse_", 2);
                _cmbBwdName = _cmbParts[0];
                _cmbFwdName = _cmbParts[1];
            }
            if (_cmbFwdName == null || _cmbBwdName == null) continue;
            final String _cmbFwdNameF = _cmbFwdName, _cmbBwdNameF = _cmbBwdName;
            boolean _cmbFwdExists = _cmbParentEClass.getEStructuralFeatures().stream().anyMatch(f -> _cmbFwdNameF.equals(f.getName()));
            boolean _cmbBwdExists = _cmbChildEClass.getEStructuralFeatures().stream().anyMatch(f -> _cmbBwdNameF.equals(f.getName()));
            if (_cmbFwdExists && _cmbBwdExists) {
                ${sourcePackageName}.EReference _cmbFwdRef2 = (${sourcePackageName}.EReference) _cmbParentEClass.getEStructuralFeatures().stream()
                    .filter(f -> f instanceof ${sourcePackageName}.EReference && _cmbFwdNameF.equals(f.getName())).findFirst().orElse(null);
                ${sourcePackageName}.EReference _cmbBwdRef2 = (${sourcePackageName}.EReference) _cmbChildEClass.getEStructuralFeatures().stream()
                    .filter(f -> f instanceof ${sourcePackageName}.EReference && _cmbBwdNameF.equals(f.getName())).findFirst().orElse(null);
                if (_cmbFwdRef2 != null && _cmbBwdRef2 != null) { _cmbFwdRef2.setEOpposite(_cmbBwdRef2); _cmbBwdRef2.setEOpposite(_cmbFwdRef2); }
                continue;
            }
            ${sourcePackageName}.EReference _cmbFwdRef = ${sourceFactory}.eINSTANCE.createEReference();
            _cmbFwdRef.setName(_cmbFwdName);
            _cmbFwdRef.setEType(_cmbChildEClass);
            _cmbFwdRef.setContainment(true);
            _cmbFwdRef.setUpperBound(_hasAnnotation(_cmbBwSlot, "single") ? 1 : -1);
            _cmbParentEClass.getEStructuralFeatures().add(_cmbFwdRef);
            ${sourcePackageName}.EReference _cmbBwdRef = ${sourceFactory}.eINSTANCE.createEReference();
            _cmbBwdRef.setName(_cmbBwdName);
            _cmbBwdRef.setEType(_cmbParentEClass);
            _cmbChildEClass.getEStructuralFeatures().add(_cmbBwdRef);
            _cmbFwdRef.setEOpposite(_cmbBwdRef);
            _cmbBwdRef.setEOpposite(_cmbFwdRef);
        }
</#if>
</#list>
</#if>
