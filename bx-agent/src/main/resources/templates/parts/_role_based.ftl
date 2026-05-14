
    // ════════════════════════════════════════════════════════════════════════
    // Helper Methods for Type-Specific Transformations
    // ════════════════════════════════════════════════════════════════════════

<#if roleBasedTypeMappingModels?has_content>
    // ════════════════════════════════════════════════════════════════════════
    // Role-Based Type Mapping Methods
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Forward: maps source objects to target types based on their role in the intermediate container.
     * Iterates over intermediate objects, creates target objects per role, and adds them to objectMap.
     */
    @SuppressWarnings("unchecked")
    private static void mapRoleBasedTypes(
            EObject sourceRoot,
            EObject targetRoot,
            Map<EObject, EObject> objectMap) {
<#list roleBasedTypeMappingModels as entry>
    <#assign rbm = entry.rbm>
        // ${rbm.sourceType()} → role-based target types via ${rbm.intermediateType()}
        for (${sourcePackageName}.${rbm.intermediateType()} family : (EList<${sourcePackageName}.${rbm.intermediateType()}>) sourceRoot.eGet(sourceRoot.eClass().getEStructuralFeature("${rbm.sourceContainerRef()}"))) {
    <#list entry.roleEntries as roleEntry>
        <#if roleEntry.isMany>
            for (${sourcePackageName}.${rbm.sourceType()} member : family.get${roleEntry.role?cap_first}()) {
                ${targetPackageName}.${roleEntry.targetType} obj = ${targetFactory}.eINSTANCE.create${roleEntry.targetType}();
                obj.set${rbm.targetAttr()?cap_first}(${rbm.nameExpression()});
                ((EList<EObject>) targetRoot.eGet(targetRoot.eClass().getEStructuralFeature("${rbm.targetContainerRef()}"))).add(obj);
                objectMap.put(member, obj);
            }
        <#else>
            if (family.get${roleEntry.role?cap_first}() != null) {
                ${sourcePackageName}.${rbm.sourceType()} member = family.get${roleEntry.role?cap_first}();
                ${targetPackageName}.${roleEntry.targetType} obj = ${targetFactory}.eINSTANCE.create${roleEntry.targetType}();
                obj.set${rbm.targetAttr()?cap_first}(${rbm.nameExpression()});
                ((EList<EObject>) targetRoot.eGet(targetRoot.eClass().getEStructuralFeature("${rbm.targetContainerRef()}"))).add(obj);
                objectMap.put(member, obj);
            }
        </#if>
    </#list>
        }
</#list>
    }

    /**
     * Backward: maps target objects back to source type, assigns to role in intermediate container.
     * Creates intermediate objects as needed and assigns members to roles based on options.
     */
    @SuppressWarnings("unchecked")
    private static void mapRoleBasedTypesBack(
            EObject targetRoot,
            EObject sourceRoot,
            Options options) {
<#list roleBasedTypeMappingModels as entry>
    <#assign rbm = entry.rbm>
        // ${rbm.targetContainerElementType()} → ${rbm.sourceType()} via ${rbm.intermediateType()}
        Map<String, ${sourcePackageName}.${rbm.intermediateType()}> familyLookup = new HashMap<>();
        for (${targetPackageName}.${rbm.targetContainerElementType()} targetObj : (EList<${targetPackageName}.${rbm.targetContainerElementType()}>) targetRoot.eGet(targetRoot.eClass().getEStructuralFeature("${rbm.targetContainerRef()}"))) {
            String intermediateKey = ${rbm.backwardFamilyNameExpression()};
            String memberName = ${rbm.backwardMemberNameExpression()};
            ${sourcePackageName}.${rbm.intermediateType()} family =
    <#if rbm.backwardPreferExistingParam()??>
                    options.${rbm.backwardPreferExistingParam()}() ? familyLookup.getOrDefault(intermediateKey, null) : null;
    <#else>
                    null;
    </#if>
            if (family == null) {
                family = ${sourceFactory}.eINSTANCE.create${rbm.intermediateType()}();
                family.set${rbm.intermediateAttr()?cap_first}(intermediateKey);
                ((EList<EObject>) sourceRoot.eGet(sourceRoot.eClass().getEStructuralFeature("${rbm.sourceContainerRef()}"))).add(family);
                familyLookup.put(intermediateKey, family);
            }
            ${sourcePackageName}.${rbm.sourceType()} member = ${sourceFactory}.eINSTANCE.create${rbm.sourceType()}();
            member.set${rbm.sourceAttr()?cap_first}(memberName);
    <#list entry.roleGroups as group>
            <#if group?is_first>if<#else>} else if</#if> (targetObj instanceof ${targetPackageName}.${group.targetType}) {
        <#if group.singleRole?? && rbm.backwardPreferSingleRoleParam()??>
                if (options.${rbm.backwardPreferSingleRoleParam()}() && family.get${group.singleRole?cap_first}() == null) {
                    family.set${group.singleRole?cap_first}(member);
                } else {
            <#list group.multiRoles as multiRole>
                    family.get${multiRole?cap_first}().add(member);
            </#list>
                }
        <#else>
            <#list group.multiRoles as multiRole>
                family.get${multiRole?cap_first}().add(member);
            </#list>
        </#if>
    </#list>
            }
        }
