package dev.bxagent.mapping;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bxagent.llm.LlmClient;
import dev.bxagent.metamodel.MetamodelSummary;

import java.util.List;
import java.util.Map;

/**
 * Extracts transformation mappings from two metamodels using an LLM.
 * This is the most complex component - it constructs prompts, calls the LLM,
 * and parses structured JSON responses.
 */
public class MappingExtractor {

    private static final int MAX_RETRIES = 3;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    /** Raw LLM response from the last successful extract() call. Null if not yet called. */
    private String lastRawResponse;

    /**
     * System prompt that instructs the LLM on the task and expected JSON schema.
     */
    private static final String SYSTEM_PROMPT = """
        You are an expert in model-driven engineering and EMF (Eclipse Modeling Framework).
        Your task is to analyze two EMF metamodels and a natural language transformation description,
        then extract a precise, machine-readable transformation specification.

        You MUST respond with a single valid JSON object matching exactly this schema:
        {
          "sourcePackageName": "string",
          "targetPackageName": "string",
          "generatedClassName": "string (derived automatically, you may omit this field)",
          "typeMappings": [
            { "sourceType": "string", "targetType": "string",
              "sourceKeyAttributes": ["string"],
              "targetKeyAttributes": ["string"],
              "backwardCondition": "string or null",
              "forwardAnnotations": ["string (annotation tag strings to attach to created target object; use [] if no annotations)"] }
          ],
          "attributeMappings": [
            {
              "sourceOwnerType": "string (unqualified EClass name that declares this attribute in the source metamodel)",
              "sourceAttr": "string",
              "sourceAttrType": "string (EMF type, e.g. EString)",
              "targetOwnerType": "string (unqualified EClass name that declares this attribute in the target metamodel)",
              "targetAttr": "string",
              "targetAttrType": "string",
              "forwardExpression": "string (valid Java expression, use variable 'source')",
              "backwardExpression": "string or null (valid Java expression, use variable 'target')"
            }
          ],
          "referenceMappings": [
            {
              "sourceRefOwnerType": "string (EClass name that owns this reference in the source metamodel)",
              "sourceRef": "string",
              "sourceRefTargetType": "string",
              "targetRefOwnerType": "string (EClass name that owns this reference in the target metamodel)",
              "targetRef": "string",
              "targetRefTargetType": "string",
              "sourceIsMany": boolean,
              "targetIsMany": boolean,
              "sourceIsContainment": boolean,
              "targetIsContainment": boolean,
              "sourceIsEOpposite": boolean,
              "targetIsEOpposite": boolean
            }
          ],
          "unresolvedMappings": ["string (describe what is unclear or not invertible)"],
          "transformationOptions": [
            {
              "name": "string (camelCase identifier, e.g. 'nameBackwardSplitStrategy')",
              "type": "string (Java type: 'String' or 'boolean')",
              "defaultValue": "string (default value, e.g. 'firstSpace' or 'true')",
              "description": "string (human-readable explanation of what this option controls)"
            }
          ],
          "roleBasedTypeMappings": [
            {
              "sourceType": "string (type being distributed, e.g. 'FamilyMember')",
              "intermediateType": "string (container type that disappears, e.g. 'Family')",
              "roleToTargetType": { "roleName": "targetTypeName" },
              "roleIsMany": { "roleName": true_or_false },
              "targetAttr": "string (attribute to set on created target object, e.g. 'name')",
              "nameExpression": "string (Java expr using 'member' for sourceType instance, 'family' for intermediateType instance)",
              "sourceContainerRef": "string (reference name from source root to intermediateType, e.g. 'families')",
              "targetContainerRef": "string (reference name from target root to target objects, e.g. 'persons')",
              "targetContainerElementType": "string (declared element type of targetContainerRef, e.g. 'Person')",
              "intermediateAttr": "string (attribute on intermediateType to set in backward, e.g. 'name')",
              "sourceAttr": "string (attribute on sourceType to set in backward, e.g. 'name')",
              "backwardFamilyNameExpression": "string (Java expr using 'targetObj', extracts intermediateType key)",
              "backwardMemberNameExpression": "string (Java expr using 'targetObj', extracts member name)",
              "backwardPreferExistingParam": "string or null (name of BackwardConfig param controlling reuse of existing intermediate)",
              "backwardPreferSingleRoleParam": "string or null (name of BackwardConfig param controlling single-role preference)",
              "sourceKeyAttributes": ["string"] (natural key attrs of sourceType for fingerprinting; [] if none),
              "targetKeyAttributes": ["string"] (natural key attrs of target types for fingerprinting; [] if none)
            }
          ],
          "backwardConfigs": [
            {
              "parameterName": "string (camelCase identifier, e.g. 'preferExistingFamily')",
              "parameterType": "string (Java type: 'boolean' or 'String')",
              "defaultValue": "string (e.g. 'true')",
              "description": "string (human-readable explanation)"
            }
          ],
          "edgeMaterializationMappings": [
            {
              "sourceOwnerType": "string (EClass in source that has the reference, e.g. 'Transition')",
              "sourceRef": "string (reference name on sourceOwnerType, e.g. 'trgT2P')",
              "sourceRefTargetType": "string (EClass at the other end of sourceRef, e.g. 'Place')",
              "edgeType": "string (target EClass to create per pair, e.g. 'TPEdge')",
              "edgeFromRef": "string (reference on edgeType pointing to mapped sourceOwnerType, e.g. 'fromTransition')",
              "edgeToRef": "string (reference on edgeType pointing to mapped sourceRefTargetType, e.g. 'toPlace')",
              "edgeContainerRef": "string (reference on target root holding edge objects, e.g. 'elements')"
            }
          ],
          "aggregationMappings": [
            {
              "sourceType": "string (EClass whose instances are grouped, e.g. 'Element')",
              "targetType": "string (EClass representing one group, e.g. 'Element')",
              "groupBySourceAttr": "string (attribute on sourceType used as grouping key, e.g. 'value')",
              "groupByTargetAttr": "string (attribute on targetType receiving the key, e.g. 'value')",
              "countTargetAttr": "string (attribute on targetType receiving the group size, e.g. 'multiplicity')",
              "sourceContainerType": "string (EClass that contains sourceType objects, e.g. 'MyBag')",
              "sourceContainerRef": "string (containment reference name from sourceContainerType, e.g. 'elements')",
              "targetContainerType": "string (EClass that contains targetType objects, e.g. 'MyBag')",
              "targetContainerRef": "string (containment reference name from targetContainerType, e.g. 'elements')",
              "sourceKeyAttributes": ["string (natural key attrs of sourceType for fingerprinting)"],
              "targetKeyAttributes": ["string (natural key attrs of targetType for fingerprinting)"]
            }
          ],
          "structuralDeduplicationMappings": [
            {
              "abstractSourceType": "string (abstract supertype in source, e.g. 'Expression')",
              "abstractTargetType": "string (abstract supertype in target, e.g. 'Expression')",
              "concreteTypes": [
                {
                  "sourceType": "string (concrete source EClass, e.g. 'Operator')",
                  "targetType": "string (concrete target EClass, e.g. 'Operator')",
                  "keyAttributes": ["string (attributes forming local identity key for this type, e.g. 'op')"],
                  "childRefs": ["string (containment refs in source to follow recursively, e.g. 'left', 'right')"]
                }
              ],
              "sourceContainerType": "string (EClass containing the source tree root, e.g. 'Model')",
              "sourceContainerRef": "string (ref from sourceContainerType to tree root(s), e.g. 'expr')",
              "sourceContainerRefIsMany": "boolean (true if sourceContainerRef is multi-valued)",
              "targetContainerType": "string (EClass containing the flat target list, e.g. 'Model')",
              "targetContainerRef": "string (ref from targetContainerType to flat list of shared nodes, e.g. 'exprs')",
              "sourceKeyAttributes": ["string (usually [] for structural dedup)"],
              "targetKeyAttributes": ["string (usually [] for structural dedup)"]
            }
          ],
          "conditionalTypeMappings": [
            {
              "sourceType": "string (EClass of the iterated elements, e.g. 'EAttribute')",
              "sourceContainerOwnerType": "string (EClass that owns the iterated feature, e.g. 'EClass')",
              "sourceContainerRef": "string (feature name — containment OR cross-ref, e.g. 'eStructuralFeatures')",
              "branches": [
                {
                  "condition": "string or null (Java boolean expr on '_ctmSrc' (the loop variable for the source object); null = else/unconditional branch)",
                  "targetType": "string or null (target EClass to create; null = skip, create nothing)",
                  "targetPlacementType": "string ('PARENT' | 'ROOT' | 'REFERENCE_TARGET')",
                  "targetPlacementRef": "string or null (only for REFERENCE_TARGET: EStructuralFeature name on source, e.g. 'eType')",
                  "targetContainerRef": "string (feature name on the placement object to add the new target to)",
                  "nameExpression": "string or null (Java expr; 'source' is a local alias for '_ctmSrc', '_ctmParentSrc' is its container EObject; null = no setName)",
                  "forwardAnnotations": ["string (annotation tag strings to attach to the created target object)"],
                  "backwardCondition": "string or null (Java boolean expr on '_ctmBwTyped' (the CTM target object being tested, NOT the target root) to identify this branch during backward)",
                  "backwardParentExpression": "string or null (Java expr returning source-side container EObject; vars: target, objectMapInverse, _ctmBwSrcRes (the source Resource); null = use objectMapInverse.get(target.eContainer()))"
                }
              ],
              "sourceKeyAttributes": ["string"],
              "targetKeyAttributes": ["string"]
            }
          ],
          "syntheticObjectMappings": [
            {
              "sourceContainerType": "string (one synthetic object is created per instance of this EClass)",
              "targetType": "string (target EClass to instantiate, e.g. 'Table')",
              "targetContainerRef": "string (feature on the mapped target container to add the object to)",
              "nameExpression": "string (Java string expr using 'source' for the target object's name)",
              "forwardAnnotations": ["string (annotation tag strings to attach)"],
              "columns": [
                {
                  "name": "string (column name)",
                  "sqlType": "string (SQL type string, e.g. 'int')",
                  "properties": ["string (EEnum literal names on Column.properties, e.g. 'NotNull', 'AutoIncrement')"]
                }
              ],
              "createPrimaryKey": "boolean (if true, create a PrimaryKey referencing the first column)",
              "targetKeyAttributes": ["string (usually [])"],
              "nestedInMappedTarget": "boolean (true = created once per mapped target EObject of the sourceContainerType, i.e. one per EClass table; false = created once per source root container, i.e. one per EPackage/Schema)"
            }
          ],
          "sqlTypeMapping": { "javaTypeName": "sqlTypeName (map from Java type string to SQL type string, e.g. 'java.lang.String'->'varchar(30)', 'int'->'int')" },
          "targetLinkMappings": [
            {
              "fkType": "string (one of: INHERITANCE, ROOT, CROSS_REF, EOBJECT_TYPE_COLUMN, BIDIRECTIONAL_CROSS_REF, CONTAINMENT_SINGLE, CONTAINMENT_MULTI_BIDIRECTIONAL)",
              "rootTableName": "string or null (for ROOT: name of the root anchor table, e.g. 'EObject')",
              "eObjectTableName": "string or null (for EOBJECT_TYPE_COLUMN: name of the type-discriminator table)",
              "deleteEvent": "string (SQL referential action: 'Cascade', 'SetNull', 'NoAction')",
              "fkAnnotations": ["string (annotation tags that identify this FK, matched against target FK annotation state)"],
              "columnProperties": ["string or null (optional Column property EEnum literals, e.g. 'NotNull')"]
            }
          ],
          "targetLinkMetamodel": {
            "linkEClass": "string (EClass for FK objects, e.g. 'ForeignKey')",
            "slotEClass": "string (EClass for column/slot objects, e.g. 'Column')",
            "constraintEClass": "string (EClass for referential-action constraint objects, e.g. 'Event')",
            "linkContainerFeature": "string (feature on node EClass holding FKs, e.g. 'ownedForeignKeys')",
            "linkSourceFeature": "string (feature on FK pointing to its owning column/slot, e.g. 'column')",
            "linkTargetFeature": "string (feature on FK pointing to the referenced node, e.g. 'referencedTable')",
            "linkConstraintsFeature": "string (feature on FK holding constraint objects, e.g. 'ownedEvents')",
            "slotContainerFeature": "string (feature on node EClass holding columns/slots, e.g. 'ownedColumns')",
            "nodeContainerFeature": "string (feature on root/schema EClass holding node objects, e.g. 'ownedTables')",
            "nodeNameFeature": "string (name attribute on node EClass, e.g. 'name')",
            "slotNameFeature": "string (name attribute on slot EClass, e.g. 'name')",
            "slotTypeFeature": "string (SQL type attribute on slot EClass, e.g. 'type')",
            "defaultSlotType": "string (default SQL type for auto-created FK columns, e.g. 'int')",
            "constraintConditionFeature": "string (condition attribute on constraint EClass, e.g. 'condition')",
            "constraintActionFeature": "string (action attribute on constraint EClass, e.g. 'action')",
            "slotPropertiesFeature": "string (properties attribute on slot EClass, e.g. 'properties')"
          },
          "annotationContainerRef": "string or null (feature name for annotation container on target objects, e.g. 'ownedAnnotations'; null if target metamodel has no annotation mechanism)",
          "annotationEClass": "string or null (EClass name for annotation objects, e.g. 'Annotation'; null if not applicable)",
          "annotationTextAttr": "string or null (attribute name for the annotation tag string, e.g. 'annotation'; null if not applicable)"
        }

        Rules:
        - forwardExpression and backwardExpression must be syntactically valid Java expressions.
        - VARIABLE USAGE — this is critical and a common source of mistakes:
          * forwardExpression reads from the source object: use variable "source" (e.g. source.getFirstName()). NEVER use "target" in a forwardExpression.
          * backwardExpression reads from the target object: use variable "target" (e.g. target.getName()). NEVER use "source" in a backwardExpression.
          * Example (name concatenation): forwardExpression = "source.getFirstName() + \" \" + source.getLastName()" | backwardExpression = "target.getName().contains(\" \") ? target.getName().substring(0, target.getName().indexOf(' ')) : target.getName()"
          * A backwardExpression that starts with "source." is ALWAYS wrong.
        - If an attribute maps trivially (same name, same type), forwardExpression = "source.getX()" and backwardExpression = "target.getX()".
        - If the backward direction is mathematically impossible or ambiguous, set backwardExpression to null and add an entry to unresolvedMappings.
        - Do not invent mappings that are not described or inferable. If something is unclear, add it to unresolvedMappings.
        - sourceOwnerType: the unqualified name of the EClass in the source metamodel that declares this attribute.
        - targetOwnerType: same as above but for the target metamodel.
        - sourceRefOwnerType: the unqualified name of the EClass in the source metamodel that declares this EReference.
        - targetRefOwnerType: same as above but for the target metamodel.
        - sourceIsEOpposite: set to true if the source-side reference is the eOpposite of a containment reference in the source metamodel. EMF sets eOpposite references automatically when the corresponding containment reference is set, so they must NOT be set manually during transformation.
        - targetIsEOpposite: same as above but for the target-side reference in the target metamodel.
        - transformationOptions: if a transformation direction is non-deterministic (e.g. splitting a concatenated string back into parts), define a configuration option instead of setting backwardExpression to null. The option MUST actually be referenced in the corresponding backwardExpression (or forwardExpression) via options.name() — declaring the option without using it is an error. The type must be "String" or "boolean". The defaultValue must be a plain string (no quotes for String, no quotes for boolean). Concrete example — split strategy option: declare { "name": "nameBackwardSplitStrategy", "type": "String", "defaultValue": "first", "description": "..." }, then in firstName backwardExpression use: "target.getName().contains(\" \") ? target.getName().substring(0, \"last\".equals(options.nameBackwardSplitStrategy()) ? target.getName().lastIndexOf(' ') : target.getName().indexOf(' ')) : target.getName()" and in lastName backwardExpression use: "target.getName().contains(\" \") ? target.getName().substring((\"last\".equals(options.nameBackwardSplitStrategy()) ? target.getName().lastIndexOf(' ') : target.getName().indexOf(' ')) + 1) : \"\"".
        - When generating split expressions, NEVER use split(" ")[n] as it drops all parts after index n. Instead use indexOf/lastIndexOf + substring to preserve the full remainder. Concrete example for splitting target.getName() into firstName and lastName: firstName backward = "target.getName().contains(\" \") ? target.getName().substring(0, target.getName().indexOf(' ')) : target.getName()" | lastName backward = "target.getName().contains(\" \") ? target.getName().substring(target.getName().indexOf(' ') + 1) : \"\""
        - If a source type has no direct counterpart in the target model but its instances are distributed into target types based on their role/reference name in the parent object, use roleBasedTypeMappings. The intermediateType is the type that disappears in the transformation (e.g., Family). The sourceType is the type being distributed (e.g., FamilyMember). The roleToTargetType map assigns each role (e.g., "father", "sons") to the appropriate target type (e.g., "Male"). The roleIsMany map indicates whether each role is multi-valued (true) or single-valued (false).
        - If a type is covered by roleBasedTypeMappings as sourceType, do NOT also include it in typeMappings or attributeMappings. The roleBasedTypeMappings mechanism handles it exclusively.
        - Always include the root container type (e.g., FamilyRegister→PersonRegister) in typeMappings, even when all contained types are covered by roleBasedTypeMappings. The root container type must be present in typeMappings so that the code generator can derive rootSourceType and rootTargetType. Only the sourceType listed in a roleBasedTypeMapping entry should be excluded from typeMappings.
        - If the backward direction of a roleBasedTypeMapping requires configuration parameters because it is ambiguous or policy-dependent (e.g., whether to reuse existing intermediate objects, or which role has priority), add them to backwardConfigs. Reference the parameter name in backwardPreferExistingParam or backwardPreferSingleRoleParam of the corresponding roleBasedTypeMapping. Do NOT duplicate these parameters in transformationOptions.
        - STRING LITERALS IN EXPRESSIONS: Always use double quotes (\") for String literals in Java expressions. Single quotes (') represent a char literal in Java and cause a compile error for multi-character strings. Correct: family.getName() + \", \" + member.getName(). Wrong: family.getName() + ', ' + member.getName().
        - VARIABLE NAMES IN roleBasedTypeMapping EXPRESSIONS: In nameExpression, use 'member' for the sourceType instance and 'family' for the intermediateType instance. In backwardFamilyNameExpression and backwardMemberNameExpression, the loop variable is 'targetObj' — do NOT use 'target' or 'source'. Use contains()/indexOf()/substring() with trim() for string splitting: backwardFamilyNameExpression = "targetObj.getName().contains(\", \") ? targetObj.getName().substring(0, targetObj.getName().indexOf(\", \")) : targetObj.getName()" | backwardMemberNameExpression = "targetObj.getName().contains(\", \") ? targetObj.getName().substring(targetObj.getName().indexOf(\", \") + 2).trim() : \"\"".
        - VARIABLE NAMES IN conditionalTypeMappings EXPRESSIONS — this is critical: In a branch 'condition', use '_ctmSrc' for the source object and '_ctmParentSrc' for its container — NEVER 'source' (not in scope at the condition site). In a branch 'nameExpression', 'source' is a local alias for '_ctmSrc' and '_ctmParentSrc' is also available. In a branch 'backwardCondition', use '_ctmBwTyped' for the CTM target object being tested — NEVER 'target' (which refers to the target root object, not the CTM element). Correct examples: condition = "_ctmSrc.getUpperBound() == 1" | nameExpression = "((ecore.EClass) _ctmParentSrc).getName() + \"_\" + source.getName()" | backwardCondition = "_hasAnnotation(_ctmBwTyped, \"attribute\") && _hasAnnotation(_ctmBwTyped, \"single\")". Wrong examples: condition = "source.getUpperBound() == 1" (compile error) | backwardCondition = "target.getProperties().contains(\"single\")" (compile error: wrong type).
        - For each typeMapping and roleBasedTypeMapping, identify which attributes form a natural key for object identity (e.g. a 'name' attribute that is unique within its container). Add them as sourceKeyAttributes and targetKeyAttributes. If no natural key exists, use an empty list [] — fingerprinting will then fall back to all attributes reflectively.
        - If a direct reference between two source EClasses (e.g. Transition.trgT2P: Place[*]) corresponds not to a direct reference in the target model but to an explicit edge EClass that BOTH owns the connection AND is itself a containment child of the root (e.g. TPEdge with fromTransition and toPlace), use edgeMaterializationMappings. The sourceOwnerType is the EClass that owns the reference in the source. The edgeType is the target EClass to instantiate per connection pair. Do NOT also list edgeType in typeMappings or referenceMappings. For an eOpposite pair in the source (e.g. Transition.trgT2P ↔ Place.srcT2P), only one side should appear as a sourceOwnerType — the canonical direction (e.g. Transition.trgT2P, not Place.srcT2P).
        - Multiple source types may map to the same target type (m:1 forward mapping). When two or more typeMappings share the same targetType, every one of them MUST include a backwardCondition: a Java boolean expression (with 'target' typed as the targetType) that identifies which source type to instantiate in the backward direction. The conditions must be mutually exclusive and collectively exhaustive. Example: sourceType "Activity" and sourceType "Dependency" both map to targetType "Activity"; Activity gets backwardCondition "!target.getName().contains(\"->\")", Dependency gets "target.getName().contains(\"->\")". If a targetType is used by only one typeMapping, backwardCondition must be null.
        - If structurally equal source subtrees (same type hierarchy, same attribute values, same child structure recursively) collapse into a single shared target object in a flat list with cross-references instead of containment (i.e. a tree is transformed into a DAG by sharing equal subexpressions), use structuralDeduplicationMappings. The abstractSourceType is the abstract parent type (e.g. 'Expression'). The abstractTargetType is its target counterpart. Provide one concreteTypes entry per concrete subtype: sourceType, targetType, keyAttributes (local identity attributes, not child refs), and childRefs (containment refs to follow recursively for fingerprint computation; these become cross-refs in the target). The sourceContainerType/sourceContainerRef identifies where the source tree root is held; the targetContainerType/targetContainerRef identifies the flat list holding all shared target nodes. Set sourceContainerRefIsMany=true if the ref is multi-valued. Do NOT include the concrete subtypes in typeMappings, attributeMappings, or referenceMappings — the structuralDeduplicationMapping handles them exclusively. The sourceContainerType and targetContainerType MUST be listed in typeMappings. Do NOT include childRefs in referenceMappings. The backward direction clones the DAG tree back into a source containment tree (shared DAG nodes produce N source copies, one per occurrence).
        - If multiple source objects of the same type with the same key value collapse into a single target object that carries the count as an attribute (e.g. a bag/multiset is condensed to a set-with-multiplicity), use aggregationMappings. Do NOT also list the sourceType or targetType in typeMappings, attributeMappings, or referenceMappings. The sourceContainerType and targetContainerType MUST be listed in typeMappings so the code generator can map the parent containers. Do NOT include the sourceContainerRef in referenceMappings — the aggregation mapping handles it exclusively. For sourceKeyAttributes and targetKeyAttributes, use the grouping key attribute (e.g. ["value"]).
        - If a single source EClass must produce DIFFERENT target EClasses depending on a runtime property of each instance (e.g. EAttribute with upperBound==1 → Column, EAttribute with upperBound!=1 → Table), use conditionalTypeMappings. Do NOT also list that sourceType in typeMappings — conditionalTypeMappings is exclusive for that sourceType. Branches are evaluated in order; the first matching branch wins. Use a null condition for the final else-branch. Use null targetType to skip (produce nothing) for a branch. targetPlacementType controls where the created object is added: PARENT = into the mapped parent object's container ref; ROOT = into the target root object's container ref; REFERENCE_TARGET = follow targetPlacementRef (a feature name) on the source object, look up the result in objectMap, add to that object's container ref. The sourceContainerOwnerType and sourceContainerRef identify the feature to iterate (may be containment or cross-ref). forwardAnnotations lists tag strings to attach to the created target object (requires annotationContainerRef/EClass/TextAttr). backwardCondition identifies the branch in the backward direction (Java boolean expr on 'target'). backwardParentExpression is a Java expr returning the source-side container EObject; available variables: target (the current target object), objectMapInverse (Map<EObject,EObject>), _ctmBwSrcRes (the source Resource). If null, objectMapInverse.get(target.eContainer()) is used. The sourceContainerRef may iterate any EStructuralFeature — including cross-references like eSuperTypes.
        - If the target model requires a fixed-structure object that has no direct source counterpart and is created once per instance of a particular source EClass (e.g. a global sentinel table created once per EPackage), use syntheticObjectMappings. The object is placed in the mapped target container's targetContainerRef. Specify columns if the target type has sub-elements to create (uses reflective EEnum literal lookup for column properties). Set createPrimaryKey=true to auto-create a PrimaryKey on the first column. Synthetic objects have no objectMap entry and no backward mapping.
        - forwardAnnotations on typeMappings and conditionalTypeMappings branches: only non-empty if annotationContainerRef/EClass/TextAttr are also set. The same annotation mechanism is used throughout — set it once at top level if the target metamodel supports annotations. CRITICAL: forwardAnnotations are NOT inherited or cumulative across mapping levels. Each typeMapping or branch must carry ONLY the tag(s) that identify that specific object — never copy tags from a parent or ancestor mapping. For example, if EPackage→Schema gets tag "package", that tag applies to the Schema object ONLY and must NOT appear in any CTM branch forwardAnnotations. If EAttribute→Column gets tags ["attribute","single"], those tags apply to the Column ONLY — do not add "package" or "class" to that branch. Violating this rule causes incorrect annotation state and broken backward conditions.
        - PLACEMENT semantics for conditionalTypeMappings branches: PARENT means the object's direct container in the source model maps to an object in the target model — put the new target in that mapped object's targetContainerRef. ROOT means put the new target directly in the root target object's targetContainerRef. REFERENCE_TARGET means follow the named EStructuralFeature on the source object to get another EObject, look that EObject up in objectMap, and put the new target in that mapped object's targetContainerRef.
        - backwardParentExpression in conditionalTypeMappings branches: write a Java expression that evaluates to the source-side container EObject. For PARENT-placed objects this is typically null (default: objectMapInverse.get(target.eContainer())). For ROOT-placed objects you may need to parse the target object's name to find the owning EClass, e.g.: "(EObject) allSourceObjects(_ctmBwSrcRes).stream().filter(o -> o instanceof ${sourcePackageName}.EClass ec && ec.getName().equals(target.getName().contains(\"_\") ? target.getName().substring(0, target.getName().indexOf(\"_\")) : target.getName())).findFirst().orElse(null)". For REFERENCE_TARGET-placed objects the backward parent is typically the target's direct container looked up via objectMapInverse.
        - Respond with JSON only. No explanation, no markdown, no code fences.
        """;

