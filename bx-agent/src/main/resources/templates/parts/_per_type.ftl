<#list typeMappings as typeMapping>
    /**
     * Transforms ${typeMapping.sourceType()} → ${typeMapping.targetType()}
     */
    private static ${targetPackageName}.${typeMapping.targetType()} transform${typeMapping.sourceType()}(${sourcePackageName}.${typeMapping.sourceType()} source, Options options) {
        if (source == null) {
            return null;
        }

        ${targetPackageName}.${typeMapping.targetType()} target = ${targetFactory}.eINSTANCE.create${typeMapping.targetType()}();

<#list attributeMappings as mapping>
    <#if mapping.sourceOwnerType()?? && (mapping.sourceOwnerType() == typeMapping.sourceType() || !typeMappings?filter(tm -> tm.sourceType() == mapping.sourceOwnerType())?has_content) && mapping.forwardExpression()?? && mapping.targetAttr()?? && mapping.targetAttr()?has_content>
        target.set${mapping.targetAttr()?cap_first}(${mapping.forwardExpression()});
    </#if>
</#list>
<#if typeMapping.forwardAnnotations()?has_content>
        // Forward annotations for ${typeMapping.sourceType()} → ${typeMapping.targetType()}
    <#list typeMapping.forwardAnnotations() as ann>
        _addAnnotation(target, "${ann}");
    </#list>
</#if>
        return target;
    }

    /**
     * Transforms ${typeMapping.targetType()} → ${typeMapping.sourceType()} (backward)
     */
    private static ${sourcePackageName}.${typeMapping.sourceType()} transformBack${typeMapping.targetType()}As${typeMapping.sourceType()}(${targetPackageName}.${typeMapping.targetType()} target, Options options) {
        if (target == null) {
            return null;
        }

        ${sourcePackageName}.${typeMapping.sourceType()} source = ${sourceFactory}.eINSTANCE.create${typeMapping.sourceType()}();

<#list attributeMappings as mapping>
    <#if mapping.targetOwnerType()?? && (mapping.targetOwnerType() == typeMapping.targetType() || !typeMappings?filter(tm -> tm.targetType() == mapping.targetOwnerType())?has_content) && mapping.sourceOwnerType()?? && (mapping.sourceOwnerType() == typeMapping.sourceType() || !typeMappings?filter(tm -> tm.sourceType() == mapping.sourceOwnerType())?has_content)>
        <#if mapping.backwardExpression()?? && mapping.sourceAttr()?? && mapping.sourceAttr()?has_content>
        source.set${mapping.sourceAttr()?cap_first}(${mapping.backwardExpression()});
        <#elseif mapping.sourceAttr()?? && mapping.sourceAttr()?has_content>
        // ${mapping.targetAttr()} → ${mapping.sourceAttr()}: no backward mapping (skipped)
        </#if>
    </#if>
</#list>
        return source;
    }

</#list>
