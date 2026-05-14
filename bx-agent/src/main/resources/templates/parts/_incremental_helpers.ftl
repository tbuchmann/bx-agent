    private static EObject createNewTargetObject(EObject srcObj, Options options) {
<#list typeMappings as typeMapping>
        if (srcObj instanceof ${sourcePackageName}.${typeMapping.sourceType()} src) {
            return transform${typeMapping.sourceType()}(src, options);
        }
</#list>
        return null;
    }

    /**
     * Returns true if the given source object is covered by a TypeMapping.
     * Used to exclude role-based source types from Phase 1 of transformIncremental,
     * so their change signal is preserved for mapRoleBasedTypesIncremental (Phase 1b).
     */
    private static boolean isCoveredByTypeMappingSource(EObject srcObj) {
<#list typeMappings as typeMapping>
        if (srcObj instanceof ${sourcePackageName}.${typeMapping.sourceType()}) return true;
</#list>
        return false;
    }

    /**
     * Returns true if the given target object is covered by a TypeMapping.
     * Used to exclude role-based target types from Phase 1 of transformIncrementalBack,
     * so their change signal is preserved for mapRoleBasedTypesIncrementalBack (Phase 1b).
     */
    private static boolean isCoveredByTypeMappingTarget(EObject tgtObj) {
<#list typeMappingGroups as group>
        if (tgtObj instanceof ${targetPackageName}.${group.targetType}) return true;
</#list>
        return false;
    }

<#if roleBasedTypeMappingModels?has_content>
    private static boolean isCoveredByRoleBasedSource(EObject srcObj) {
<#list roleBasedTypeMappingModels as entry>
<#assign rbm = entry.rbm>
        if (srcObj instanceof ${sourcePackageName}.${rbm.sourceType()}) return true;
</#list>
        return false;
    }

    private static boolean isCoveredByRoleBasedTarget(EObject tgtObj) {
<#list roleBasedTypeMappingModels as entry>
<#list entry.roleGroups as group>
        if (tgtObj instanceof ${targetPackageName}.${group.targetType}) return true;
</#list>
</#list>
        return false;
    }
</#if>

<#if syntheticObjectMappings?has_content>
    /**
     * Returns true if the given target object is a synthetic object (no source counterpart).
     * Synthetic objects must be skipped in all backward transformations.
     * Identified by type + constant name from nameExpression (does not require annotation mechanism).
     */
    private static boolean isSyntheticObject(EObject tgtObj) {
<#list syntheticObjectMappings as som>
        if (tgtObj instanceof ${targetPackageName}.${som.targetType()} _synObj) {
            Object _synName = _synObj.eGet(_synObj.eClass().getEStructuralFeature("name"));
            if (${som.nameExpression()}.equals(_synName)) return true;
        }
</#list>
        return false;
    }