</#list>
    }

    /**
     * Incremental forward: applies role-based type mappings, skipping members already in corrIndex.
     */
    @SuppressWarnings("unchecked")
    private static void mapRoleBasedTypesIncremental(
            EObject sourceRoot,
            EObject targetRoot,
            Resource corrResource,
            com.google.common.collect.BiMap<EObject, EObject> corrIndex,
            Options options) {
<#list roleBasedTypeMappingModels as entry>
    <#assign rbm = entry.rbm>
        for (${sourcePackageName}.${rbm.intermediateType()} family : (EList<${sourcePackageName}.${rbm.intermediateType()}>) sourceRoot.eGet(sourceRoot.eClass().getEStructuralFeature("${rbm.sourceContainerRef()}"))) {
    <#list entry.roleEntries as roleEntry>
        <#if roleEntry.isMany>
            for (${sourcePackageName}.${rbm.sourceType()} member : family.get${roleEntry.role?cap_first}()) {
                // Composite fingerprint: intermediate container key + member key.
                // This ensures a Family rename is detected even when member.name is unchanged.
                String _roleFp = family.get${rbm.intermediateAttr()?cap_first}() + "|" + computeFingerprint(member);
                if (!corrIndex.containsKey(member)) {
                    // New member: create target object
                    ${targetPackageName}.${roleEntry.targetType} obj = ${targetFactory}.eINSTANCE.create${roleEntry.targetType}();
                    obj.set${rbm.targetAttr()?cap_first}(${rbm.nameExpression()});
                    ((EList<EObject>) targetRoot.eGet(targetRoot.eClass().getEStructuralFeature("${rbm.targetContainerRef()}"))).add(obj);
                    String _srcCR = member.eContainmentFeature() != null ? member.eContainmentFeature().getName() : "";
                    String _tgtCR = obj.eContainmentFeature() != null ? obj.eContainmentFeature().getName() : "";
                    CorrespondenceModel.addEntry(corrResource,
                            member, member.eClass().getName(), _roleFp,
                            obj, obj.eClass().getName(), computeFingerprintBack(obj), _srcCR, _tgtCR);
                    corrIndex.put(member, obj);
                } else {
                    // Known: check for role type change, containment role change, or attribute change
                    Optional<EObject> entryOpt = CorrespondenceModel.findBySource(corrResource, member);
                    if (entryOpt.isPresent()) {
                        if (!"${roleEntry.targetType}".equals(CorrespondenceModel.getTargetType(entryOpt.get()))) {
                            // Szenario A (type change): member moved to a different role → replace target object
                            EObject _oldTargetObj = corrIndex.get(member);
                            ${targetPackageName}.${roleEntry.targetType} _newTargetObj = ${targetFactory}.eINSTANCE.create${roleEntry.targetType}();
                            // Copy common EAttributes via EMF reflection (preserves target-only attrs, e.g. birthday)
                            for (org.eclipse.emf.ecore.EAttribute _attr : _newTargetObj.eClass().getEAllAttributes()) {
                                if (!_attr.isDerived() && !_attr.isTransient()) {
                                    org.eclipse.emf.ecore.EStructuralFeature _oldAttr = _oldTargetObj.eClass().getEStructuralFeature(_attr.getName());
                                    if (_oldAttr instanceof org.eclipse.emf.ecore.EAttribute) {
                                        _newTargetObj.eSet(_attr, _oldTargetObj.eGet(_oldAttr));
                                    }
                                }
                            }
                            // Apply role-specific attribute mapping (overwrites any copied value)
                            _newTargetObj.set${rbm.targetAttr()?cap_first}(${rbm.nameExpression()});
                            ((EList<EObject>) targetRoot.eGet(targetRoot.eClass().getEStructuralFeature("${rbm.targetContainerRef()}"))).add(_newTargetObj);
                            // Update corr entry and index BEFORE deleting old object (prevents EMF from nulling CE_TARGET_OBJECT)
                            CorrespondenceModel.updateTargetObject(entryOpt.get(), _newTargetObj, _newTargetObj.eClass().getName());
                            CorrespondenceModel.updateFingerprint(entryOpt.get(), _roleFp);
                            CorrespondenceModel.updateTargetFingerprint(entryOpt.get(), computeFingerprintBack(_newTargetObj));
                            String _newSrcCR = member.eContainmentFeature() != null ? member.eContainmentFeature().getName() : "";
                            String _newTgtCR = _newTargetObj.eContainmentFeature() != null ? _newTargetObj.eContainmentFeature().getName() : "";
                            CorrespondenceModel.updateSourceContainmentRole(entryOpt.get(), _newSrcCR);
                            CorrespondenceModel.updateTargetContainmentRole(entryOpt.get(), _newTgtCR);
                            corrIndex.put(member, _newTargetObj);
                            EcoreUtil.delete(_oldTargetObj, true);
                        } else {
                            // Same target type: check for containment role change (Szenario B)
                            String _currentSrcRole = member.eContainmentFeature() != null ? member.eContainmentFeature().getName() : "";
                            String _storedSrcRole  = CorrespondenceModel.getSourceContainmentRole(entryOpt.get());
                            if (_storedSrcRole == null) _storedSrcRole = "";
                            if (!_currentSrcRole.equals(_storedSrcRole)) {
                                // Szenario B: same type, different containment role (e.g. father → sons)
                                // Target stays in the same container (targetContainerRef is fixed for all roles).
                                // Just update the stored roles in the corrEntry.
                                CorrespondenceModel.updateSourceContainmentRole(entryOpt.get(), _currentSrcRole);
                                // targetContainmentRole stays the same (all roles share targetContainerRef)
                            }
                            if (!_roleFp.equals(CorrespondenceModel.getFingerprint(entryOpt.get()))) {
                                // Fingerprint changed (name or family rename)
                                EObject targetMember = corrIndex.get(member);
                                if (targetMember instanceof ${targetPackageName}.${rbm.targetContainerElementType()} p) {
                                    p.set${rbm.targetAttr()?cap_first}(${rbm.nameExpression()});
                                    CorrespondenceModel.updateFingerprint(entryOpt.get(), _roleFp);
                                    CorrespondenceModel.updateTargetFingerprint(entryOpt.get(), computeFingerprintBack(targetMember));
                                }
                            }
                        }
                    }
                }
            }
        <#else>
            if (family.get${roleEntry.role?cap_first}() != null) {
                ${sourcePackageName}.${rbm.sourceType()} member = family.get${roleEntry.role?cap_first}();
                // Composite fingerprint: intermediate container key + member key.
                String _roleFp = family.get${rbm.intermediateAttr()?cap_first}() + "|" + computeFingerprint(member);
                if (!corrIndex.containsKey(member)) {
                    ${targetPackageName}.${roleEntry.targetType} obj = ${targetFactory}.eINSTANCE.create${roleEntry.targetType}();
                    obj.set${rbm.targetAttr()?cap_first}(${rbm.nameExpression()});
                    ((EList<EObject>) targetRoot.eGet(targetRoot.eClass().getEStructuralFeature("${rbm.targetContainerRef()}"))).add(obj);
                    String _srcCR = member.eContainmentFeature() != null ? member.eContainmentFeature().getName() : "";
                    String _tgtCR = obj.eContainmentFeature() != null ? obj.eContainmentFeature().getName() : "";
                    CorrespondenceModel.addEntry(corrResource,
                            member, member.eClass().getName(), _roleFp,
                            obj, obj.eClass().getName(), computeFingerprintBack(obj), _srcCR, _tgtCR);
                    corrIndex.put(member, obj);
                } else {
                    // Known: check for role type change, containment role change, or attribute change
                    Optional<EObject> entryOpt = CorrespondenceModel.findBySource(corrResource, member);
                    if (entryOpt.isPresent()) {
                        if (!"${roleEntry.targetType}".equals(CorrespondenceModel.getTargetType(entryOpt.get()))) {
                            // Szenario A (type change): member moved to a different role → replace target object
                            EObject _oldTargetObj = corrIndex.get(member);
                            ${targetPackageName}.${roleEntry.targetType} _newTargetObj = ${targetFactory}.eINSTANCE.create${roleEntry.targetType}();
                            // Copy common EAttributes via EMF reflection (preserves target-only attrs, e.g. birthday)
                            for (org.eclipse.emf.ecore.EAttribute _attr : _newTargetObj.eClass().getEAllAttributes()) {
                                if (!_attr.isDerived() && !_attr.isTransient()) {
                                    org.eclipse.emf.ecore.EStructuralFeature _oldAttr = _oldTargetObj.eClass().getEStructuralFeature(_attr.getName());
                                    if (_oldAttr instanceof org.eclipse.emf.ecore.EAttribute) {
                                        _newTargetObj.eSet(_attr, _oldTargetObj.eGet(_oldAttr));
                                    }
                                }
                            }
                            // Apply role-specific attribute mapping (overwrites any copied value)
                            _newTargetObj.set${rbm.targetAttr()?cap_first}(${rbm.nameExpression()});
                            ((EList<EObject>) targetRoot.eGet(targetRoot.eClass().getEStructuralFeature("${rbm.targetContainerRef()}"))).add(_newTargetObj);
                            // Update corr entry and index BEFORE deleting old object (prevents EMF from nulling CE_TARGET_OBJECT)
                            CorrespondenceModel.updateTargetObject(entryOpt.get(), _newTargetObj, _newTargetObj.eClass().getName());
                            CorrespondenceModel.updateFingerprint(entryOpt.get(), _roleFp);
                            CorrespondenceModel.updateTargetFingerprint(entryOpt.get(), computeFingerprintBack(_newTargetObj));
                            String _newSrcCR = member.eContainmentFeature() != null ? member.eContainmentFeature().getName() : "";
                            String _newTgtCR = _newTargetObj.eContainmentFeature() != null ? _newTargetObj.eContainmentFeature().getName() : "";
                            CorrespondenceModel.updateSourceContainmentRole(entryOpt.get(), _newSrcCR);
                            CorrespondenceModel.updateTargetContainmentRole(entryOpt.get(), _newTgtCR);
                            corrIndex.put(member, _newTargetObj);
                            EcoreUtil.delete(_oldTargetObj, true);
                        } else {
                            // Same target type: check for containment role change (Szenario B)
                            String _currentSrcRole = member.eContainmentFeature() != null ? member.eContainmentFeature().getName() : "";
                            String _storedSrcRole  = CorrespondenceModel.getSourceContainmentRole(entryOpt.get());
                            if (_storedSrcRole == null) _storedSrcRole = "";
                            if (!_currentSrcRole.equals(_storedSrcRole)) {
                                // Szenario B: same type, different containment role (e.g. father → sons)
                                CorrespondenceModel.updateSourceContainmentRole(entryOpt.get(), _currentSrcRole);
                            }
                            if (!_roleFp.equals(CorrespondenceModel.getFingerprint(entryOpt.get()))) {
                                // Fingerprint changed (name or family rename)
                                EObject targetMember = corrIndex.get(member);
                                if (targetMember instanceof ${targetPackageName}.${rbm.targetContainerElementType()} p) {
                                    p.set${rbm.targetAttr()?cap_first}(${rbm.nameExpression()});
                                    CorrespondenceModel.updateFingerprint(entryOpt.get(), _roleFp);
                                    CorrespondenceModel.updateTargetFingerprint(entryOpt.get(), computeFingerprintBack(targetMember));
                                }
                            }
                        }
                    }
                }
            }
        </#if>
    </#list>
        }
</#list>
    }

    /**
     * Incremental backward: applies role-based type mappings, handling new/changed/deleted target objects.
     * Deletion is handled by the caller (transformIncrementalBack Phase 2).
     */
    @SuppressWarnings("unchecked")
    private static void mapRoleBasedTypesIncrementalBack(
            EObject targetRoot,
            EObject sourceRoot,
            Resource corrResource,
            com.google.common.collect.BiMap<EObject, EObject> corrIndex,
            Options options) {
<#list roleBasedTypeMappingModels as entry>
    <#assign rbm = entry.rbm>
        // Pre-populate familyLookup from existing source ${rbm.intermediateType()} objects
        Map<String, ${sourcePackageName}.${rbm.intermediateType()}> familyLookup = new HashMap<>();
        for (${sourcePackageName}.${rbm.intermediateType()} existingFamily : (EList<${sourcePackageName}.${rbm.intermediateType()}>) sourceRoot.eGet(sourceRoot.eClass().getEStructuralFeature("${rbm.sourceContainerRef()}"))) {
            familyLookup.put(existingFamily.get${rbm.intermediateAttr()?cap_first}(), existingFamily);
        }

        for (${targetPackageName}.${rbm.targetContainerElementType()} targetObj : (EList<${targetPackageName}.${rbm.targetContainerElementType()}>) targetRoot.eGet(targetRoot.eClass().getEStructuralFeature("${rbm.targetContainerRef()}"))) {
            String intermediateKey = ${rbm.backwardFamilyNameExpression()};
            String memberName = ${rbm.backwardMemberNameExpression()};
            String currentFp = computeFingerprintBack(targetObj);

            if (!corrIndex.inverse().containsKey(targetObj)) {
                // New target object: create source member + possibly new intermediate
                ${sourcePackageName}.${rbm.intermediateType()} family =
    <#if rbm.backwardPreferExistingParam()??>
                        options.${rbm.backwardPreferExistingParam()}() ? familyLookup.getOrDefault(intermediateKey, null) : null;
    <#else>
                        null;
    </#if>
                if (family == null) {
                    family = ${sourceFactory}.eINSTANCE.create${rbm.intermediateType()}();
                    family.set${rbm.intermediateAttr()?cap_first}(intermediateKey);
                    ((EList<EObject>) sourceRoot.eGet(sourceRoot.eClass().getEStructuralFeature("${rbm.sourceContainerRef()}"))).add(family);
                    familyLookup.put(intermediateKey, family);
                }
                ${sourcePackageName}.${rbm.sourceType()} member = ${sourceFactory}.eINSTANCE.create${rbm.sourceType()}();
                member.set${rbm.sourceAttr()?cap_first}(memberName);
    <#list entry.roleGroups as group>
                <#if group?is_first>if<#else>} else if</#if> (targetObj instanceof ${targetPackageName}.${group.targetType}) {
        <#if group.singleRole?? && rbm.backwardPreferSingleRoleParam()??>
                    if (options.${rbm.backwardPreferSingleRoleParam()}() && family.get${group.singleRole?cap_first}() == null) {
                        family.set${group.singleRole?cap_first}(member);
                    } else {
            <#list group.multiRoles as multiRole>
                        family.get${multiRole?cap_first}().add(member);
            </#list>
                    }
        <#else>
            <#list group.multiRoles as multiRole>
                    family.get${multiRole?cap_first}().add(member);
            </#list>
        </#if>
    </#list>
                }
                String _srcCR = member.eContainmentFeature() != null ? member.eContainmentFeature().getName() : "";
                String _tgtCR = targetObj.eContainmentFeature() != null ? targetObj.eContainmentFeature().getName() : "";
                CorrespondenceModel.addEntry(corrResource,
                        member, member.eClass().getName(), computeFingerprint(member),
                        targetObj, targetObj.eClass().getName(), currentFp, _srcCR, _tgtCR);
                corrIndex.put(member, targetObj);
            } else {
                // Known: check for containment role change, then target fingerprint change.
                // Note: simple attribute update (member name) was already applied by
                // updateSourceAttributes in Phase 1. This branch only handles the structural
                // part (family move) and commits the new fingerprint.
                EObject srcMember = corrIndex.inverse().get(targetObj);
                if (srcMember != null) {
                    Optional<EObject> entryOpt = CorrespondenceModel.findBySource(corrResource, srcMember);
                    if (entryOpt.isPresent()) {
                        // Check target containment role (Szenario B backward: Person moves to different list)
                        String _currentTgtRole = targetObj.eContainmentFeature() != null ? targetObj.eContainmentFeature().getName() : "";
                        String _storedTgtRole  = CorrespondenceModel.getTargetContainmentRole(entryOpt.get());
                        if (_storedTgtRole == null) _storedTgtRole = "";
                        if (!_currentTgtRole.equals(_storedTgtRole)) {
                            // Target containment role changed — update stored role.
                            // For F2P: all Persons are in PersonRegister.persons, so this is effectively a no-op.
                            CorrespondenceModel.updateTargetContainmentRole(entryOpt.get(), _currentTgtRole);
                        }
                        String storedFp = CorrespondenceModel.getTargetFingerprint(entryOpt.get());
                        if (storedFp == null || storedFp.isEmpty() || !currentFp.equals(storedFp)) {
                            if (srcMember instanceof ${sourcePackageName}.${rbm.sourceType()} m) {
                                // Update member's first name (Person rename → FamilyMember rename)
                                m.set${rbm.sourceAttr()?cap_first}(memberName);
                                // Check if the intermediate container (family) changed
                                EObject _currentContainer = m.eContainer();
                                String _oldKey = (_currentContainer instanceof ${sourcePackageName}.${rbm.intermediateType()} _oldFam)
                                        ? _oldFam.get${rbm.intermediateAttr()?cap_first}() : null;
                                if (!intermediateKey.equals(_oldKey)) {
                                    // Move: detach from old family, attach to new one
                                    EcoreUtil.remove(m);
                                    ${sourcePackageName}.${rbm.intermediateType()} _newFamily =
    <#if rbm.backwardPreferExistingParam()??>
                                            options.${rbm.backwardPreferExistingParam()}() ? familyLookup.getOrDefault(intermediateKey, null) : null;
    <#else>
                                            null;
    </#if>
                                    if (_newFamily == null) {
                                        _newFamily = ${sourceFactory}.eINSTANCE.create${rbm.intermediateType()}();
                                        _newFamily.set${rbm.intermediateAttr()?cap_first}(intermediateKey);
                                        ((EList<EObject>) sourceRoot.eGet(sourceRoot.eClass().getEStructuralFeature("${rbm.sourceContainerRef()}"))).add(_newFamily);
                                        familyLookup.put(intermediateKey, _newFamily);
                                    }
    <#list entry.roleGroups as group>
                                    <#if group?is_first>if<#else>} else if</#if> (targetObj instanceof ${targetPackageName}.${group.targetType}) {
        <#if group.singleRole?? && rbm.backwardPreferSingleRoleParam()??>
                                        if (options.${rbm.backwardPreferSingleRoleParam()}() && _newFamily.get${group.singleRole?cap_first}() == null) {
                                            _newFamily.set${group.singleRole?cap_first}(m);
                                        } else {
            <#list group.multiRoles as multiRole>
                                            _newFamily.get${multiRole?cap_first}().add(m);
            </#list>
                                        }
        <#else>
            <#list group.multiRoles as multiRole>
                                        _newFamily.get${multiRole?cap_first}().add(m);
            </#list>
        </#if>
    </#list>
                                    }
                                }
                                // Commit fingerprint after all structural changes are applied
                                CorrespondenceModel.updateTargetFingerprint(entryOpt.get(), currentFp);
                            }
                        }
                    }
                }
            }
        }
</#list>
    }

</#if>
