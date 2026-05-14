
    // ════════════════════════════════════════════════════════════════════════
    // Incremental Helper Methods
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Computes a fingerprint string for an EObject.
     * Uses sourceKeyAttributes from the TypeMapping when available; falls back to all attributes.
     */
    private static String computeFingerprint(EObject obj) {
        StringBuilder sb = new StringBuilder();
        sb.append(obj.eClass().getName()).append(":");
        if (obj.eContainer() != null) {
            EStructuralFeature _cfContainerNameFeat = obj.eContainer().eClass().getEStructuralFeature("name");
            if (_cfContainerNameFeat != null) { sb.append("@").append(obj.eContainer().eGet(_cfContainerNameFeat)).append(":"); }
        }
<#list typeMappings as typeMapping>
        if (obj instanceof ${sourcePackageName}.${typeMapping.sourceType()} typed) {
    <#if typeMapping.sourceKeyAttributes()?has_content>
        <#list typeMapping.sourceKeyAttributes() as attr>
            <#if attr?contains(".")><#assign _kparts = attr?split(".")>
            if (typed.get${_kparts[0]?cap_first}() != null) { sb.append(typed.get${_kparts[0]?cap_first}().get${_kparts[1]?cap_first}()).append("|"); }
            <#else>
            sb.append(typed.get${attr?cap_first}()).append("|");
            </#if>
        </#list>
            // Non-key attributes for change detection
            for (EAttribute ea : obj.eClass().getEAllAttributes()) {
                if (<#list typeMapping.sourceKeyAttributes() as _ka>"${_ka}".equals(ea.getName())<#sep> || </#sep></#list><#if excludedAttributes?has_content> || <#list excludedAttributes as _ea>"${_ea}".equals(ea.getName())<#sep> || </#sep></#list></#if>) continue;
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
        if (obj instanceof ${sourcePackageName}.${rbm.sourceType()} typed) {
    <#if rbm.sourceKeyAttributes()?has_content>
        <#list rbm.sourceKeyAttributes() as attr>
            <#if attr?contains(".")><#assign _kparts = attr?split(".")>
            if (typed.get${_kparts[0]?cap_first}() != null) { sb.append(typed.get${_kparts[0]?cap_first}().get${_kparts[1]?cap_first}()).append("|"); }
            <#else>
            sb.append(typed.get${attr?cap_first}()).append("|");
            </#if>
        </#list>
            // Non-key attributes for change detection
            for (EAttribute ea : obj.eClass().getEAllAttributes()) {
                if (<#list rbm.sourceKeyAttributes() as _ka>"${_ka}".equals(ea.getName())<#sep> || </#sep></#list><#if excludedAttributes?has_content> || <#list excludedAttributes as _ea>"${_ea}".equals(ea.getName())<#sep> || </#sep></#list></#if>) continue;
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
<#if aggregationMappings?has_content>
<#list aggregationMappings as agg>
        if (obj instanceof ${sourcePackageName}.${agg.sourceType()} typed) {
    <#if agg.sourceKeyAttributes()?has_content>
        <#list agg.sourceKeyAttributes() as attr>
            sb.append(typed.get${attr?cap_first}()).append("|");
        </#list>
            // Non-key attributes for change detection
            for (EAttribute ea : obj.eClass().getEAllAttributes()) {
                if (<#list agg.sourceKeyAttributes() as _ka>"${_ka}".equals(ea.getName())<#sep> || </#sep></#list><#if excludedAttributes?has_content> || <#list excludedAttributes as _ea>"${_ea}".equals(ea.getName())<#sep> || </#sep></#list></#if>) continue;
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
     * Finds an EObject in a resource by identifier (XMI-ID first, then URI fragment).
     */
    private static EObject findInModel(Resource resource, String identifier) {
        if (resource instanceof XMIResource xmi) {
            EObject obj = xmi.getEObject(identifier);
            if (obj != null) return obj;
        }
        try {
            return resource.getEObject(identifier);
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns all EObjects in a resource as a flat list. */
    private static List<EObject> allSourceObjects(Resource resource) {
        List<EObject> all = new ArrayList<>();
        for (Iterator<EObject> it = resource.getAllContents(); it.hasNext(); ) {
            all.add(it.next());
        }
        return all;
    }