</#if>

    /** Updates the target object's attributes to reflect changes in the source object. */
    private static void updateTargetAttributes(EObject srcObj, EObject tgtObj, Options options) {
<#list typeMappings as typeMapping>
        if (srcObj instanceof ${sourcePackageName}.${typeMapping.sourceType()} source
                && tgtObj instanceof ${targetPackageName}.${typeMapping.targetType()} target) {
    <#list attributeMappings as mapping>
        <#if mapping.sourceOwnerType()?? && (mapping.sourceOwnerType() == typeMapping.sourceType() || !typeMappings?filter(tm -> tm.sourceType() == mapping.sourceOwnerType())?has_content) && mapping.forwardExpression()?? && mapping.targetAttr()?? && mapping.targetAttr()?has_content>
            target.set${mapping.targetAttr()?cap_first}(${mapping.forwardExpression()});
        </#if>
    </#list>
        }
</#list>
    }

    /**
     * Adds a newly created target object to its correct containment in the target model.
     * Uses the corrIndex to find the corresponding target parent.
     */
    private static void addToTargetContainment(
            EObject srcObj, EObject newTarget,
            Resource existingTarget,
            com.google.common.collect.BiMap<EObject, EObject> corrIndex) {
        EObject srcParent = srcObj.eContainer();
        if (srcParent == null) {
            existingTarget.getContents().add(newTarget);
            return;
        }
        EObject targetParent = corrIndex.get(srcParent);
        if (targetParent == null) return;

<#list referenceMappings as refMapping>
    <#if refMapping.sourceIsContainment()>
        if (srcParent instanceof ${sourcePackageName}.${refMapping.sourceRefOwnerType()}
                && newTarget instanceof ${targetPackageName}.${refMapping.targetRefTargetType()}
                && targetParent instanceof ${targetPackageName}.${refMapping.targetRefOwnerType()}) {
            org.eclipse.emf.ecore.EStructuralFeature _tgtFeat = targetParent.eClass().getEStructuralFeature("${refMapping.targetRef()}");
            if (_tgtFeat != null) {
                if (_tgtFeat.isMany()) {
                    @SuppressWarnings("unchecked")
                    EList<EObject> _tgtList = (EList<EObject>) targetParent.eGet(_tgtFeat);
                    _tgtList.add(newTarget);
                } else {
                    targetParent.eSet(_tgtFeat, newTarget);
                }
            }
        }
    </#if>
</#list>
        // Reflective fallback: if no explicit mapping matched, use the source containment
        // feature name to locate the corresponding containment feature on the target parent.
        // Handles cases where the LLM omits the containment referenceMapping (e.g. same-named features).
        if (newTarget.eContainer() == null && srcObj.eContainmentFeature() != null) {
            org.eclipse.emf.ecore.EStructuralFeature _tgtFeat =
                    targetParent.eClass().getEStructuralFeature(srcObj.eContainmentFeature().getName());
            if (_tgtFeat instanceof org.eclipse.emf.ecore.EReference _tgtRef && _tgtRef.isContainment()) {
                if (_tgtRef.isMany()) {
                    @SuppressWarnings("unchecked")
                    EList<EObject> _tgtList = (EList<EObject>) targetParent.eGet(_tgtRef);
                    _tgtList.add(newTarget);
                } else {
                    targetParent.eSet(_tgtRef, newTarget);
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Backward Incremental Helper Methods
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Computes a fingerprint string for a target EObject (used for backward incremental detection).
     * Uses targetKeyAttributes from TypeMapping/RoleBasedTypeMapping; falls back to all attributes.
     */
    private static String computeFingerprintBack(EObject obj) {
        StringBuilder sb = new StringBuilder();
        sb.append(obj.eClass().getName()).append(":");
<#list typeMappingGroups as group>
        if (obj instanceof ${targetPackageName}.${group.targetType} typed) {
    <#assign tm = group.mappings[0]>
    <#if tm.targetKeyAttributes()?has_content>
        <#list tm.targetKeyAttributes() as attr>
            <#if attr?contains(".")><#assign _kparts = attr?split(".")>
            if (typed.get${_kparts[0]?cap_first}() != null) { sb.append(typed.get${_kparts[0]?cap_first}().get${_kparts[1]?cap_first}()).append("|"); }
            <#else>
            sb.append(typed.get${attr?cap_first}()).append("|");
            </#if>
        </#list>
            // Non-key attributes for change detection
            for (EAttribute ea : obj.eClass().getEAllAttributes()) {
                if (<#list tm.targetKeyAttributes() as _ka>"${_ka}".equals(ea.getName())<#sep> || </#sep></#list><#if excludedAttributes?has_content> || <#list excludedAttributes as _ea>"${_ea}".equals(ea.getName())<#sep> || </#sep></#list></#if>) continue;
                sb.append(obj.eGet(ea)).append("|");
            }
    <#else>
            for (EAttribute ea : obj.eClass().getEAllAttributes()) {
<#if excludedAttributes?has_content>
                if (<#list excludedAttributes as _ea>"${_ea}".equals(ea.getName())<#sep> || </#sep></#list>) continue;
</#if>
                sb.append(obj.eGet(ea)).append("|");
            }
    </#if>
            return sb.toString();
        }
</#list>
<#list roleBasedTypeMappingModels as entry>
    <#assign rbm = entry.rbm>
    <#list entry.roleGroups as group>
        if (obj instanceof ${targetPackageName}.${group.targetType} typed) {
        <#if rbm.targetKeyAttributes()?has_content>
            <#list rbm.targetKeyAttributes() as attr>
            <#if attr?contains(".")><#assign _kparts = attr?split(".")>
            if (typed.get${_kparts[0]?cap_first}() != null) { sb.append(typed.get${_kparts[0]?cap_first}().get${_kparts[1]?cap_first}()).append("|"); }
            <#else>
            sb.append(typed.get${attr?cap_first}()).append("|");
            </#if>
            </#list>
            // Non-key attributes for change detection
            for (EAttribute ea : obj.eClass().getEAllAttributes()) {
                if (<#list rbm.targetKeyAttributes() as _ka>"${_ka}".equals(ea.getName())<#sep> || </#sep></#list><#if excludedAttributes?has_content> || <#list excludedAttributes as _ea>"${_ea}".equals(ea.getName())<#sep> || </#sep></#list></#if>) continue;
                sb.append(obj.eGet(ea)).append("|");
            }
        <#else>
            for (EAttribute ea : obj.eClass().getEAllAttributes()) {
<#if excludedAttributes?has_content>
                if (<#list excludedAttributes as _ea>"${_ea}".equals(ea.getName())<#sep> || </#sep></#list>) continue;
</#if>
                sb.append(obj.eGet(ea)).append("|");
            }
        </#if>
            return sb.toString();
        }
    </#list>
</#list>
<#if aggregationMappings?has_content>
<#list aggregationMappings as agg>
        if (obj instanceof ${targetPackageName}.${agg.targetType()} typed) {
    <#if agg.targetKeyAttributes()?has_content>
        <#list agg.targetKeyAttributes() as attr>
            sb.append(typed.get${attr?cap_first}()).append("|");
        </#list>
            // Non-key attributes for change detection
            for (EAttribute ea : obj.eClass().getEAllAttributes()) {
                if (<#list agg.targetKeyAttributes() as _ka>"${_ka}".equals(ea.getName())<#sep> || </#sep></#list><#if excludedAttributes?has_content> || <#list excludedAttributes as _ea>"${_ea}".equals(ea.getName())<#sep> || </#sep></#list></#if>) continue;
                sb.append(obj.eGet(ea)).append("|");
            }
    <#else>
            for (EAttribute ea : obj.eClass().getEAllAttributes()) {
<#if excludedAttributes?has_content>
                if (<#list excludedAttributes as _ea>"${_ea}".equals(ea.getName())<#sep> || </#sep></#list>) continue;
</#if>
                sb.append(obj.eGet(ea)).append("|");
            }
    </#if>
            return sb.toString();
        }
</#list>
</#if>
        // Generic fallback: all attributes reflectively
        for (EAttribute ea : obj.eClass().getEAllAttributes()) {
<#if excludedAttributes?has_content>
            if (<#list excludedAttributes as _ea>"${_ea}".equals(ea.getName())<#sep> || </#sep></#list>) continue;
</#if>
            sb.append(obj.eGet(ea)).append("|");
        }
        return sb.toString();
    }

    /**
     * Computes the expected target fingerprint for a source object as if it were transformed forward.
     * Used in sync() for fuzzy matching: links existing unmatched objects instead of creating new ones.
     * Returns null if no mapping applies.
     */
    private static String computeExpectedTargetFingerprint(EObject srcObj) {
        // TypeMapping types: create a temporary target and compute its fingerprint.
        EObject _tmp = createNewTargetObject(srcObj, Options.defaults());
        if (_tmp != null) return computeFingerprintBack(_tmp);
<#list roleBasedTypeMappingModels as entry>
    <#assign rbm = entry.rbm>
        // Role-based: ${rbm.sourceType()} held in ${rbm.intermediateType()}
        if (srcObj instanceof ${sourcePackageName}.${rbm.sourceType()} member) {
            EObject _cEObj = member.eContainer();
            if (!(_cEObj instanceof ${sourcePackageName}.${rbm.intermediateType()})) return null;
            ${sourcePackageName}.${rbm.intermediateType()} family = (${sourcePackageName}.${rbm.intermediateType()}) _cEObj;
            String _role = member.eContainmentFeature() != null ? member.eContainmentFeature().getName() : "";
            String _targetName = ${rbm.nameExpression()};
    <#list entry.roleEntries as roleEntry>
            if ("${roleEntry.role}".equals(_role)) {
                ${targetPackageName}.${roleEntry.targetType} _tmpTarget = ${targetFactory}.eINSTANCE.create${roleEntry.targetType}();
                _tmpTarget.set${rbm.targetAttr()?cap_first}(_targetName);
                return computeFingerprintBack(_tmpTarget);
            }
    </#list>
        }
</#list>
        return null;
    }

    /**
     * Computes the expected source fingerprint for a target object as if it were transformed backward.
     * Used in sync() for fuzzy matching: links existing unmatched objects instead of creating new ones.
     * Returns null if no mapping applies.
     */
    private static String computeExpectedSourceFingerprint(EObject tgtObj) {
        // TypeMapping types: create a temporary source and compute its fingerprint.
        EObject _tmp = createNewSourceObject(tgtObj, Options.defaults());
        if (_tmp != null) return computeFingerprint(_tmp);
<#list roleBasedTypeMappingModels as entry>
    <#assign rbm = entry.rbm>
        // Role-based target types
    <#list entry.roleGroups as group>
        if (tgtObj instanceof ${targetPackageName}.${group.targetType} targetObj) {
            String _memberName = ${rbm.backwardMemberNameExpression()};
            ${sourcePackageName}.${rbm.sourceType()} _tmpMember = ${sourceFactory}.eINSTANCE.create${rbm.sourceType()}();
            _tmpMember.set${rbm.sourceAttr()?cap_first}(_memberName);
            return computeFingerprint(_tmpMember);
        }
    </#list>
</#list>
        return null;
    }

<#if roleBasedTypeMappingModels?has_content>
    /**
     * Computes the composite source fingerprint for role-based types, matching the format
     * used by mapRoleBasedTypesIncremental for storage:
     *   intermediateKey + "|" + computeFingerprint(member)
     * Used in sync() Step 1 so that _srcChg correctly reflects actual source changes
     * rather than always being true due to format mismatch.
     */
    private static String computeRoleBasedSourceFingerprint(EObject srcObj) {
<#list roleBasedTypeMappingModels as entry>
<#assign rbm = entry.rbm>
        if (srcObj instanceof ${sourcePackageName}.${rbm.sourceType()} member) {
            EObject _container = member.eContainer();
            if (_container instanceof ${sourcePackageName}.${rbm.intermediateType()} family) {
                return family.get${rbm.intermediateAttr()?cap_first}() + "|" + computeFingerprint(member);
            }
        }
</#list>
        return computeFingerprint(srcObj);
    }
</#if>

<#if roleBasedTypeMappingModels?has_content>
    /**
     * Name-only fingerprint for role-based target types.
     * Compares only the mapped attribute (e.g. "name"), ignoring target-only attributes
     * like "birthday" that would not be set on a freshly-created temporary object.
     * Used exclusively in the alternative fuzzy-match path in sync().
     */
    private static String computeNameOnlyFingerprintBack(EObject obj) {
<#list roleBasedTypeMappingModels as entry>
<#assign rbm = entry.rbm>
<#list entry.roleGroups as group>
        if (obj instanceof ${targetPackageName}.${group.targetType} _typed) {
            return obj.eClass().getName() + ":" + _typed.get${rbm.targetAttr()?cap_first}() + "|";
        }
</#list>
</#list>
        return computeFingerprintBack(obj);
    }

    /**
     * Alternative expected target fingerprint for role-based types.
     * Uses the raw source attribute value (e.g., member first name only) instead of the
     * full nameExpression (e.g., "Family, Member"), and compares only the mapped attribute
     * via computeNameOnlyFingerprintBack to avoid false negatives when the target object
     * has additional target-only attributes set (e.g. birthday).
     */
    private static String computeAlternativeTargetFingerprint(EObject srcObj) {
<#list roleBasedTypeMappingModels as entry>
<#assign rbm = entry.rbm>
        if (srcObj instanceof ${sourcePackageName}.${rbm.sourceType()} member) {
            String _role = member.eContainmentFeature() != null ? member.eContainmentFeature().getName() : "";
            Object _rawName = member.eGet(member.eClass().getEStructuralFeature("${rbm.sourceAttr()}"));
            if (!(_rawName instanceof String _memberName)) return null;
<#list entry.roleEntries as roleEntry>
            if ("${roleEntry.role}".equals(_role)) {
                ${targetPackageName}.${roleEntry.targetType} _tmpTarget = ${targetFactory}.eINSTANCE.create${roleEntry.targetType}();
                _tmpTarget.set${rbm.targetAttr()?cap_first}(_memberName);
                return computeNameOnlyFingerprintBack(_tmpTarget);
            }
</#list>
        }
</#list>
        return null;
    }
</#if>

    /** Creates a new source object for the given target EObject, or null if no mapping exists. */
    private static EObject createNewSourceObject(EObject tgtObj, Options options) {
<#list typeMappingGroups as group>
    <#if group.hasMultiple>
        if (tgtObj instanceof ${targetPackageName}.${group.targetType} _typed) {
        <#list group.mappings as tm>
            <#if !tm?is_first>} else </#if>if (${tm.backwardCondition()?replace("target", "_typed")}) {
                return transformBack${group.targetType}As${tm.sourceType()}(_typed, options);
        </#list>
            }
        }
    <#else>
        if (tgtObj instanceof ${targetPackageName}.${group.targetType} tgt) {
            return transformBack${group.targetType}As${group.mappings[0].sourceType()}(tgt, options);
        }
    </#if>
</#list>
        return null;
    }

    /**
     * Updates the source object's attributes to reflect changes in the target object (backward).
     * Handles both TypeMapping attribute expressions and role-based simple attribute updates.
     * Structural changes (e.g. moving a FamilyMember to a different Family) are handled
     * by mapRoleBasedTypesIncrementalBack after this method runs.
     */
    @SuppressWarnings("unchecked")
    private static void updateSourceAttributes(EObject tgtObj, EObject srcObj, Options options) {
<#list typeMappings as typeMapping>
        if (tgtObj instanceof ${targetPackageName}.${typeMapping.targetType()} target
                && srcObj instanceof ${sourcePackageName}.${typeMapping.sourceType()} source) {
    <#list attributeMappings as mapping>
        <#if mapping.targetOwnerType()?? && (mapping.targetOwnerType() == typeMapping.targetType() || !typeMappings?filter(tm -> tm.targetType() == mapping.targetOwnerType())?has_content) && mapping.sourceOwnerType()?? && (mapping.sourceOwnerType() == typeMapping.sourceType() || !typeMappings?filter(tm -> tm.sourceType() == mapping.sourceOwnerType())?has_content) && mapping.backwardExpression()?? && mapping.sourceAttr()?? && mapping.sourceAttr()?has_content>
            source.set${mapping.sourceAttr()?cap_first}(${mapping.backwardExpression()});
        </#if>
    </#list>
        }
</#list>
<#list roleBasedTypeMappingModels as entry>
    <#assign rbm = entry.rbm>
        // Role-based backward attribute update: apply backwardMemberNameExpression
    <#list entry.roleGroups as group>
        if (tgtObj instanceof ${targetPackageName}.${group.targetType}
                && srcObj instanceof ${sourcePackageName}.${rbm.sourceType()} m) {
            // Alias to match the variable name expected by backwardMemberNameExpression
            ${targetPackageName}.${rbm.targetContainerElementType()} targetObj =
                    (${targetPackageName}.${rbm.targetContainerElementType()}) tgtObj;
            String _memberName = ${rbm.backwardMemberNameExpression()};
            m.set${rbm.sourceAttr()?cap_first}(_memberName);
        }
    </#list>
</#list>
    }

    /**
     * Adds a newly created source object to its correct containment in the source model.
     * Uses corrIndex.inverse() to find the corresponding source parent from the target parent.
     */
    private static void addToSourceContainment(
            EObject tgtObj, EObject newSource,
            Resource existingSource,
            com.google.common.collect.BiMap<EObject, EObject> corrIndex) {
        EObject tgtParent = tgtObj.eContainer();
        if (tgtParent == null) {
            existingSource.getContents().add(newSource);
            return;
        }
        EObject srcParent = corrIndex.inverse().get(tgtParent);
        if (srcParent == null) return;

<#list referenceMappings as refMapping>
    <#if refMapping.targetIsContainment()>
        if (tgtParent instanceof ${targetPackageName}.${refMapping.targetRefOwnerType()}
                && newSource instanceof ${sourcePackageName}.${refMapping.sourceRefTargetType()}
                && srcParent instanceof ${sourcePackageName}.${refMapping.sourceRefOwnerType()}) {
            org.eclipse.emf.ecore.EStructuralFeature _srcFeat = srcParent.eClass().getEStructuralFeature("${refMapping.sourceRef()}");
            if (_srcFeat != null) {
                if (_srcFeat.isMany()) {
                    @SuppressWarnings("unchecked")
                    EList<EObject> _srcList = (EList<EObject>) srcParent.eGet(_srcFeat);
                    _srcList.add(newSource);
                } else {
                    srcParent.eSet(_srcFeat, newSource);
                }
            }
        }
    </#if>
</#list>
        // Reflective fallback: if no explicit mapping matched, use the target containment
        // feature name to locate the corresponding containment feature on the source parent.
        if (newSource.eContainer() == null && tgtObj.eContainmentFeature() != null) {
            org.eclipse.emf.ecore.EStructuralFeature _srcFeat =
                    srcParent.eClass().getEStructuralFeature(tgtObj.eContainmentFeature().getName());
            if (_srcFeat instanceof org.eclipse.emf.ecore.EReference _srcRef && _srcRef.isContainment()) {
                if (_srcRef.isMany()) {
                    @SuppressWarnings("unchecked")
                    EList<EObject> _srcList = (EList<EObject>) srcParent.eGet(_srcRef);
                    _srcList.add(newSource);
                } else {
                    srcParent.eSet(_srcRef, newSource);
                }
            }
        }
    }

<#if rootSourceType??>
    // ════════════════════════════════════════════════════════════════════════
    // Single-Object Transformation Methods (kept for backwards compatibility)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Transforms a single ${rootSourceType} object.
     * @deprecated Use transform(Resource, Resource) for full model transformation.
     */
    @Deprecated
    public static ${targetPackageName}.${rootTargetType} transform(${sourcePackageName}.${rootSourceType} source) {
        return transform${rootSourceType}(source, Options.defaults());
    }

    /**
     * Transforms a single ${rootTargetType} object back.
     * @deprecated Use transformBack(Resource, Resource) for full model transformation.
     */
    @Deprecated
    public static ${sourcePackageName}.${rootSourceType} transformBack(${targetPackageName}.${rootTargetType} target) {
        return transformBack${rootTargetType}As${rootSourceType}(target, Options.defaults());
    }
</#if>

    // ════════════════════════════════════════════════════════════════════════
    // Synchronisation: sync() — both models changed independently
    // ════════════════════════════════════════════════════════════════════════

    /**
     * BEKANNTER RANDFALL – Delete-Recreate mit gleichem Fingerprint:
     *
     * Wenn ein Objekt gelöscht und ein neues Objekt mit identischem
     * Fingerprint (gleiche Schlüsselattribute) angelegt wird, hat das
     * neue Objekt keinen Korrespondenzeintrag. Es wird daher in
     * Schritt 5 als "neu" behandelt und erneut transformiert, obwohl
     * es konzeptuell ein Update des gelöschten Objekts sein könnte.
     *
     * Konsequenz: Im Zielmodell entsteht ein neues Objekt, während
     * das alte Zielobjekt über den verwaisten corrEntry (Partition 4)
     * gelöscht wird. Das Ergebnis ist funktional korrekt, aber
     * Querverweise anderer Objekte auf das alte Zielobjekt gehen
     * verloren (sie zeigen nach dem Sync ins Leere, bis Phase 6
     * Cross-References neu auflöst).
     *
     * Lösungsansatz für spätere Ausbaustufen: Persistente Objekt-IDs
     * (XMI-IDs) statt Fingerprinting als primäre Identifikation.
     * Dann ist Delete-Recreate eindeutig vom echten Neuanlegen
     * unterscheidbar.
     *
     * Siehe auch: PROJECT_OVERVIEW.md, Bekannte Einschränkungen Nr. 2
     */