    private boolean debugLog = false;

    public MappingExtractor(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    public void setDebugLog(boolean debugLog) {
        this.debugLog = debugLog;
    }

    /**
     * Returns the raw LLM response from the last extract() call, or null if not yet called.
     * Useful for debugging: contains the unmodified JSON string returned by the LLM.
     */
    public String getLastRawResponse() {
        return lastRawResponse;
    }

    /**
     * Parses a TransformationSpec directly from a cached LLM response JSON string.
     * Bypasses the LLM call — useful for regenerating code after template fixes
     * without incurring another LLM round-trip.
     *
     * @param jsonContent Raw JSON content (as saved in mapping-llm-response.json)
     * @return Parsed TransformationSpec
     * @throws RuntimeException if JSON parsing fails
     */
    public MappingModel.TransformationSpec extractFromJson(String jsonContent) {
        try {
            lastRawResponse = jsonContent;
            String cleanedResponse = cleanJsonResponse(jsonContent);
            TransformationSpecDTO dto = objectMapper.readValue(cleanedResponse, TransformationSpecDTO.class);
            String derivedClassName = capitalize(dto.sourcePackageName) + "2"
                + capitalize(dto.targetPackageName) + "Transformation";
            return buildSpec(dto, derivedClassName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse cached LLM response: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts a transformation specification from two metamodels and a description.
     *
     * @param sourceMM      Source metamodel summary
     * @param targetMM      Target metamodel summary
     * @param description   Natural language transformation description
     * @return TransformationSpec extracted by the LLM
     * @throws RuntimeException if extraction fails after retries
     */
    public MappingModel.TransformationSpec extract(
        MetamodelSummary.Summary sourceMM,
        MetamodelSummary.Summary targetMM,
        String description
    ) {
        String userMessage = buildUserMessage(sourceMM, targetMM, description);

        if (debugLog) writeDebugLog("llm-debug.log", llmClient.getProviderName(), llmClient.getModelName(), SYSTEM_PROMPT, userMessage, null);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                System.out.println("[MappingExtractor] Attempt " + attempt + "/" + MAX_RETRIES);

                String llmResponse = llmClient.complete(SYSTEM_PROMPT, userMessage);
                lastRawResponse = llmResponse;

                if (debugLog) writeDebugLog("llm-debug.log", null, null, null, null, llmResponse);

                // Clean response: remove markdown code fences if present
                String cleanedResponse = cleanJsonResponse(llmResponse);

                // Parse JSON into TransformationSpec
                TransformationSpecDTO dto = objectMapper.readValue(
                    cleanedResponse,
                    TransformationSpecDTO.class
                );

                // Convert DTO to record
                // Derive class name from package names; ignore whatever the LLM returned
                String derivedClassName = capitalize(dto.sourcePackageName) + "2"
                    + capitalize(dto.targetPackageName) + "Transformation";

                return buildSpec(dto, derivedClassName);

            } catch (Exception e) {
                System.err.println("[MappingExtractor] Attempt " + attempt + " failed: " + e.getMessage());

                if (attempt < MAX_RETRIES) {
                    // Retry with additional instruction
                    userMessage = userMessage + "\n\nYour previous response was not valid JSON. Respond with JSON only, no markdown, no explanations.";
                } else {
                    throw new RuntimeException(
                        "Failed to extract transformation specification after " + MAX_RETRIES + " attempts",
                        e
                    );
                }
            }
        }

        throw new RuntimeException("Unexpected: reached end of extract method");
    }

    /** Converts a parsed DTO to a TransformationSpec record. */
    private MappingModel.TransformationSpec buildSpec(TransformationSpecDTO dto, String derivedClassName) {
        return new MappingModel.TransformationSpec(
            dto.sourcePackageName,
            dto.targetPackageName,
            derivedClassName,
            dto.typeMappings.stream()
                .map(tm -> new MappingModel.TypeMapping(
                    tm.sourceType, tm.targetType,
                    tm.sourceKeyAttributes != null ? tm.sourceKeyAttributes : List.of(),
                    tm.targetKeyAttributes != null ? tm.targetKeyAttributes : List.of(),
                    tm.backwardCondition,
                    tm.forwardAnnotations != null ? tm.forwardAnnotations : List.of()))
                .toList(),
            dto.attributeMappings.stream()
                .map(am -> new MappingModel.AttributeMapping(
                    am.sourceOwnerType, am.sourceAttr, am.sourceAttrType,
                    am.targetOwnerType, am.targetAttr, am.targetAttrType,
                    am.forwardExpression, am.backwardExpression))
                .toList(),
            dto.referenceMappings.stream()
                .map(rm -> new MappingModel.ReferenceMapping(
                    rm.sourceRefOwnerType, rm.sourceRef, rm.sourceRefTargetType,
                    rm.targetRefOwnerType, rm.targetRef, rm.targetRefTargetType,
                    rm.sourceIsMany, rm.targetIsMany,
                    rm.sourceIsContainment, rm.targetIsContainment,
                    rm.sourceIsEOpposite, rm.targetIsEOpposite))
                .toList(),
            dto.unresolvedMappings != null ? dto.unresolvedMappings : List.of(),
            dto.transformationOptions != null
                ? dto.transformationOptions.stream()
                    .map(o -> new MappingModel.TransformationOption(
                        o.name, o.type, o.defaultValue, o.description))
                    .toList()
                : List.of(),
            dto.roleBasedTypeMappings != null
                ? dto.roleBasedTypeMappings.stream()
                    .map(r -> new MappingModel.RoleBasedTypeMapping(
                        r.sourceType, r.intermediateType,
                        r.roleToTargetType != null ? r.roleToTargetType : Map.of(),
                        r.roleIsMany != null ? r.roleIsMany : Map.of(),
                        r.targetAttr, r.nameExpression,
                        r.sourceContainerRef, r.targetContainerRef, r.targetContainerElementType,
                        r.intermediateAttr, r.sourceAttr,
                        r.backwardFamilyNameExpression, r.backwardMemberNameExpression,
                        r.backwardPreferExistingParam, r.backwardPreferSingleRoleParam,
                        r.sourceKeyAttributes != null ? r.sourceKeyAttributes : List.of(),
                        r.targetKeyAttributes != null ? r.targetKeyAttributes : List.of()))
                    .toList()
                : List.of(),
            dto.backwardConfigs != null
                ? dto.backwardConfigs.stream()
                    .map(b -> new MappingModel.BackwardConfig(
                        b.parameterName, b.parameterType, b.defaultValue, b.description))
                    .toList()
                : List.of(),
            dto.excludedAttributes != null ? dto.excludedAttributes : List.of(),
            dto.edgeMaterializationMappings != null
                ? dto.edgeMaterializationMappings.stream()
                    .map(e -> new MappingModel.EdgeMaterializationMapping(
                        e.sourceOwnerType, e.sourceRef, e.sourceRefTargetType,
                        e.edgeType, e.edgeFromRef, e.edgeToRef, e.edgeContainerRef))
                    .toList()
                : List.of(),
            dto.aggregationMappings != null
                ? dto.aggregationMappings.stream()
                    .map(a -> new MappingModel.AggregationMapping(
                        a.sourceType, a.targetType,
                        a.groupBySourceAttr, a.groupByTargetAttr, a.countTargetAttr,
                        a.sourceContainerType, a.sourceContainerRef,
                        a.targetContainerType, a.targetContainerRef,
                        a.sourceKeyAttributes != null ? a.sourceKeyAttributes : List.of(),
                        a.targetKeyAttributes != null ? a.targetKeyAttributes : List.of()))
                    .toList()
                : List.of(),
            dto.structuralDeduplicationMappings != null
                ? dto.structuralDeduplicationMappings.stream()
                    .map(s -> new MappingModel.StructuralDeduplicationMapping(
                        s.abstractSourceType, s.abstractTargetType,
                        s.concreteTypes != null
                            ? s.concreteTypes.stream()
                                .map(c -> new MappingModel.ConcreteTypeDedup(
                                    c.sourceType, c.targetType,
                                    c.keyAttributes != null ? c.keyAttributes : List.of(),
                                    c.childRefs != null ? c.childRefs : List.of()))
                                .toList()
                            : List.of(),
                        s.sourceContainerType, s.sourceContainerRef, s.sourceContainerRefIsMany,
                        s.targetContainerType, s.targetContainerRef,
                        s.sourceKeyAttributes != null ? s.sourceKeyAttributes : List.of(),
                        s.targetKeyAttributes != null ? s.targetKeyAttributes : List.of()))
                    .toList()
                : List.of(),
            dto.conditionalTypeMappings != null
                ? dto.conditionalTypeMappings.stream()
                    .map(ctm -> new MappingModel.ConditionalTypeMapping(
                        ctm.sourceType, ctm.sourceContainerOwnerType, ctm.sourceContainerRef,
                        ctm.branches != null
                            ? ctm.branches.stream()
                                .map(b -> new MappingModel.ConditionalBranch(
                                    b.condition, b.targetType,
                                    b.targetPlacementType, b.targetPlacementRef,
                                    b.targetContainerRef, b.nameExpression,
                                    b.forwardAnnotations != null ? b.forwardAnnotations : List.of(),
                                    b.backwardCondition, b.backwardParentExpression))
                                .toList()
                            : List.of(),
                        ctm.sourceKeyAttributes != null ? ctm.sourceKeyAttributes : List.of(),
                        ctm.targetKeyAttributes != null ? ctm.targetKeyAttributes : List.of()))
                    .toList()
                : List.of(),
            dto.syntheticObjectMappings != null
                ? dto.syntheticObjectMappings.stream()
                    .map(som -> new MappingModel.SyntheticObjectMapping(
                        som.sourceContainerType, som.targetType, som.targetContainerRef,
                        som.nameExpression,
                        som.forwardAnnotations != null ? som.forwardAnnotations : List.of(),
                        som.columns != null
                            ? som.columns.stream()
                                .map(c -> new MappingModel.SyntheticColumnDef(
                                    c.name, c.sqlType,
                                    c.properties != null ? c.properties : List.of()))
                                .toList()
                            : List.of(),
                        som.createPrimaryKey,
                        som.targetKeyAttributes != null ? som.targetKeyAttributes : List.of(),
                        som.nestedInMappedTarget))
                    .toList()
                : List.of(),
            dto.annotationContainerRef,
            dto.annotationEClass,
            dto.annotationTextAttr,
            dto.targetLinkMappings != null
                ? dto.targetLinkMappings.stream()
                    .map(tlm -> new MappingModel.TargetLinkMapping(
                        tlm.fkType, tlm.rootTableName, tlm.eObjectTableName,
                        tlm.deleteEvent,
                        tlm.fkAnnotations != null ? tlm.fkAnnotations : List.of(),
                        tlm.columnProperties != null ? tlm.columnProperties : List.of()))
                    .toList()
                : List.of(),
            dto.sqlTypeMapping != null ? dto.sqlTypeMapping : java.util.Map.of(),
            dto.targetLinkMetamodel != null
                ? new MappingModel.TargetLinkMetamodel(
                    dto.targetLinkMetamodel.linkEClass,
                    dto.targetLinkMetamodel.slotEClass,
                    dto.targetLinkMetamodel.constraintEClass,
                    dto.targetLinkMetamodel.linkContainerFeature,
                    dto.targetLinkMetamodel.linkSourceFeature,
                    dto.targetLinkMetamodel.linkTargetFeature,
                    dto.targetLinkMetamodel.linkConstraintsFeature,
                    dto.targetLinkMetamodel.slotContainerFeature,
                    dto.targetLinkMetamodel.nodeContainerFeature,
                    dto.targetLinkMetamodel.nodeNameFeature,
                    dto.targetLinkMetamodel.slotNameFeature,
                    dto.targetLinkMetamodel.slotTypeFeature,
                    dto.targetLinkMetamodel.defaultSlotType,
                    dto.targetLinkMetamodel.constraintConditionFeature,
                    dto.targetLinkMetamodel.constraintActionFeature,
                    dto.targetLinkMetamodel.slotPropertiesFeature)
                : null
        );
    }

    /**
     * Writes debug information to a log file.
     * Pass non-null systemPrompt+userMessage for the outgoing prompt, null for response-only entries.
     */
    private static void writeDebugLog(String filename, String provider, String model, String systemPrompt, String userMessage, String rawResponse) {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(filename);
            StringBuilder sb = new StringBuilder();
            String sep = "=".repeat(80);
            if (systemPrompt != null) {
                sb.append(sep).append("\n");
                sb.append("TIMESTAMP : ").append(java.time.LocalDateTime.now()).append("\n");
                sb.append("PROVIDER  : ").append(provider).append("  MODEL: ").append(model).append("\n");
                sb.append(sep).append("\n");
                sb.append(">>> SYSTEM PROMPT:\n").append(systemPrompt).append("\n");
                sb.append(sep).append("\n");
                sb.append(">>> USER MESSAGE:\n").append(userMessage).append("\n");
                sb.append(sep).append("\n");
            }
            if (rawResponse != null) {
                sb.append(">>> RAW LLM RESPONSE:\n").append(rawResponse).append("\n");
                sb.append(sep).append("\n\n");
            }
            java.nio.file.Files.writeString(path, sb.toString(),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
            System.out.println("[MappingExtractor] Debug log: " + path.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("[MappingExtractor] Could not write debug log: " + e.getMessage());
        }
    }

    /**
     * Builds the user message by combining metamodel summaries and description.
     */
    private String buildUserMessage(
        MetamodelSummary.Summary sourceMM,
        MetamodelSummary.Summary targetMM,
        String description
    ) {
        return """
            TRANSFORMATION DESCRIPTION (read this first — it overrides any inferences from the metamodels):
            %s

            SOURCE METAMODEL:
            %s

            TARGET METAMODEL:
            %s

            Extract the transformation specification as JSON following the description above exactly.
            """.formatted(
                description,
                sourceMM.toPromptString(),
                targetMM.toPromptString()
            );
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Cleans the LLM response by removing markdown code fences and extra whitespace.
     */
    private String cleanJsonResponse(String response) {
        // Remove markdown code fences if present
        String cleaned = response.trim();

        // Remove ```json ... ``` or ``` ... ```
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }

        return cleaned;
    }

    // ===== DTOs for Jackson JSON parsing =====

    /**
     * DTO for deserializing the JSON response from the LLM.
     * Jackson needs mutable classes with setters.
     */
    private static class TransformationSpecDTO {
        public String sourcePackageName;
        public String targetPackageName;
        public String generatedClassName;
        public List<TypeMappingDTO> typeMappings;
        public List<AttributeMappingDTO> attributeMappings;
        public List<ReferenceMappingDTO> referenceMappings;
        @JsonAlias("unresolved")
        public List<String> unresolvedMappings;
        public List<TransformationOptionDTO> transformationOptions;
        public List<RoleBasedTypeMappingDTO> roleBasedTypeMappings;
        public List<BackwardConfigDTO> backwardConfigs;
        public List<String> excludedAttributes;
        public List<EdgeMaterializationMappingDTO> edgeMaterializationMappings;
        public List<AggregationMappingDTO> aggregationMappings;
        public List<StructuralDeduplicationMappingDTO> structuralDeduplicationMappings;
        public List<ConditionalTypeMappingDTO> conditionalTypeMappings;
        public List<SyntheticObjectMappingDTO> syntheticObjectMappings;
        public String annotationContainerRef;
        public String annotationEClass;
        public String annotationTextAttr;
        public List<TargetLinkMappingDTO> targetLinkMappings;
        public java.util.Map<String,String> sqlTypeMapping;
        public TargetLinkMetamodelDTO targetLinkMetamodel;
    }

    private static class TypeMappingDTO {
        public String sourceType;
        public String targetType;
        public List<String> sourceKeyAttributes;
        public List<String> targetKeyAttributes;
        public String backwardCondition;
        public List<String> forwardAnnotations;
    }

    private static class AttributeMappingDTO {
        public String sourceOwnerType;
        public String sourceAttr;
        public String sourceAttrType;
        public String targetOwnerType;
        public String targetAttr;
        public String targetAttrType;
        public String forwardExpression;
        public String backwardExpression;
    }

    private static class TransformationOptionDTO {
        public String name;
        public String type;
        public String defaultValue;
        public String description;
    }

    private static class ReferenceMappingDTO {
        public String sourceRefOwnerType;
        public String sourceRef;
        public String sourceRefTargetType;
        public String targetRefOwnerType;
        public String targetRef;
        public String targetRefTargetType;
        public boolean sourceIsMany;
        public boolean targetIsMany;
        public boolean sourceIsContainment;
        public boolean targetIsContainment;
        public boolean sourceIsEOpposite;
        public boolean targetIsEOpposite;
    }

    private static class RoleBasedTypeMappingDTO {
        public String sourceType;
        public String intermediateType;
        public Map<String, String> roleToTargetType;
        public Map<String, Boolean> roleIsMany;
        public String targetAttr;
        public String nameExpression;
        public String sourceContainerRef;
        public String targetContainerRef;
        public String targetContainerElementType;
        public String intermediateAttr;
        public String sourceAttr;
        public String backwardFamilyNameExpression;
        public String backwardMemberNameExpression;
        public String backwardPreferExistingParam;
        public String backwardPreferSingleRoleParam;
        public List<String> sourceKeyAttributes;
        public List<String> targetKeyAttributes;
    }

    private static class BackwardConfigDTO {
        public String parameterName;
        public String parameterType;
        public String defaultValue;
        public String description;
    }

    private static class EdgeMaterializationMappingDTO {
        public String sourceOwnerType;
        public String sourceRef;
        public String sourceRefTargetType;
        public String edgeType;
        public String edgeFromRef;
        public String edgeToRef;
        public String edgeContainerRef;
    }

    private static class AggregationMappingDTO {
        public String sourceType;
        public String targetType;
        public String groupBySourceAttr;
        public String groupByTargetAttr;
        public String countTargetAttr;
        public String sourceContainerType;
        public String sourceContainerRef;
        public String targetContainerType;
        public String targetContainerRef;
        public List<String> sourceKeyAttributes;
        public List<String> targetKeyAttributes;
    }

    private static class StructuralDeduplicationMappingDTO {
        public String abstractSourceType;
        public String abstractTargetType;
        public List<ConcreteTypeDedupDTO> concreteTypes;
        public String sourceContainerType;
        public String sourceContainerRef;
        public boolean sourceContainerRefIsMany;
        public String targetContainerType;
        public String targetContainerRef;
        public List<String> sourceKeyAttributes;
        public List<String> targetKeyAttributes;
    }

    private static class ConcreteTypeDedupDTO {
        public String sourceType;
        public String targetType;
        public List<String> keyAttributes;
        public List<String> childRefs;
    }

    private static class ConditionalBranchDTO {
        public String condition;
        public String targetType;
        public String targetPlacementType;
        public String targetPlacementRef;
        public String targetContainerRef;
        public String nameExpression;
        public List<String> forwardAnnotations;
        public String backwardCondition;
        public String backwardParentExpression;
    }

    private static class ConditionalTypeMappingDTO {
        public String sourceType;
        public String sourceContainerOwnerType;
        public String sourceContainerRef;
        public List<ConditionalBranchDTO> branches;
        public List<String> sourceKeyAttributes;
        public List<String> targetKeyAttributes;
    }

    private static class SyntheticColumnDefDTO {
        public String name;
        public String sqlType;
        public List<String> properties;
    }

    private static class SyntheticObjectMappingDTO {
        public String sourceContainerType;
        public String targetType;
        public String targetContainerRef;
        public String nameExpression;
        public List<String> forwardAnnotations;
        public List<SyntheticColumnDefDTO> columns;
        public boolean createPrimaryKey;
        public List<String> targetKeyAttributes;
        public boolean nestedInMappedTarget;
    }

    private static class TargetLinkMappingDTO {
        public String fkType;
        public String rootTableName;
        public String eObjectTableName;
        public String deleteEvent;
        public List<String> fkAnnotations;
        public List<String> columnProperties;
    }

    private static class TargetLinkMetamodelDTO {
        public String linkEClass;
        public String slotEClass;
        public String constraintEClass;
        public String linkContainerFeature;
        public String linkSourceFeature;
        public String linkTargetFeature;
        public String linkConstraintsFeature;
        public String slotContainerFeature;
        public String nodeContainerFeature;
        public String nodeNameFeature;
        public String slotNameFeature;
        public String slotTypeFeature;
        public String defaultSlotType;
        public String constraintConditionFeature;
        public String constraintActionFeature;
        public String slotPropertiesFeature;
    }
}
