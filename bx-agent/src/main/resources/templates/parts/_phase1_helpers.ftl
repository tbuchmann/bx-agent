    private static EObject createAndMapObjects(EObject source, Map<EObject, EObject> objectMap, Set<EObject> visited, Options options) {
        if (source == null) {
            return null;
        }

        // Use visited set to prevent infinite loops (objectMap alone is not enough
        // since root elements are pre-seeded and must still be traversed)
        if (visited.contains(source)) {
            return objectMap.get(source);
        }
        visited.add(source);

        EObject target;
        if (objectMap.containsKey(source)) {
            // Pre-existing entry (e.g. root element) — use it, still traverse children
            target = objectMap.get(source);
        } else {
            target = null;

<#list typeMappings as typeMapping>
            // Handle ${typeMapping.sourceType()} → ${typeMapping.targetType()}
            if (source instanceof ${sourcePackageName}.${typeMapping.sourceType()}) {
                target = transform${typeMapping.sourceType()}((${sourcePackageName}.${typeMapping.sourceType()}) source, options);
            }<#if typeMapping?has_next> else</#if>
</#list>

            if (target != null) {
                objectMap.put(source, target);
            }
        }

        if (target != null) {

            // Recursively transform containment references
            // Cardinality (isMany) is resolved at runtime via EMF feature metadata,
            // avoiding reliance on potentially incorrect LLM-provided sourceIsMany/targetIsMany flags.
<#assign hasContainments = false>
<#list referenceMappings as refMapping>
    <#if refMapping.sourceIsContainment()>
        <#assign hasContainments = true>
            // Containment: ${refMapping.sourceRefOwnerType()}.${refMapping.sourceRef()} → ${refMapping.targetRefOwnerType()}.${refMapping.targetRef()}
            if (source instanceof ${sourcePackageName}.${refMapping.sourceRefOwnerType()} && target instanceof ${targetPackageName}.${refMapping.targetRefOwnerType()}) {
                org.eclipse.emf.ecore.EStructuralFeature _srcFeat = source.eClass().getEStructuralFeature("${refMapping.sourceRef()}");
                org.eclipse.emf.ecore.EStructuralFeature _tgtFeat = target.eClass().getEStructuralFeature("${refMapping.targetRef()}");
                if (_srcFeat != null && _tgtFeat != null) {
                    @SuppressWarnings("unchecked")
                    java.util.List<EObject> _srcItems = _srcFeat.isMany()
                        ? (EList<EObject>) source.eGet(_srcFeat)
                        : (source.eGet(_srcFeat) != null ? java.util.List.of((EObject) source.eGet(_srcFeat)) : java.util.List.of());
                    for (EObject _rawChild : _srcItems) {
                        if (_rawChild instanceof ${sourcePackageName}.${refMapping.sourceRefTargetType()} child) {
                            EObject transformedChild = createAndMapObjects(child, objectMap, visited, options);
                            if (transformedChild instanceof ${targetPackageName}.${refMapping.targetRefTargetType()}) {
                                if (_tgtFeat.isMany()) {
                                    @SuppressWarnings("unchecked")
                                    EList<EObject> _tgtList = (EList<EObject>) target.eGet(_tgtFeat);
                                    _tgtList.add(transformedChild);
                                } else {
                                    target.eSet(_tgtFeat, transformedChild);
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

        return target;
    }

<#if syntheticObjectMappings?has_content>
    // ════════════════════════════════════════════════════════════════════════
    // Phase 0: Synthetic Object Creation
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Creates fixed-structure synthetic target objects once per source container instance.
     * These objects have no source counterpart and are not tracked in objectMap.
     */
    @SuppressWarnings("unchecked")
    private static void _createSyntheticObjects(EObject srcContainer, EObject tgtContainer,
            Map<EObject, EObject> objectMap) {
    <#list syntheticObjectMappings as som>
        if (srcContainer instanceof ${sourcePackageName}.${som.sourceContainerType()} source) {
            org.eclipse.emf.ecore.EStructuralFeature _somContFeat =
                tgtContainer.eClass().getEStructuralFeature("${som.targetContainerRef()}");
            if (_somContFeat != null) {
                ${targetPackageName}.${som.targetType()} _syn =
                    ${targetFactory}.eINSTANCE.create${som.targetType()}();
                _syn.setName(${som.nameExpression()});
        <#list som.forwardAnnotations() as ann>
                _addAnnotation(_syn, "${ann}");
        </#list>
        <#list som.columns() as col>
                {
                    ${targetPackageName}.Column _col =
                        ${targetFactory}.eINSTANCE.createColumn();
                    _col.setName("${col.name()}");
                    _col.setType("${col.sqlType()}");
            <#list col.properties() as prop>
                    {
                        org.eclipse.emf.ecore.EStructuralFeature _pf =
                            _col.eClass().getEStructuralFeature("properties");
                        if (_pf != null && _pf.getEType() instanceof org.eclipse.emf.ecore.EEnum _pe) {
                            org.eclipse.emf.ecore.EEnumLiteral _lit = _pe.getEEnumLiteral("${prop}");
                            if (_lit != null)
                                ((java.util.List<Object>) _col.eGet(_pf)).add(_lit.getInstance());
                        }
                    }
            </#list>
                    ((java.util.List<EObject>) _syn.eGet(
                        _syn.eClass().getEStructuralFeature("ownedColumns"))).add(_col);
                }
        </#list>
        <#if som.createPrimaryKey()>
                {
                    java.util.List<?> _synCols = (java.util.List<?>)
                        _syn.eGet(_syn.eClass().getEStructuralFeature("ownedColumns"));
                    if (!_synCols.isEmpty()) {
                        ${targetPackageName}.PrimaryKey _pk =
                            ${targetFactory}.eINSTANCE.createPrimaryKey();
                        _pk.eSet(_pk.eClass().getEStructuralFeature("column"),
                            _synCols.get(0));
                        _syn.eSet(_syn.eClass().getEStructuralFeature("ownedPrimaryKey"), _pk);
                    }
                }
        </#if>
                if (_somContFeat.isMany()) {
                    ((java.util.List<EObject>) tgtContainer.eGet(_somContFeat)).add(_syn);
                } else {
                    tgtContainer.eSet(_somContFeat, _syn);
                }
            }
        }
    </#list>
    }

<#assign hasNestedSom2 = false>
<#if syntheticObjectMappings?has_content>
  <#list syntheticObjectMappings as som>
    <#if som.nestedInMappedTarget()><#assign hasNestedSom2 = true></#if>
  </#list>
</#if>
<#if hasNestedSom2>
    /**
     * Phase 1.5: For each source instance of the configured type, create a synthetic sub-object
     * inside the already-mapped target (found via objectMap).  The first entry of 'columns'
     * provides the sub-object's own type/properties; createPrimaryKey attaches a PrimaryKey
     * to the CONTAINER (mapped target), not to the created sub-object.
     */
    @SuppressWarnings("unchecked")
    private static void _createNestedSyntheticObjects(EObject src, Map<EObject, EObject> objectMap) {
  <#list syntheticObjectMappings as som>
    <#if som.nestedInMappedTarget()>
        if (src.eClass().getName().equals("${som.sourceContainerType()}")) {
            EObject _nsynSrcTyped = src;
            EObject _nsynContainer = objectMap.get(src);
            if (_nsynContainer == null) return;
            org.eclipse.emf.ecore.EStructuralFeature _nsynFeat =
                _nsynContainer.eClass().getEStructuralFeature("${som.targetContainerRef()}");
            if (_nsynFeat == null) return;
            // Skip if already present (idempotent)
            {
                EObject _nsynSrcForName = _nsynSrcTyped;
                String _nsynExpectedName = String.valueOf(${som.nameExpression()});
                if (_nsynFeat.isMany()) {
                    java.util.List<?> _nsynExisting = (java.util.List<?>) _nsynContainer.eGet(_nsynFeat);
                    boolean _nsynFound = _nsynExisting.stream().anyMatch(o ->
                        o instanceof EObject _neo &&
                        _nsynExpectedName.equals(_neo.eGet(_neo.eClass().getEStructuralFeature("name"))));
                    if (_nsynFound) return;
                } else {
                    EObject _nsynExisting = (EObject) _nsynContainer.eGet(_nsynFeat);
                    if (_nsynExisting != null) return;
                }
            }
            ${targetPackageName}.${som.targetType()} _nsyn =
                ${targetFactory}.eINSTANCE.create${som.targetType()}();
            {
                EObject _nsynSrcForName = _nsynSrcTyped;
                _nsyn.setName(${som.nameExpression()});
            }
      <#if som.columns()?has_content>
            // Apply type and properties from columns[0] to the created sub-object
            {
                org.eclipse.emf.ecore.EStructuralFeature _nsynTypeFeat =
                    _nsyn.eClass().getEStructuralFeature("type");
                if (_nsynTypeFeat != null) _nsyn.eSet(_nsynTypeFeat, "${som.columns()[0].sqlType()}");
        <#list som.columns()[0].properties() as prop>
                {
                    org.eclipse.emf.ecore.EStructuralFeature _nsynPropFeat =
                        _nsyn.eClass().getEStructuralFeature("properties");
                    if (_nsynPropFeat != null && _nsynPropFeat.isMany()) {
                        org.eclipse.emf.ecore.EEnum _nsynPropEnum = (org.eclipse.emf.ecore.EEnum)
                            _nsynPropFeat.getEType();
                        org.eclipse.emf.ecore.EEnumLiteral _nsynLit =
                            _nsynPropEnum.getEEnumLiteral("${prop}");
                        if (_nsynLit != null)
                            ((java.util.List<Object>) _nsyn.eGet(_nsynPropFeat)).add(_nsynLit.getInstance());
                    }
                }
        </#list>
            }
      </#if>
      <#list som.forwardAnnotations() as ann>
            _addAnnotation(_nsyn, "${ann}");
      </#list>
            if (_nsynFeat.isMany()) {
                ((java.util.List<EObject>) _nsynContainer.eGet(_nsynFeat)).add(_nsyn);
            } else {
                _nsynContainer.eSet(_nsynFeat, _nsyn);
            }
      <#if som.createPrimaryKey()>
            // Create PrimaryKey in the container (not in the sub-object)
            {
                ${targetPackageName}.PrimaryKey _nsynPk =
                    ${targetFactory}.eINSTANCE.createPrimaryKey();
                _nsynPk.eSet(_nsynPk.eClass().getEStructuralFeature("column"), _nsyn);
                _nsynContainer.eSet(_nsynContainer.eClass().getEStructuralFeature("ownedPrimaryKey"), _nsynPk);
            }
      </#if>
        }
    </#if>
  </#list>
    }
</#if>
</#if>

<#if targetLinkMappings?has_content && targetLinkMetamodel??>
    // ════════════════════════════════════════════════════════════════════════
    // Phase 1.6: Target Link Generation (reflective, metamodel-agnostic)
    // ════════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private static void _createTargetLinks(Map<EObject, EObject> srcToTgt, Resource targetResource) {
        // TargetLinkMetamodel constants — all feature names come from the JSON mapping
        final String TLM_LINK    = "${targetLinkMetamodel.linkEClass()}";
        final String TLM_SLOT    = "${targetLinkMetamodel.slotEClass()}";
        final String TLM_CONS    = "${targetLinkMetamodel.constraintEClass()!}";
        final String TLM_LINKS_F = "${targetLinkMetamodel.linkContainerFeature()}";
        final String TLM_SRC_F   = "${targetLinkMetamodel.linkSourceFeature()}";
        final String TLM_TGT_F   = "${targetLinkMetamodel.linkTargetFeature()}";
        final String TLM_CONS_F  = "${targetLinkMetamodel.linkConstraintsFeature()!}";
        final String TLM_SLOTS_F = "${targetLinkMetamodel.slotContainerFeature()}";
        final String TLM_NODES_F = "${targetLinkMetamodel.nodeContainerFeature()}";
        final String TLM_NNAME_F = "${targetLinkMetamodel.nodeNameFeature()}";
        final String TLM_SNAME_F = "${targetLinkMetamodel.slotNameFeature()}";
        final String TLM_STYPE_F = "${targetLinkMetamodel.slotTypeFeature()}";
        final String TLM_DEF_T   = "${targetLinkMetamodel.defaultSlotType()}";
        final String TLM_COND_F  = "${targetLinkMetamodel.constraintConditionFeature()!}";
        final String TLM_ACT_F   = "${targetLinkMetamodel.constraintActionFeature()!}";
        final String TLM_PROP_F  = "${targetLinkMetamodel.slotPropertiesFeature()!}";

        // Locate the named anchor node (for ROOT / EOBJECT_TYPE_COLUMN lookups)
        EObject _tlmAnchorNode = null;
        for (EObject _tlmRoot : targetResource.getContents()) {
            for (EObject _tlmN : _tlmList(_tlmRoot, TLM_NODES_F)) {
                String _tlmNName = _tlmGetStr(_tlmN, TLM_NNAME_F);
    <#list targetLinkMappings as tlm>
    <#if tlm.fkType() == "ROOT">
                if ("${tlm.rootTableName()}".equals(_tlmNName)) { _tlmAnchorNode = _tlmN; }
    </#if>
    <#if tlm.fkType() == "EOBJECT_TYPE_COLUMN">
                if ("${tlm.eObjectTableName()}".equals(_tlmNName)) { _tlmAnchorNode = _tlmN; }
    </#if>
    </#list>
            }
        }

        // Pre-cleanup: remove all previously-generated TLM objects so they are recreated fresh.
        // In batch mode this is a no-op (target is empty); in incremental mode it removes stale
        // FKs/columns/association-nodes left over from earlier relationship states.
        for (EObject _pcRoot : targetResource.getContents()) {
            // Remove association nodes (BIDIRECTIONAL_CROSS_REF tables annotated "bidirectional"+"cross")
            _tlmList(_pcRoot, TLM_NODES_F).removeIf(n -> _hasAnnotation(n, "bidirectional") && _hasAnnotation(n, "cross"));
            for (EObject _pcNode : new ArrayList<>(_tlmList(_pcRoot, TLM_NODES_F))) {
                // Remove all FKs — recreated below from current corrIndex/objectMap
                _tlmList(_pcNode, TLM_LINKS_F).clear();
                // Remove CONTAINMENT columns added by _createTargetLinks (CONTAINMENT_SINGLE/MULTI)
                _tlmList(_pcNode, TLM_SLOTS_F).removeIf(s -> _hasAnnotation(s, "containment"));
            }
        }
        // Remove EOBJECT_TYPE_COLUMN slots from anchor node that have no active EClass in corrIndex
        if (_tlmAnchorNode != null) {
            java.util.Set<String> _activeNodeNames = new java.util.HashSet<>();
            for (EObject _anTgt : srcToTgt.values()) {
                if (_tlmIsNodeOf(_anTgt, TLM_NODES_F)) {
                    String _anName = _tlmGetStr(_anTgt, TLM_NNAME_F);
                    if (_anName != null) _activeNodeNames.add(_anName);
                }
            }
            final EObject _pcAnchor = _tlmAnchorNode;
            _tlmList(_pcAnchor, TLM_SLOTS_F).removeIf(s -> {
                String _sName = _tlmGetStr(s, TLM_SNAME_F);
                return _sName != null && !"id".equals(_sName) && !_activeNodeNames.contains(_sName);
            });
        }

        for (Map.Entry<EObject, EObject> _fkEntry : new java.util.ArrayList<>(srcToTgt.entrySet())) {
            EObject _fkSrc = _fkEntry.getKey();
            EObject _fkTgt = _fkEntry.getValue();

    <#list targetLinkMappings as tlm>
    <#if tlm.fkType() == "INHERITANCE">
            // INHERITANCE: EClass with supertype → link id-slot → supertype node
            if (_fkSrc.eClass().getName().equals("EClass") && _tlmIsNodeOf(_fkTgt, TLM_NODES_F)) {
                EObject _fkNode = _fkTgt;
                Object _stVal = _fkSrc.eGet(_fkSrc.eClass().getEStructuralFeature("eSuperTypes"));
                java.util.List<?> _stList = _stVal instanceof java.util.List<?> _l ? _l : java.util.List.of();
                for (Object _st : _stList) {
                    if (!(_st instanceof EObject _superEClass)) continue;
                    EObject _superNode = srcToTgt.get(_superEClass);
                    if (_superNode == null) continue;
                    EObject _idSlot = _tlmList(_fkNode, TLM_SLOTS_F).stream()
                        .filter(s -> "id".equals(_tlmGetStr(s, TLM_SNAME_F))).findFirst().orElse(null);
                    if (_idSlot == null) continue;
                    boolean _exists = _tlmList(_fkNode, TLM_LINKS_F).stream().anyMatch(lk ->
                        _tlmGetRef(lk, TLM_SRC_F) == _idSlot && _tlmGetRef(lk, TLM_TGT_F) == _superNode);
                    if (_exists) continue;
                    EObject _fk = _tlmCreateObj(TLM_LINK);
                    _tlmSet(_fk, TLM_SRC_F, _idSlot);
                    _tlmSet(_fk, TLM_TGT_F, _superNode);
                    _tlmList(_fkNode, TLM_LINKS_F).add(_fk);
                    _tlmAddConstraint(_fk, TLM_CONS, TLM_CONS_F, TLM_COND_F, TLM_ACT_F, "Delete", "${tlm.deleteEvent()}");
                    <#list tlm.fkAnnotations() as ann>
                    _addAnnotation(_fk, "${ann}");
                    </#list>
                }
            }
    </#if>
    <#if tlm.fkType() == "ROOT">
            // ROOT: EClass with no supertype → link id-slot → anchor node (${tlm.rootTableName()})
            if (_fkSrc.eClass().getName().equals("EClass") && _tlmIsNodeOf(_fkTgt, TLM_NODES_F) && _tlmAnchorNode != null) {
                EObject _fkNode = _fkTgt;
                Object _stVal = _fkSrc.eGet(_fkSrc.eClass().getEStructuralFeature("eSuperTypes"));
                java.util.List<?> _stList = _stVal instanceof java.util.List<?> _l ? _l : java.util.List.of();
                if (_stList.isEmpty()) {
                    EObject _idSlot = _tlmList(_fkNode, TLM_SLOTS_F).stream()
                        .filter(s -> "id".equals(_tlmGetStr(s, TLM_SNAME_F))).findFirst().orElse(null);
                    if (_idSlot != null) {
                        EObject _rootNode = _tlmAnchorNode;
                        boolean _exists = _tlmList(_fkNode, TLM_LINKS_F).stream().anyMatch(lk ->
                            _tlmGetRef(lk, TLM_SRC_F) == _idSlot && _tlmGetRef(lk, TLM_TGT_F) == _rootNode);
                        if (!_exists) {
                            EObject _fk = _tlmCreateObj(TLM_LINK);
                            _tlmSet(_fk, TLM_SRC_F, _idSlot);
                            _tlmSet(_fk, TLM_TGT_F, _rootNode);
                            _tlmList(_fkNode, TLM_LINKS_F).add(_fk);
                            _tlmAddConstraint(_fk, TLM_CONS, TLM_CONS_F, TLM_COND_F, TLM_ACT_F, "Delete", "${tlm.deleteEvent()}");
                            <#list tlm.fkAnnotations() as ann>
                            _addAnnotation(_fk, "${ann}");
                            </#list>
                        }
                    }
                }
            }
    </#if>
    <#if tlm.fkType() == "CROSS_REF">
            // CROSS_REF: non-containment single EReference → link ref-slot → target EClass node
            if (_fkSrc.eClass().getName().equals("EReference") && _tlmIsSlotOf(_fkTgt, TLM_SLOTS_F)) {
                EObject _fkSlot = _fkTgt;
                Object _isConf = _fkSrc.eGet(_fkSrc.eClass().getEStructuralFeature("containment"));
                Object _ub = _fkSrc.eGet(_fkSrc.eClass().getEStructuralFeature("upperBound"));
                if (Boolean.FALSE.equals(_isConf) && Integer.valueOf(1).equals(_ub)) {
                    EObject _ownerNode = (EObject) _fkSlot.eContainer();
                    Object _eTypeObj = _fkSrc.eGet(_fkSrc.eClass().getEStructuralFeature("eType"));
                    if (_eTypeObj instanceof EObject _targetEClass) {
                        EObject _refNode = srcToTgt.get(_targetEClass);
                        if (_refNode != null) {
                            boolean _exists = _tlmList(_ownerNode, TLM_LINKS_F).stream().anyMatch(lk ->
                                _tlmGetRef(lk, TLM_SRC_F) == _fkSlot && _tlmGetRef(lk, TLM_TGT_F) == _refNode);
                            if (!_exists) {
                                EObject _fk = _tlmCreateObj(TLM_LINK);
                                _tlmSet(_fk, TLM_SRC_F, _fkSlot);
                                _tlmSet(_fk, TLM_TGT_F, _refNode);
                                _tlmList(_ownerNode, TLM_LINKS_F).add(_fk);
                                _tlmAddConstraint(_fk, TLM_CONS, TLM_CONS_F, TLM_COND_F, TLM_ACT_F, "Delete", "${tlm.deleteEvent()}");
                                <#list tlm.fkAnnotations() as ann>
                                _addAnnotation(_fk, "${ann}");
                                </#list>
                            }
                        }
                    }
                }
            }
    </#if>
    <#if tlm.fkType() == "EOBJECT_TYPE_COLUMN">
            // EOBJECT_TYPE_COLUMN: for each EClass, add slot+link to anchor node (${tlm.eObjectTableName()})
            if (_fkSrc.eClass().getName().equals("EClass") && _tlmIsNodeOf(_fkTgt, TLM_NODES_F) && _tlmAnchorNode != null) {
                EObject _fkClassNode = _fkTgt;
                final String _className = _tlmGetStr(_fkClassNode, TLM_NNAME_F);
                EObject _existingSlot = _tlmList(_tlmAnchorNode, TLM_SLOTS_F).stream()
                    .filter(s -> _className.equals(_tlmGetStr(s, TLM_SNAME_F))).findFirst().orElse(null);
                if (_existingSlot == null) {
                    EObject _typeSlot = _tlmCreateObj(TLM_SLOT);
                    _tlmSet(_typeSlot, TLM_SNAME_F, _className);
                    _tlmSet(_typeSlot, TLM_STYPE_F, TLM_DEF_T);
                    _tlmAddPropLiteral(_typeSlot, TLM_PROP_F, "Unique");
                    _tlmList(_tlmAnchorNode, TLM_SLOTS_F).add(_typeSlot);
                    EObject _fk = _tlmCreateObj(TLM_LINK);
                    _tlmSet(_fk, TLM_SRC_F, _typeSlot);
                    _tlmSet(_fk, TLM_TGT_F, _fkClassNode);
                    _tlmList(_tlmAnchorNode, TLM_LINKS_F).add(_fk);
                    _tlmAddConstraint(_fk, TLM_CONS, TLM_CONS_F, TLM_COND_F, TLM_ACT_F, "Delete", "${tlm.deleteEvent()}");
                    <#list tlm.fkAnnotations() as ann>
                    _addAnnotation(_fk, "${ann}");
                    </#list>
                } else {
                    // Slot exists — repair link if its target was nulled by a prior deletion
                    EObject _repairSlot = _existingSlot;
                    boolean _fkOk = _tlmList(_tlmAnchorNode, TLM_LINKS_F).stream().anyMatch(lk ->
                        _tlmGetRef(lk, TLM_SRC_F) == _repairSlot && _tlmGetRef(lk, TLM_TGT_F) == _fkClassNode);
                    if (!_fkOk) {
                        _tlmList(_tlmAnchorNode, TLM_LINKS_F).removeIf(lk -> _tlmGetRef(lk, TLM_SRC_F) == _repairSlot);
                        EObject _repairFk = _tlmCreateObj(TLM_LINK);
                        _tlmSet(_repairFk, TLM_SRC_F, _repairSlot);
                        _tlmSet(_repairFk, TLM_TGT_F, _fkClassNode);
                        _tlmList(_tlmAnchorNode, TLM_LINKS_F).add(_repairFk);
                        _tlmAddConstraint(_repairFk, TLM_CONS, TLM_CONS_F, TLM_COND_F, TLM_ACT_F, "Delete", "${tlm.deleteEvent()}");
                    }
                }
            }
    </#if>
    </#list>
        }

    <#list targetLinkMappings as tlm>
    <#if tlm.fkType() == "BIDIRECTIONAL_CROSS_REF">
        // BIDIRECTIONAL_CROSS_REF: bidirectional non-containment EReferences → association node
        EObject _bxrfRootNode = null;
        for (EObject _bxrfR : targetResource.getContents()) { if (!_tlmList(_bxrfR, TLM_NODES_F).isEmpty()) { _bxrfRootNode = _bxrfR; break; } }
        if (_bxrfRootNode != null) {
            final EObject _bxrfRoot = _bxrfRootNode;
            for (EObject _bxrfSrcEO : new java.util.ArrayList<>(srcToTgt.keySet())) {
                if (!(_bxrfSrcEO instanceof ${sourcePackageName}.EClass _bxrfOwnerClass)) continue;
                for (Object _bxrfSfObj : new java.util.ArrayList<>(_bxrfOwnerClass.getEStructuralFeatures())) {
                    if (!(_bxrfSfObj instanceof ${sourcePackageName}.EReference _bxrfRef)) continue;
                    if (_bxrfRef.isContainment()) continue;
                    ${sourcePackageName}.EReference _bxrfOpp = _bxrfRef.getEOpposite();
                    if (_bxrfOpp == null) continue;
                    { Object _oppCont = _bxrfOpp.eGet(_bxrfOpp.eClass().getEStructuralFeature("containment")); if (Boolean.TRUE.equals(_oppCont)) continue; }
                    if (!(_bxrfRef.getEType() instanceof ${sourcePackageName}.EClass _bxrfTgtClass)) continue;
                    String _bxrfOwnerName = _bxrfOwnerClass.getName();
                    String _bxrfTgtName = _bxrfTgtClass.getName();
                    int _bxrfCmp = _bxrfOwnerName.compareTo(_bxrfTgtName);
                    if (_bxrfCmp > 0) continue;
                    if (_bxrfCmp == 0 && _bxrfRef.getName().compareTo(_bxrfOpp.getName()) > 0) continue;
                    String _bxrfRefName = _bxrfRef.getName();
                    String _bxrfOppName = _bxrfOpp.getName();
                    final String _bxrfNodeName = _bxrfOwnerName + "_" + _bxrfRefName + "_inverse_" + _bxrfTgtName + "_" + _bxrfOppName;
                    if (_tlmList(_bxrfRoot, TLM_NODES_F).stream().anyMatch(n -> _bxrfNodeName.equals(_tlmGetStr(n, TLM_NNAME_F)))) continue;
                    EObject _bxrfOwnerNodeEO = srcToTgt.get(_bxrfOwnerClass);
                    EObject _bxrfTgtNodeEO = srcToTgt.get(_bxrfTgtClass);
                    if (_bxrfOwnerNodeEO == null || _bxrfTgtNodeEO == null) continue;
                    EObject _bxrfAssoc = _tlmCreateObj(TLM_LINK.equals("") ? "Node" : _tlmNodeEClassName(targetResource, TLM_NODES_F));
                    _tlmSet(_bxrfAssoc, TLM_NNAME_F, _bxrfNodeName);
                    _tlmList(_bxrfRoot, TLM_NODES_F).add(_bxrfAssoc);
                    <#list tlm.fkAnnotations() as ann>
                    _addAnnotation(_bxrfAssoc, "${ann}");
                    </#list>
                    { Object _fwdUb = _bxrfRef.eGet(_bxrfRef.eClass().getEStructuralFeature("upperBound")); if (Integer.valueOf(1).equals(_fwdUb)) _addAnnotation(_bxrfAssoc, "forwardSingle"); }
                    { Object _bwdUb = _bxrfOpp.eGet(_bxrfOpp.eClass().getEStructuralFeature("upperBound")); if (Integer.valueOf(1).equals(_bwdUb)) _addAnnotation(_bxrfAssoc, "backwardSingle"); }
                    EObject _bxrfSrcSlot = _tlmCreateObj(TLM_SLOT);
                    _tlmSet(_bxrfSrcSlot, TLM_SNAME_F, "source");
                    _tlmSet(_bxrfSrcSlot, TLM_STYPE_F, TLM_DEF_T);
                    <#if tlm.columnProperties()?has_content>
                    <#list tlm.columnProperties() as prop>
                    _tlmAddPropLiteral(_bxrfSrcSlot, TLM_PROP_F, "${prop}");
                    </#list>
                    </#if>
                    _tlmList(_bxrfAssoc, TLM_SLOTS_F).add(_bxrfSrcSlot);
                    EObject _bxrfTgtSlot = _tlmCreateObj(TLM_SLOT);
                    _tlmSet(_bxrfTgtSlot, TLM_SNAME_F, "target");
                    _tlmSet(_bxrfTgtSlot, TLM_STYPE_F, TLM_DEF_T);
                    <#if tlm.columnProperties()?has_content>
                    <#list tlm.columnProperties() as prop>
                    _tlmAddPropLiteral(_bxrfTgtSlot, TLM_PROP_F, "${prop}");
                    </#list>
                    </#if>
                    _tlmList(_bxrfAssoc, TLM_SLOTS_F).add(_bxrfTgtSlot);
                    EObject _bxrfFk1 = _tlmCreateObj(TLM_LINK);
                    _tlmSet(_bxrfFk1, TLM_SRC_F, _bxrfSrcSlot);
                    _tlmSet(_bxrfFk1, TLM_TGT_F, _bxrfOwnerNodeEO);
                    _tlmList(_bxrfAssoc, TLM_LINKS_F).add(_bxrfFk1);
                    _tlmAddConstraint(_bxrfFk1, TLM_CONS, TLM_CONS_F, TLM_COND_F, TLM_ACT_F, "Delete", "${tlm.deleteEvent()}");
                    EObject _bxrfFk2 = _tlmCreateObj(TLM_LINK);
                    _tlmSet(_bxrfFk2, TLM_SRC_F, _bxrfTgtSlot);
                    _tlmSet(_bxrfFk2, TLM_TGT_F, _bxrfTgtNodeEO);
                    _tlmList(_bxrfAssoc, TLM_LINKS_F).add(_bxrfFk2);
                    _tlmAddConstraint(_bxrfFk2, TLM_CONS, TLM_CONS_F, TLM_COND_F, TLM_ACT_F, "Delete", "${tlm.deleteEvent()}");
                }
            }
        }
    </#if>
    <#if tlm.fkType() == "CONTAINMENT_SINGLE">
        // CONTAINMENT_SINGLE: containment single unidirectional EReference → {refName}_inverse slot in child node + link → parent
        for (EObject _csfSrcEO : new java.util.ArrayList<>(srcToTgt.keySet())) {
            if (!(_csfSrcEO instanceof ${sourcePackageName}.EClass _csfOwnerClass)) continue;
            for (Object _csfSfObj : new java.util.ArrayList<>(_csfOwnerClass.getEStructuralFeatures())) {
                if (!(_csfSfObj instanceof ${sourcePackageName}.EReference _csfRef)) continue;
                if (!_csfRef.isContainment()) continue;
                if (_csfRef.getEOpposite() != null) continue;
                if (!(_csfRef.getEType() instanceof ${sourcePackageName}.EClass _csfChildClass)) continue;
                EObject _csfParentNode = srcToTgt.get(_csfOwnerClass);
                EObject _csfChildNode = srcToTgt.get(_csfChildClass);
                if (_csfParentNode == null || _csfChildNode == null) continue;
                final String _csfColName = _csfRef.getName() + "_inverse";
                if (_tlmList(_csfChildNode, TLM_SLOTS_F).stream().anyMatch(s -> _csfColName.equals(_tlmGetStr(s, TLM_SNAME_F)))) continue;
                EObject _csfSlot = _tlmCreateObj(TLM_SLOT);
                _tlmSet(_csfSlot, TLM_SNAME_F, _csfColName);
                _tlmSet(_csfSlot, TLM_STYPE_F, TLM_DEF_T);
                _tlmList(_csfChildNode, TLM_SLOTS_F).add(_csfSlot);
                _addAnnotation(_csfSlot, "containment");
                _addAnnotation(_csfSlot, "unidirectional");
                { Object _csfUb = _csfRef.eGet(_csfRef.eClass().getEStructuralFeature("upperBound")); _addAnnotation(_csfSlot, Integer.valueOf(1).equals(_csfUb) ? "single" : "multi"); }
                EObject _csfFk = _tlmCreateObj(TLM_LINK);
                _tlmSet(_csfFk, TLM_SRC_F, _csfSlot);
                _tlmSet(_csfFk, TLM_TGT_F, _csfParentNode);
                _tlmList(_csfChildNode, TLM_LINKS_F).add(_csfFk);
                _tlmAddConstraint(_csfFk, TLM_CONS, TLM_CONS_F, TLM_COND_F, TLM_ACT_F, "Delete", "${tlm.deleteEvent()}");
                _addAnnotation(_csfFk, "containment");
                _addAnnotation(_csfFk, "unidirectional");
                { Object _csfUb2 = _csfRef.eGet(_csfRef.eClass().getEStructuralFeature("upperBound")); _addAnnotation(_csfFk, Integer.valueOf(1).equals(_csfUb2) ? "single" : "multi"); }
            }
        }
    </#if>
    <#if tlm.fkType() == "CONTAINMENT_MULTI_BIDIRECTIONAL">
        // CONTAINMENT_MULTI_BIDIRECTIONAL: containment multi EReference with eOpposite → {bwdRef}_inverse_{fwdRef} slot in child node + link → parent
        for (EObject _cmbfSrcEO : new java.util.ArrayList<>(srcToTgt.keySet())) {
            if (!(_cmbfSrcEO instanceof ${sourcePackageName}.EClass _cmbfOwnerClass)) continue;
            for (Object _cmbfSfObj : new java.util.ArrayList<>(_cmbfOwnerClass.getEStructuralFeatures())) {
                if (!(_cmbfSfObj instanceof ${sourcePackageName}.EReference _cmbfRef)) continue;
                if (!_cmbfRef.isContainment()) continue;
                ${sourcePackageName}.EReference _cmbfOpp = _cmbfRef.getEOpposite();
                if (_cmbfOpp == null) continue;
                if (!(_cmbfRef.getEType() instanceof ${sourcePackageName}.EClass _cmbfChildClass)) continue;
                EObject _cmbfParentNode = srcToTgt.get(_cmbfOwnerClass);
                EObject _cmbfChildNode = srcToTgt.get(_cmbfChildClass);
                if (_cmbfParentNode == null || _cmbfChildNode == null) continue;
                final String _cmbfColName = _cmbfOpp.getName() + "_inverse_" + _cmbfRef.getName();
                if (_tlmList(_cmbfChildNode, TLM_SLOTS_F).stream().anyMatch(s -> _cmbfColName.equals(_tlmGetStr(s, TLM_SNAME_F)))) continue;
                EObject _cmbfSlot = _tlmCreateObj(TLM_SLOT);
                _tlmSet(_cmbfSlot, TLM_SNAME_F, _cmbfColName);
                _tlmSet(_cmbfSlot, TLM_STYPE_F, TLM_DEF_T);
                _tlmList(_cmbfChildNode, TLM_SLOTS_F).add(_cmbfSlot);
                _addAnnotation(_cmbfSlot, "bidirectional");
                _addAnnotation(_cmbfSlot, "containment");
                { Object _cmbfUb = _cmbfRef.eGet(_cmbfRef.eClass().getEStructuralFeature("upperBound")); _addAnnotation(_cmbfSlot, Integer.valueOf(1).equals(_cmbfUb) ? "single" : "multi"); }
                EObject _cmbfFk = _tlmCreateObj(TLM_LINK);
                _tlmSet(_cmbfFk, TLM_SRC_F, _cmbfSlot);
                _tlmSet(_cmbfFk, TLM_TGT_F, _cmbfParentNode);
                _tlmList(_cmbfChildNode, TLM_LINKS_F).add(_cmbfFk);
                _tlmAddConstraint(_cmbfFk, TLM_CONS, TLM_CONS_F, TLM_COND_F, TLM_ACT_F, "Delete", "${tlm.deleteEvent()}");
                _addAnnotation(_cmbfFk, "bidirectional");
                _addAnnotation(_cmbfFk, "containment");
                { Object _cmbfUb2 = _cmbfRef.eGet(_cmbfRef.eClass().getEStructuralFeature("upperBound")); _addAnnotation(_cmbfFk, Integer.valueOf(1).equals(_cmbfUb2) ? "single" : "multi"); }
            }
        }
    </#if>
    </#list>
        // Multi-EAttribute nodes: populate id + value slots and link → parent EClass node
        for (Map.Entry<EObject, EObject> _maEntry : new java.util.ArrayList<>(srcToTgt.entrySet())) {
            EObject _maSrc = _maEntry.getKey();
            EObject _maTgt = _maEntry.getValue();
            if (!"EAttribute".equals(_maSrc.eClass().getName())) continue;
            if (!_tlmIsNodeOf(_maTgt, TLM_NODES_F)) continue;
            if (!_hasAnnotation(_maTgt, "multi")) continue;
            EObject _maParentNode = srcToTgt.get(_maSrc.eContainer());
            if (_maParentNode == null) continue;
            EObject _maNode = _maTgt;
            EObject _maIdSlot = _tlmList(_maNode, TLM_SLOTS_F).stream()
                .filter(s -> "id".equals(_tlmGetStr(s, TLM_SNAME_F))).findFirst().orElse(null);
            if (_maIdSlot == null) {
                _maIdSlot = _tlmCreateObj(TLM_SLOT);
                _tlmSet(_maIdSlot, TLM_SNAME_F, "id");
                _tlmSet(_maIdSlot, TLM_STYPE_F, TLM_DEF_T);
                _tlmAddPropLiteral(_maIdSlot, TLM_PROP_F, "NotNull");
                _tlmList(_maNode, TLM_SLOTS_F).add(_maIdSlot);
            }
            final EObject _maIdSlotF = _maIdSlot;
            if (_tlmList(_maNode, TLM_LINKS_F).stream().noneMatch(lk -> _tlmGetRef(lk, TLM_SRC_F) == _maIdSlotF)) {
                EObject _maFk = _tlmCreateObj(TLM_LINK);
                _tlmSet(_maFk, TLM_SRC_F, _maIdSlotF);
                _tlmSet(_maFk, TLM_TGT_F, _maParentNode);
                _tlmList(_maNode, TLM_LINKS_F).add(_maFk);
                _tlmAddConstraint(_maFk, TLM_CONS, TLM_CONS_F, TLM_COND_F, TLM_ACT_F, "Delete", "Cascade");
            }
            if (_tlmList(_maNode, TLM_SLOTS_F).stream().noneMatch(s -> "value".equals(_tlmGetStr(s, TLM_SNAME_F)))) {
                EObject _maValSlot = _tlmCreateObj(TLM_SLOT);
                _tlmSet(_maValSlot, TLM_SNAME_F, "value");
                Object _maEType = _maSrc.eGet(_maSrc.eClass().getEStructuralFeature("eType"));
                String _maRawType = "java.lang.String";
                if (_maEType instanceof EObject _maETypeObj) {
                    Object _maIcn = _maETypeObj.eGet(_maETypeObj.eClass().getEStructuralFeature("instanceClassName"));
                    if (_maIcn != null) _maRawType = _maIcn.toString();
                }
                java.util.Map<String,String> _maSqlTypeMap = new java.util.HashMap<>();
                <#list sqlTypeMapping?keys as k>_maSqlTypeMap.put("${k}", "${sqlTypeMapping[k]}");
                </#list>
                _tlmSet(_maValSlot, TLM_STYPE_F, _maSqlTypeMap.getOrDefault(_maRawType, _maRawType));
                _tlmAddPropLiteral(_maValSlot, TLM_PROP_F, "NotNull");
                _tlmList(_maNode, TLM_SLOTS_F).add(_maValSlot);
            }
        }
    }

    /** Creates a target EObject by EClass name using the target package factory. */
    private static EObject _tlmCreateObj(String eClassName) {
        org.eclipse.emf.ecore.EClass _eClass = (org.eclipse.emf.ecore.EClass)
            ${targetFactory}.eINSTANCE.getEPackage().getEClassifier(eClassName);
        return ${targetFactory}.eINSTANCE.getEPackage().getEFactoryInstance().create(_eClass);
    }

    /** Returns the EClass name of the first node element found in the target resource. */
    private static String _tlmNodeEClassName(Resource targetResource, String nodesFeat) {
        for (EObject root : targetResource.getContents()) {
            java.util.List<EObject> nodes = _tlmList(root, nodesFeat);
            if (!nodes.isEmpty()) return nodes.get(0).eClass().getName();
        }
        return "Node";
    }

    /** Returns true if obj is a node (its container has the nodesFeat). */
    private static boolean _tlmIsNodeOf(EObject obj, String nodesFeat) {
        if (obj == null || obj.eContainer() == null) return false;
        return obj.eContainer().eClass().getEStructuralFeature(nodesFeat) != null;
    }

    /** Returns true if obj is a slot (its container has the slotsFeat — same check as node). */
    private static boolean _tlmIsSlotOf(EObject obj, String slotsFeat) {
        return _tlmIsNodeOf(obj, slotsFeat);
    }

    /** Returns string value of a named feature on obj, or null. */
    private static String _tlmGetStr(EObject obj, String feat) {
        if (obj == null || feat == null || feat.isEmpty()) return null;
        org.eclipse.emf.ecore.EStructuralFeature f = obj.eClass().getEStructuralFeature(feat);
        if (f == null) return null;
        Object v = obj.eGet(f);
        return v != null ? v.toString() : null;
    }

    /** Returns single EObject ref from a named feature, or null. */
    private static EObject _tlmGetRef(EObject obj, String feat) {
        if (obj == null || feat == null || feat.isEmpty()) return null;
        org.eclipse.emf.ecore.EStructuralFeature f = obj.eClass().getEStructuralFeature(feat);
        if (f == null) return null;
        Object v = obj.eGet(f);
        return v instanceof EObject eo ? eo : null;
    }

    /** Sets a named feature on obj to val. */
    private static void _tlmSet(EObject obj, String feat, Object val) {
        if (obj == null || feat == null || feat.isEmpty()) return;
        org.eclipse.emf.ecore.EStructuralFeature f = obj.eClass().getEStructuralFeature(feat);
        if (f != null) obj.eSet(f, val);
    }

    /** Returns list of EObjects for a named isMany feature, or empty list. */
    @SuppressWarnings("unchecked")
    private static java.util.List<EObject> _tlmList(EObject obj, String feat) {
        if (obj == null || feat == null || feat.isEmpty()) return java.util.List.of();
        org.eclipse.emf.ecore.EStructuralFeature f = obj.eClass().getEStructuralFeature(feat);
        if (f == null || !f.isMany()) return java.util.List.of();
        return (java.util.List<EObject>) obj.eGet(f);
    }

    /** Appends an EEnum literal by name to an isMany EEnum feature of obj. */
    @SuppressWarnings("unchecked")
    private static void _tlmAddPropLiteral(EObject obj, String feat, String literalName) {
        if (obj == null || feat == null || feat.isEmpty() || literalName == null) return;
        org.eclipse.emf.ecore.EStructuralFeature f = obj.eClass().getEStructuralFeature(feat);
        if (f == null || !f.isMany()) return;
        org.eclipse.emf.ecore.EEnum e = (org.eclipse.emf.ecore.EEnum) f.getEType();
        org.eclipse.emf.ecore.EEnumLiteral lit = e.getEEnumLiteral(literalName);
        if (lit != null) ((java.util.List<Object>) obj.eGet(f)).add(lit.getInstance());
    }

    /** Sets an EEnum feature by literal name (single-valued). */
    private static void _tlmSetEnum(EObject obj, String feat, String literalName) {
        if (obj == null || feat == null || feat.isEmpty() || literalName == null) return;
        org.eclipse.emf.ecore.EStructuralFeature f = obj.eClass().getEStructuralFeature(feat);
        if (f == null) return;
        org.eclipse.emf.ecore.EEnum e = (org.eclipse.emf.ecore.EEnum) f.getEType();
        org.eclipse.emf.ecore.EEnumLiteral lit = e.getEEnumLiteral(literalName);
        if (lit != null) obj.eSet(f, lit.getInstance());
    }

    /** Creates and attaches a constraint object to a link if the constraint EClass is configured. */
    private static void _tlmAddConstraint(EObject link, String consEClass, String consFeat,
            String condFeat, String actFeat, String condVal, String actVal) {
        if (consEClass == null || consEClass.isEmpty() || consFeat == null || consFeat.isEmpty()) return;
        EObject _cons = _tlmCreateObj(consEClass);
        _tlmSetEnum(_cons, condFeat, condVal);
        _tlmSetEnum(_cons, actFeat, actVal);
        _tlmList(link, consFeat).add(_cons);
    }

</#if>
    // ════════════════════════════════════════════════════════════════════════
    // Phase 2: Cross-Reference Resolution (Forward)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Resolves all cross-references (non-containment) using the object map.
     */
    @SuppressWarnings("unchecked")
