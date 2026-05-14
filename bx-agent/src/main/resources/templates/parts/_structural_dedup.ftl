<#if structuralDeduplicationMappings?has_content>
    // ════════════════════════════════════════════════════════════════════════
    // Structural Deduplication Methods (Tree → DAG)
    // ════════════════════════════════════════════════════════════════════════

<#list structuralDeduplicationMappings as sdm>
    // ── ${sdm.abstractSourceType()} → ${sdm.abstractTargetType()} ──────────────────────────────────────────────────────

    /**
     * Computes a recursive structural fingerprint for a source tree node.
     * The fingerprint encodes the node type, key attributes, and child fingerprints.
     * Equal subtrees produce equal fingerprints and thus map to the same DAG node.
     */
    private static String _computeStructuralFingerprint${sdm.abstractSourceType()}(
            EObject node, java.util.Map<EObject, String> fpCache) {
        if (node == null) return "null";
        String _cached = fpCache.get(node);
        if (_cached != null) return _cached;

        String _typeName = node.eClass().getName();
        StringBuilder _sb = new StringBuilder(_typeName);

<#list sdm.concreteTypes() as ct>
        if (node instanceof ${sourcePackageName}.${ct.sourceType()}) {
<#list ct.keyAttributes() as ka>
            {
                org.eclipse.emf.ecore.EStructuralFeature _f = node.eClass().getEStructuralFeature("${ka}");
                if (_f != null) _sb.append(":${ka}=").append(node.eGet(_f));
            }
</#list>
<#list ct.childRefs() as childRef>
            {
                org.eclipse.emf.ecore.EStructuralFeature _cr = node.eClass().getEStructuralFeature("${childRef}");
                if (_cr != null) {
                    EObject _child = (EObject) node.eGet(_cr);
                    _sb.append(":${childRef}=").append(
                            _computeStructuralFingerprint${sdm.abstractSourceType()}(_child, fpCache));
                }
            }
</#list>
        }
</#list>

        String _fp = _sb.toString();
        fpCache.put(node, _fp);
        return _fp;
    }

    /**
     * Batch forward: walks the source tree rooted at the container's source ref,
     * builds DAG nodes (sharing equal subtrees), and populates the target container's flat list.
     */
    private static void _materializeStructuralDedup${sdm.abstractSourceType()}(
            ${sourcePackageName}.${sdm.sourceContainerType()} srcContainer,
            ${targetPackageName}.${sdm.targetContainerType()} tgtContainer,
            java.util.Map<EObject, EObject> dedupMap,
            java.util.Map<EObject, String> fpCache) {

        // Clear target flat list — will be rebuilt entirely
        @SuppressWarnings("unchecked")
        java.util.List<EObject> _tgtList = (java.util.List<EObject>) tgtContainer.eGet(
                tgtContainer.eClass().getEStructuralFeature("${sdm.targetContainerRef()}"));
        _tgtList.clear();

        // Fingerprint → DAG node for deduplication within this tree
        java.util.Map<String, EObject> _fpToTarget = new java.util.LinkedHashMap<>();

<#if sdm.sourceContainerRefIsMany()>
        @SuppressWarnings("unchecked")
        java.util.List<EObject> _srcRoots = (java.util.List<EObject>) srcContainer.eGet(
                srcContainer.eClass().getEStructuralFeature("${sdm.sourceContainerRef()}"));
        for (EObject _srcRoot : _srcRoots) {
            _walkAndDedup${sdm.abstractSourceType()}(_srcRoot, _fpToTarget, fpCache, dedupMap, tgtContainer);
        }
<#else>
        EObject _srcRoot = (EObject) srcContainer.eGet(
                srcContainer.eClass().getEStructuralFeature("${sdm.sourceContainerRef()}"));
        if (_srcRoot != null) {
            _walkAndDedup${sdm.abstractSourceType()}(_srcRoot, _fpToTarget, fpCache, dedupMap, tgtContainer);
        }
</#if>
    }

    /**
     * Post-order tree walk helper for batch forward structural deduplication.
     * Children are processed first so that child DAG nodes are available when the parent is created.
     */
    private static EObject _walkAndDedup${sdm.abstractSourceType()}(
            EObject node,
            java.util.Map<String, EObject> fpToTarget,
            java.util.Map<EObject, String> fpCache,
            java.util.Map<EObject, EObject> dedupMap,
            ${targetPackageName}.${sdm.targetContainerType()} tgtContainer) {

        if (node == null) return null;

        // Post-order: resolve children first, collect resulting DAG child nodes
        java.util.Map<String, EObject> _childDagNodes = new java.util.LinkedHashMap<>();
<#list sdm.concreteTypes() as ct>
<#if ct.childRefs()?has_content>
        if (node instanceof ${sourcePackageName}.${ct.sourceType()}) {
<#list ct.childRefs() as childRef>
            {
                org.eclipse.emf.ecore.EStructuralFeature _cr = node.eClass().getEStructuralFeature("${childRef}");
                if (_cr != null) {
                    EObject _child = (EObject) node.eGet(_cr);
                    EObject _dagChild = _walkAndDedup${sdm.abstractSourceType()}(
                            _child, fpToTarget, fpCache, dedupMap, tgtContainer);
                    _childDagNodes.put("${childRef}", _dagChild);
                }
            }
</#list>
        }
</#if>
</#list>

        // Compute fingerprint (children already cached)
        String _fp = _computeStructuralFingerprint${sdm.abstractSourceType()}(node, fpCache);

        // Reuse existing DAG node for this fingerprint, or create a new one
        EObject _dagNode = fpToTarget.get(_fp);
        if (_dagNode == null) {
<#list sdm.concreteTypes() as ct>
            if (node instanceof ${sourcePackageName}.${ct.sourceType()}) {
                ${targetPackageName}.${ct.targetType()} _newDag = ${targetFactory}.eINSTANCE.create${ct.targetType()}();
                _copyAttrsReflective(node, _newDag);
<#list ct.childRefs() as childRef>
                {
                    org.eclipse.emf.ecore.EStructuralFeature _dagRef =
                            _newDag.eClass().getEStructuralFeature("${childRef}");
                    if (_dagRef != null) _newDag.eSet(_dagRef, _childDagNodes.get("${childRef}"));
                }
</#list>
                // Add to flat list (children before parents due to post-order)
                @SuppressWarnings("unchecked")
                java.util.List<EObject> _exprs = (java.util.List<EObject>) tgtContainer.eGet(
                        tgtContainer.eClass().getEStructuralFeature("${sdm.targetContainerRef()}"));
                _exprs.add(_newDag);
                _dagNode = _newDag;
            }
</#list>
            if (_dagNode != null) fpToTarget.put(_fp, _dagNode);
        }

        if (_dagNode != null) dedupMap.put(node, _dagNode);
        return _dagNode;
    }

    /**
     * Batch backward: finds the DAG root (node not referenced by any other node),
     * then recursively clones the DAG tree into AST containment.
     * Shared DAG nodes produce N AST copies (one per occurrence in the tree).
     */
    private static void _materializeStructuralDedupBack${sdm.abstractTargetType()}(
            ${targetPackageName}.${sdm.targetContainerType()} tgtContainer,
            ${sourcePackageName}.${sdm.sourceContainerType()} srcContainer,
            java.util.Map<EObject, java.util.List<EObject>> backDedupMap) {

        // Collect all nodes referenced as children — the root is the one not in this set
        java.util.Set<EObject> _referenced = new java.util.HashSet<>();
        @SuppressWarnings("unchecked")
        java.util.List<EObject> _allDagNodes = (java.util.List<EObject>) tgtContainer.eGet(
                tgtContainer.eClass().getEStructuralFeature("${sdm.targetContainerRef()}"));
        for (EObject _dagNode : _allDagNodes) {
<#list sdm.concreteTypes() as ct>
<#if ct.childRefs()?has_content>
            if (_dagNode instanceof ${targetPackageName}.${ct.targetType()}) {
<#list ct.childRefs() as childRef>
                {
                    org.eclipse.emf.ecore.EStructuralFeature _dagRef =
                            _dagNode.eClass().getEStructuralFeature("${childRef}");
                    if (_dagRef != null) {
                        EObject _child = (EObject) _dagNode.eGet(_dagRef);
                        if (_child != null) _referenced.add(_child);
                    }
                }
</#list>
            }
</#if>
</#list>
        }

        EObject _dagRoot = null;
        for (EObject _dagNode : _allDagNodes) {
            if (!_referenced.contains(_dagNode)) { _dagRoot = _dagNode; break; }
        }
        if (_dagRoot == null) return;

        // Clone DAG tree to AST; shared DAG nodes produce multiple AST copies
        EObject _astRoot = _cloneToAst${sdm.abstractTargetType()}(_dagRoot, backDedupMap);
        if (_astRoot == null) return;

        // Set AST root in source container
<#if sdm.sourceContainerRefIsMany()>
        @SuppressWarnings("unchecked")
        java.util.List<EObject> _srcList = (java.util.List<EObject>) srcContainer.eGet(
                srcContainer.eClass().getEStructuralFeature("${sdm.sourceContainerRef()}"));
        _srcList.add(_astRoot);
<#else>
        org.eclipse.emf.ecore.EStructuralFeature _srcRootRef =
                srcContainer.eClass().getEStructuralFeature("${sdm.sourceContainerRef()}");
        srcContainer.eSet(_srcRootRef, _astRoot);
</#if>
    }

    /**
     * Recursively clones a DAG node into a new AST node (containment tree).
     * Each call creates a fresh AST object, so shared DAG nodes produce multiple copies.
     */
    private static EObject _cloneToAst${sdm.abstractTargetType()}(
            EObject dagNode,
            java.util.Map<EObject, java.util.List<EObject>> backDedupMap) {

        if (dagNode == null) return null;

        EObject _astNode = null;
<#list sdm.concreteTypes() as ct>
        if (dagNode instanceof ${targetPackageName}.${ct.targetType()}) {
            ${sourcePackageName}.${ct.sourceType()} _newAst = ${sourceFactory}.eINSTANCE.create${ct.sourceType()}();
            _copyAttrsReflective(dagNode, _newAst);
<#list ct.childRefs() as childRef>
            {
                org.eclipse.emf.ecore.EStructuralFeature _dagRef =
                        dagNode.eClass().getEStructuralFeature("${childRef}");
                org.eclipse.emf.ecore.EStructuralFeature _astRef =
                        _newAst.eClass().getEStructuralFeature("${childRef}");
                if (_dagRef != null && _astRef != null) {
                    EObject _dagChild = (EObject) dagNode.eGet(_dagRef);
                    EObject _astChild = _cloneToAst${sdm.abstractTargetType()}(_dagChild, backDedupMap);
                    _newAst.eSet(_astRef, _astChild);
                }
            }
</#list>
            _astNode = _newAst;
        }
</#list>

        if (_astNode != null) {
            backDedupMap.computeIfAbsent(dagNode, k -> new java.util.ArrayList<>()).add(_astNode);
        }
        return _astNode;
    }

    /**
     * Incremental forward: reuses existing DAG nodes (by structural fingerprint from corrResource),
     * creates new ones for unseen fingerprints, and deletes orphaned DAG nodes.
     */
    @SuppressWarnings("unchecked")
    private static void _materializeStructuralDedup${sdm.abstractSourceType()}Incremental(
            Resource sourceModel, Resource existingTarget,
            Resource corrResource,
            com.google.common.collect.BiMap<EObject, EObject> corrIndex,
            java.util.Map<EObject, EObject> sdDedupIndex,
            List<EObject> _created, List<EObject> _updated) {

        // Build fingerprint → existing DAG node from corrResource structural dedup CEs
        java.util.Map<String, EObject> _existingFpToTarget =
                CorrespondenceModel.buildStructuralDedupFpIndex(corrResource);
        java.util.Map<EObject, String> _fpCache = new java.util.HashMap<>();

        for (EObject _srcObj : allSourceObjects(sourceModel)) {
            if (!(_srcObj instanceof ${sourcePackageName}.${sdm.sourceContainerType()} _srcContainer)) continue;
            EObject _tgtContainerObj = corrIndex.get(_srcContainer);
            if (!(_tgtContainerObj instanceof ${targetPackageName}.${sdm.targetContainerType()} _tgtContainer)) continue;

            java.util.List<EObject> _tgtList = (java.util.List<EObject>) _tgtContainer.eGet(
                    _tgtContainer.eClass().getEStructuralFeature("${sdm.targetContainerRef()}"));

            // Save existing DAG nodes before rebuilding so we can detect orphans
            java.util.Set<EObject> _oldDagNodes = new java.util.HashSet<>(_tgtList);

            // Clear flat list — will be rebuilt by the incremental walk
            _tgtList.clear();

            // Walk AST post-order; reuse or create DAG nodes
            java.util.Map<String, EObject> _fpToTarget = new java.util.LinkedHashMap<>(_existingFpToTarget);
            java.util.Map<EObject, EObject> _newSdMap = new java.util.LinkedHashMap<>();

<#if sdm.sourceContainerRefIsMany()>
            java.util.List<EObject> _srcRoots = (java.util.List<EObject>) _srcContainer.eGet(
                    _srcContainer.eClass().getEStructuralFeature("${sdm.sourceContainerRef()}"));
            for (EObject _srcRoot : _srcRoots) {
                _walkAndDedupIncremental${sdm.abstractSourceType()}(
                        _srcRoot, _fpToTarget, _fpCache, _newSdMap, sdDedupIndex, _tgtContainer,
                        corrResource, _created, _updated);
            }
<#else>
            EObject _srcRoot = (EObject) _srcContainer.eGet(
                    _srcContainer.eClass().getEStructuralFeature("${sdm.sourceContainerRef()}"));
            if (_srcRoot != null) {
                _walkAndDedupIncremental${sdm.abstractSourceType()}(
                        _srcRoot, _fpToTarget, _fpCache, _newSdMap, sdDedupIndex, _tgtContainer,
                        corrResource, _created, _updated);
            }
</#if>

            // Update/add structural dedup CE entries for the new mapping
            for (java.util.Map.Entry<EObject, EObject> _e : _newSdMap.entrySet()) {
                EObject _srcNode = _e.getKey();
                EObject _dagNode = _e.getValue();
                EObject _existingMapped = sdDedupIndex.get(_srcNode);
                if (_existingMapped == null) {
                    // New AST → DAG mapping
                    String _srcFp = _fpCache.getOrDefault(_srcNode, "");
                    String _tgtFp = computeFingerprintBack(_dagNode);
                    CorrespondenceModel.addStructuralDedupEntry(corrResource,
                            _srcNode, _srcNode.eClass().getName(), _srcFp,
                            _dagNode, _dagNode.eClass().getName(), _tgtFp);
                    sdDedupIndex.put(_srcNode, _dagNode);
                }
            }

            // Delete orphaned DAG nodes (those no longer in the flat list).
            // Remove their CEs BEFORE deletion: once removed from containment the object has
            // eResource()==null, so EcoreUtil.delete can no longer nullify cross-resource refs
            // and the CE_TARGET_OBJECT dangling pointer would cause a save failure.
            _oldDagNodes.removeAll(new java.util.HashSet<>(_tgtList));
            java.util.Map<EObject, java.util.List<EObject>> _reverseForOrphans =
                    CorrespondenceModel.buildReverseStructuralDedupIndex(corrResource);
            for (EObject _orphan : _oldDagNodes) {
                java.util.List<EObject> _srcNodes =
                        _reverseForOrphans.getOrDefault(_orphan, java.util.List.of());
                for (EObject _srcNode : _srcNodes) {
                    CorrespondenceModel.findStructuralDedupBySource(corrResource, _srcNode)
                            .ifPresent(ce -> CorrespondenceModel.removeCorrespondenceEntry(corrResource, ce));
                }
                EcoreUtil.delete(_orphan, true);
            }
        }

        // Clean up stale structural dedup CEs (source AST node was deleted)
        for (EObject _entry : new java.util.ArrayList<>(
                CorrespondenceModel.findDeletedStructuralDedupSourceEntries(corrResource))) {
            CorrespondenceModel.removeCorrespondenceEntry(corrResource, _entry);
        }
        // Clean up any remaining CEs whose DAG node was deleted
        for (EObject _entry : new java.util.ArrayList<>(
                CorrespondenceModel.findDeletedStructuralDedupTargetEntries(corrResource))) {
            CorrespondenceModel.removeCorrespondenceEntry(corrResource, _entry);
        }
    }

    /**
     * Post-order incremental walk helper. Reuses DAG nodes for known fingerprints;
     * creates new DAG nodes for unseen fingerprints.
     */
    @SuppressWarnings("unchecked")
    private static EObject _walkAndDedupIncremental${sdm.abstractSourceType()}(
            EObject node,
            java.util.Map<String, EObject> fpToTarget,
            java.util.Map<EObject, String> fpCache,
            java.util.Map<EObject, EObject> newSdMap,
            java.util.Map<EObject, EObject> sdDedupIndex,
            ${targetPackageName}.${sdm.targetContainerType()} tgtContainer,
            Resource corrResource,
            List<EObject> _created, List<EObject> _updated) {

        if (node == null) return null;

        // Post-order: children first
        java.util.Map<String, EObject> _childDagNodes = new java.util.LinkedHashMap<>();
<#list sdm.concreteTypes() as ct>
<#if ct.childRefs()?has_content>
        if (node instanceof ${sourcePackageName}.${ct.sourceType()}) {
<#list ct.childRefs() as childRef>
            {
                org.eclipse.emf.ecore.EStructuralFeature _cr = node.eClass().getEStructuralFeature("${childRef}");
                if (_cr != null) {
                    EObject _child = (EObject) node.eGet(_cr);
                    EObject _dagChild = _walkAndDedupIncremental${sdm.abstractSourceType()}(
                            _child, fpToTarget, fpCache, newSdMap, sdDedupIndex, tgtContainer,
                            corrResource, _created, _updated);
                    _childDagNodes.put("${childRef}", _dagChild);
                }
            }
</#list>
        }
</#if>
</#list>

        // Compute fingerprint (children already cached)
        String _fp = _computeStructuralFingerprint${sdm.abstractSourceType()}(node, fpCache);

        EObject _dagNode = fpToTarget.get(_fp);
        if (_dagNode == null) {
            // New fingerprint: create new DAG node.
            // Inherit non-key attrs (e.g. incrementalID) from the old DAG node this AST
            // node previously mapped to, so independently-set target values survive structural edits.
            EObject _oldDag = sdDedupIndex.get(node);
<#list sdm.concreteTypes() as ct>
            if (node instanceof ${sourcePackageName}.${ct.sourceType()}) {
                ${targetPackageName}.${ct.targetType()} _newDag = ${targetFactory}.eINSTANCE.create${ct.targetType()}();
                _copyAttrsReflective(node, _newDag);
                // Inherit explicitly-set non-key attrs from old DAG node (if any)
                if (_oldDag != null) {
                    java.util.Set<String> _keySet = new java.util.HashSet<>(
                            java.util.Arrays.asList(<#list ct.keyAttributes() as ka>"${ka}"<#sep>, </#sep></#list>));
                    for (org.eclipse.emf.ecore.EAttribute _a : _oldDag.eClass().getEAllAttributes()) {
                        if (_keySet.contains(_a.getName()) || _a.isDerived() || _a.isTransient()) continue;
                        if (_oldDag.eIsSet(_a)) {
                            org.eclipse.emf.ecore.EStructuralFeature _tf =
                                    _newDag.eClass().getEStructuralFeature(_a.getName());
                            if (_tf != null) _newDag.eSet(_tf, _oldDag.eGet(_a));
                        }
                    }
                }
<#list ct.childRefs() as childRef>
                {
                    org.eclipse.emf.ecore.EStructuralFeature _dagRef =
                            _newDag.eClass().getEStructuralFeature("${childRef}");
                    if (_dagRef != null) _newDag.eSet(_dagRef, _childDagNodes.get("${childRef}"));
                }
</#list>
                java.util.List<EObject> _exprs = (java.util.List<EObject>) tgtContainer.eGet(
                        tgtContainer.eClass().getEStructuralFeature("${sdm.targetContainerRef()}"));
                _exprs.add(_newDag);
                _dagNode = _newDag;
                _created.add(_newDag);
            }
</#list>
            if (_dagNode != null) fpToTarget.put(_fp, _dagNode);
        } else {
            // Known fingerprint: reuse existing DAG node unchanged.
            // Do NOT copy attributes: the fingerprint match guarantees key attributes are
            // identical, and non-key attributes set independently in the target
            // (e.g. incrementalID set by the test harness) must be preserved (stability).
            // Child cross-refs also need no rewiring: if child fingerprints changed the
            // parent fingerprint would have changed too, so we would have taken the new-node branch.
            // Ensure node is back in the rebuilt flat list (was cleared at the start).
            java.util.List<EObject> _exprs = (java.util.List<EObject>) tgtContainer.eGet(
                    tgtContainer.eClass().getEStructuralFeature("${sdm.targetContainerRef()}"));
            if (!_exprs.contains(_dagNode)) _exprs.add(_dagNode);
        }

        if (_dagNode != null) newSdMap.put(node, _dagNode);
        return _dagNode;
    }

    /**
     * Incremental backward: walks DAG tree and existing AST tree in sync ("tree-walk diff").
     * Updates AST nodes in-place when types match; replaces subtrees when types differ.
     */
    @SuppressWarnings("unchecked")
    private static void _materializeStructuralDedupBack${sdm.abstractTargetType()}Incremental(
            Resource targetModel, Resource sourceModel,
            Resource corrResource,
            com.google.common.collect.BiMap<EObject, EObject> corrIndex,
            List<EObject> _createdBack, List<EObject> _updatedBack) {

        for (EObject _tgtObj : allSourceObjects(targetModel)) {
            if (!(_tgtObj instanceof ${targetPackageName}.${sdm.targetContainerType()} _tgtContainer)) continue;
            EObject _srcContainerObj = corrIndex.inverse().get(_tgtContainer);
            if (!(_srcContainerObj instanceof ${sourcePackageName}.${sdm.sourceContainerType()} _srcContainer)) continue;

            // Find DAG root (node not referenced by any other node as a child)
            java.util.Set<EObject> _referenced = new java.util.HashSet<>();
            java.util.List<EObject> _allDagNodes = (java.util.List<EObject>) _tgtContainer.eGet(
                    _tgtContainer.eClass().getEStructuralFeature("${sdm.targetContainerRef()}"));
            for (EObject _dagNode : _allDagNodes) {
<#list sdm.concreteTypes() as ct>
<#if ct.childRefs()?has_content>
                if (_dagNode instanceof ${targetPackageName}.${ct.targetType()}) {
<#list ct.childRefs() as childRef>
                    {
                        org.eclipse.emf.ecore.EStructuralFeature _dagRef =
                                _dagNode.eClass().getEStructuralFeature("${childRef}");
                        if (_dagRef != null) {
                            EObject _child = (EObject) _dagNode.eGet(_dagRef);
                            if (_child != null) _referenced.add(_child);
                        }
                    }
</#list>
                }
</#if>
</#list>
            }

            EObject _dagRoot = null;
            for (EObject _dagNode : _allDagNodes) {
                if (!_referenced.contains(_dagNode)) { _dagRoot = _dagNode; break; }
            }
            if (_dagRoot == null) continue;

            // Get existing AST root
<#if sdm.sourceContainerRefIsMany()>
            java.util.List<EObject> _srcRootList = (java.util.List<EObject>) _srcContainer.eGet(
                    _srcContainer.eClass().getEStructuralFeature("${sdm.sourceContainerRef()}"));
            EObject _astRoot = _srcRootList.isEmpty() ? null : _srcRootList.get(0);
<#else>
            EObject _astRoot = (EObject) _srcContainer.eGet(
                    _srcContainer.eClass().getEStructuralFeature("${sdm.sourceContainerRef()}"));
</#if>

            // Sync DAG tree to AST tree
            EObject _newAstRoot = _syncDagToAst${sdm.abstractSourceType()}(
                    _dagRoot, _astRoot, corrResource, _createdBack, _updatedBack);

            // Update source container's root ref if changed
            if (_newAstRoot != _astRoot) {
<#if sdm.sourceContainerRefIsMany()>
                java.util.List<EObject> _srcList = (java.util.List<EObject>) _srcContainer.eGet(
                        _srcContainer.eClass().getEStructuralFeature("${sdm.sourceContainerRef()}"));
                _srcList.clear();
                if (_newAstRoot != null) _srcList.add(_newAstRoot);
<#else>
                org.eclipse.emf.ecore.EStructuralFeature _srcRootRef =
                        _srcContainer.eClass().getEStructuralFeature("${sdm.sourceContainerRef()}");
                _srcContainer.eSet(_srcRootRef, _newAstRoot);
</#if>
            }
        }

        // Clean up stale structural dedup CEs (target DAG node was deleted → null in corr)
        for (EObject _entry : new java.util.ArrayList<>(
                CorrespondenceModel.findDeletedStructuralDedupTargetEntries(corrResource))) {
            EObject _srcObj = CorrespondenceModel.getSourceObject(_entry);
            if (_srcObj != null && _srcObj.eResource() != null) {
                EcoreUtil.delete(_srcObj, true);
            }
            CorrespondenceModel.removeCorrespondenceEntry(corrResource, _entry);
        }
    }

    /**
     * Tree-walk diff: walks DAG and AST trees in sync.
     * Updates AST attributes when types match; replaces AST subtrees when types differ.
     */
    private static EObject _syncDagToAst${sdm.abstractSourceType()}(
            EObject dagNode, EObject astNode,
            Resource corrResource,
            List<EObject> _created, List<EObject> _updated) {

        if (dagNode == null) {
            if (astNode != null) {
                _deleteStructuralDedupSubtree${sdm.abstractSourceType()}(astNode, corrResource);
            }
            return null;
        }

        // Check if concrete types match (DAG type → expected AST type)
        boolean _typesMatch = false;
<#list sdm.concreteTypes() as ct>
        if (dagNode instanceof ${targetPackageName}.${ct.targetType()}
                && astNode instanceof ${sourcePackageName}.${ct.sourceType()}) {
            _typesMatch = true;
        }
</#list>

        if (!_typesMatch) {
            if (astNode != null) {
                _deleteStructuralDedupSubtree${sdm.abstractSourceType()}(astNode, corrResource);
            }
            return _createAstNodeFromDag${sdm.abstractSourceType()}(dagNode, corrResource, _created);
        }

        // Types match: copy only key attributes from DAG to AST.
        // Non-key attributes set independently in the AST (e.g. incrementalID set by
        // the test harness) must be preserved — do NOT call _copyAttrsReflective here.
        boolean _anyKeyAttrChanged = false;
<#list sdm.concreteTypes() as ct>
<#if ct.keyAttributes()?has_content>
        if (dagNode instanceof ${targetPackageName}.${ct.targetType()} && astNode instanceof ${sourcePackageName}.${ct.sourceType()}) {
<#list ct.keyAttributes() as ka>
            {
                org.eclipse.emf.ecore.EStructuralFeature _dagF = dagNode.eClass().getEStructuralFeature("${ka}");
                org.eclipse.emf.ecore.EStructuralFeature _astF = astNode.eClass().getEStructuralFeature("${ka}");
                if (_dagF != null && _astF != null) {
                    Object _newVal = dagNode.eGet(_dagF);
                    Object _curVal = astNode.eGet(_astF);
                    if (_newVal instanceof org.eclipse.emf.common.util.Enumerator _e
                            && _astF.getEType() instanceof org.eclipse.emf.ecore.EEnum _enum) {
                        org.eclipse.emf.ecore.EEnumLiteral _lit = _enum.getEEnumLiteralByLiteral(_e.getLiteral());
                        if (_lit != null && !_lit.getInstance().equals(_curVal)) {
                            astNode.eSet(_astF, _lit.getInstance());
                            _anyKeyAttrChanged = true;
                        }
                    } else if (!java.util.Objects.equals(_newVal, _curVal)) {
                        astNode.eSet(_astF, _newVal);
                        _anyKeyAttrChanged = true;
                    }
                }
            }
</#list>
        }
</#if>
</#list>
        if (_anyKeyAttrChanged) _updated.add(astNode);

        // Update CE target fingerprint
        CorrespondenceModel.findStructuralDedupBySource(corrResource, astNode).ifPresent(ce ->
                CorrespondenceModel.updateTargetFingerprint(ce, computeFingerprintBack(dagNode)));

<#list sdm.concreteTypes() as ct>
<#if ct.childRefs()?has_content>
        if (dagNode instanceof ${targetPackageName}.${ct.targetType()}
                && astNode instanceof ${sourcePackageName}.${ct.sourceType()}) {
<#list ct.childRefs() as childRef>
            {
                org.eclipse.emf.ecore.EStructuralFeature _dagRef =
                        dagNode.eClass().getEStructuralFeature("${childRef}");
                org.eclipse.emf.ecore.EStructuralFeature _astRef =
                        astNode.eClass().getEStructuralFeature("${childRef}");
                if (_dagRef != null && _astRef != null) {
                    EObject _dagChild = (EObject) dagNode.eGet(_dagRef);
                    EObject _astChild = (EObject) astNode.eGet(_astRef);
                    EObject _newAstChild = _syncDagToAst${sdm.abstractSourceType()}(
                            _dagChild, _astChild, corrResource, _created, _updated);
                    if (_newAstChild != _astChild) {
                        astNode.eSet(_astRef, _newAstChild);
                    }
                }
            }
</#list>
        }
</#if>
</#list>

        return astNode;
    }

    /**
     * Recursively creates a new AST subtree from a DAG node, adding CEs for each created node.
     */
    private static EObject _createAstNodeFromDag${sdm.abstractSourceType()}(
            EObject dagNode, Resource corrResource, List<EObject> _created) {

        if (dagNode == null) return null;

        EObject _astNode = null;
<#list sdm.concreteTypes() as ct>
        if (dagNode instanceof ${targetPackageName}.${ct.targetType()}) {
            ${sourcePackageName}.${ct.sourceType()} _newAst = ${sourceFactory}.eINSTANCE.create${ct.sourceType()}();
            _copyAttrsReflective(dagNode, _newAst);
<#list ct.childRefs() as childRef>
            {
                org.eclipse.emf.ecore.EStructuralFeature _dagRef =
                        dagNode.eClass().getEStructuralFeature("${childRef}");
                org.eclipse.emf.ecore.EStructuralFeature _astRef =
                        _newAst.eClass().getEStructuralFeature("${childRef}");
                if (_dagRef != null && _astRef != null) {
                    EObject _dagChild = (EObject) dagNode.eGet(_dagRef);
                    EObject _astChild = _createAstNodeFromDag${sdm.abstractSourceType()}(
                            _dagChild, corrResource, _created);
                    _newAst.eSet(_astRef, _astChild);
                }
            }
</#list>
            _astNode = _newAst;
        }
</#list>

        if (_astNode != null) {
            _created.add(_astNode);
            String _tgtFp = computeFingerprintBack(dagNode);
            CorrespondenceModel.addStructuralDedupEntry(corrResource,
                    _astNode, _astNode.eClass().getName(), "",
                    dagNode, dagNode.eClass().getName(), _tgtFp);
        }
        return _astNode;
    }

    /**
     * Recursively deletes an AST subtree, removing structural dedup CEs before deletion.
     */
    private static void _deleteStructuralDedupSubtree${sdm.abstractSourceType()}(
            EObject astNode, Resource corrResource) {

        if (astNode == null) return;

        // Recurse to children before deleting this node
<#list sdm.concreteTypes() as ct>
<#if ct.childRefs()?has_content>
        if (astNode instanceof ${sourcePackageName}.${ct.sourceType()}) {
<#list ct.childRefs() as childRef>
            {
                org.eclipse.emf.ecore.EStructuralFeature _astRef =
                        astNode.eClass().getEStructuralFeature("${childRef}");
                if (_astRef != null) {
                    EObject _child = (EObject) astNode.eGet(_astRef);
                    _deleteStructuralDedupSubtree${sdm.abstractSourceType()}(_child, corrResource);
                }
            }
</#list>
        }
</#if>
</#list>

        // Remove CE for this node before deletion (EMF nullifies refs on EcoreUtil.delete)
        CorrespondenceModel.findStructuralDedupBySource(corrResource, astNode)
                .ifPresent(ce -> CorrespondenceModel.removeCorrespondenceEntry(corrResource, ce));

        EcoreUtil.delete(astNode, true);
    }

</#list>
    /**
     * Copies all non-derived, non-transient attributes from src to tgt using reflection.
     * Handles cross-package EEnum values by matching on literal strings.
     * Used by structural deduplication methods.
     */
    private static void _copyAttrsReflective(EObject src, EObject tgt) {
        for (org.eclipse.emf.ecore.EAttribute _srcAttr : src.eClass().getEAllAttributes()) {
            if (_srcAttr.isDerived() || _srcAttr.isTransient()) continue;
            org.eclipse.emf.ecore.EStructuralFeature _tgtFeat =
                    tgt.eClass().getEStructuralFeature(_srcAttr.getName());
            if (_tgtFeat instanceof org.eclipse.emf.ecore.EAttribute _tgtAttr
                    && !_tgtAttr.isDerived() && !_tgtAttr.isTransient()) {
                Object _srcVal = src.eGet(_srcAttr);
                // eGet() on EEnum attrs returns the Java Enumerator instance (not EEnumLiteral).
                // Cross-package enums have incompatible Java types, so look up by literal string.
                if (_srcVal instanceof org.eclipse.emf.common.util.Enumerator _srcEnum
                        && _tgtAttr.getEType() instanceof org.eclipse.emf.ecore.EEnum _tgtEnum) {
                    org.eclipse.emf.ecore.EEnumLiteral _tgtLit =
                            _tgtEnum.getEEnumLiteralByLiteral(_srcEnum.getLiteral());
                    if (_tgtLit != null) tgt.eSet(_tgtFeat, _tgtLit.getInstance());
                } else {
                    tgt.eSet(_tgtFeat, _srcVal);
                }
            }
        }
    }
</#if>
