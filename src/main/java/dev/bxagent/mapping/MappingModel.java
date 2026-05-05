package dev.bxagent.mapping;

import java.util.List;
import java.util.Map;

/**
 * Central data model for EMT-Agent.
 * Contains all record types representing transformation mappings.
 */
public class MappingModel {

    /**
     * Represents the mapping of a source EClass to a target EClass.
     * sourceType and targetType are the names of the EClasses (unqualified).
     */
    public record TypeMapping(
        String sourceType,
        String targetType,
        List<String> sourceKeyAttributes,  // natural key attrs for fingerprinting (empty = use all)
        List<String> targetKeyAttributes,  // natural key attrs for fingerprinting (empty = use all)
        String backwardCondition,          // optional: Java boolean expr on 'target' to discriminate
                                           // which source type to instantiate in the backward direction.
                                           // Required when multiple TypeMappings share the same targetType.
                                           // null = no condition (1:1 mapping, default case).
        List<String> forwardAnnotations    // optional: annotation tag strings to attach to created target objects.
                                           // Requires annotationContainerRef/EClass/TextAttr set in TransformationSpec.
                                           // Empty = no annotations.
    ) {}

    /**
     * Represents the mapping of a single attribute or reference.
     * forwardExpression and backwardExpression are Java expressions,
     * where 'source' or 'target' is available as a variable.
     *
     * Example forward:  "2025 - source.getAge()"
     * Example backward: "2025 - target.getBirthYear()"
     *
     * For non-invertible mappings, backwardExpression is null.
     */
    public record AttributeMapping(
        String sourceOwnerType,  // EClass name that owns this attribute in the source metamodel
        String sourceAttr,
        String sourceAttrType,   // "EString", "EInt", etc.
        String targetOwnerType,  // EClass name that owns this attribute in the target metamodel
        String targetAttr,
        String targetAttrType,
        String forwardExpression,
        String backwardExpression  // null = not automatically invertible
    ) {}

    /**
     * Represents a reference mapping (EReference instead of EAttribute).
     * sourceIsContainment/targetIsContainment indicate composition relationships.
     */
    public record ReferenceMapping(
        String sourceRefOwnerType,  // EClass that owns this reference in the source metamodel
        String sourceRef,
        String sourceRefTargetType,
        String targetRefOwnerType,  // EClass that owns this reference in the target metamodel
        String targetRef,
        String targetRefTargetType,
        boolean sourceIsMany,
        boolean targetIsMany,
        boolean sourceIsContainment,
        boolean targetIsContainment,
        boolean sourceIsEOpposite,  // true = EMF manages this automatically, skip in Phase 2
        boolean targetIsEOpposite   // true = EMF manages this automatically, skip in Phase 2
    ) {}

    /**
     * A configuration option for non-deterministic transformation steps.
     * Referenced in attribute expressions via options.name().
     *
     * Example: nameBackwardSplitStrategy (String, default "firstSpace")
     * can be referenced as: options.nameBackwardSplitStrategy()
     */
    public record TransformationOption(
        String name,          // camelCase identifier, used as record field name
        String type,          // Java type: "String" or "boolean"
        String defaultValue,  // default value (string literal or "true"/"false")
        String description    // human-readable explanation
    ) {}

    /**
     * Maps a source type to multiple target types based on the role (reference name)
     * it plays in a parent/intermediate object. The intermediate type itself
     * has no direct counterpart in the target model.
     *
     * Example: FamilyMember in role "father"/"sons" → Male,
     *          FamilyMember in role "mother"/"daughters" → Female.
     *          Family is the intermediateType that disappears.
     */
    public record RoleBasedTypeMapping(
        String sourceType,          // type being distributed, e.g. "FamilyMember"
        String intermediateType,    // container type that disappears, e.g. "Family"
        Map<String, String> roleToTargetType,       // role name → target type, e.g. {"father": "Male"}
        Map<String, Boolean> roleIsMany,            // role name → multiplicity (true = 0..*)
        String targetAttr,          // attribute to set on created target object, e.g. "name"
        String nameExpression,      // Java expr using 'member' (sourceType) and 'family' (intermediateType)
        String sourceContainerRef,  // reference from source root to intermediateType, e.g. "families"
        String targetContainerRef,  // reference from target root for target objects, e.g. "persons"
        String targetContainerElementType, // declared element type of targetContainerRef, e.g. "Person"
        String intermediateAttr,    // attribute on intermediateType for backward, e.g. "name" on Family
        String sourceAttr,          // attribute on sourceType for backward, e.g. "name" on FamilyMember
        String backwardFamilyNameExpression, // Java expr using 'targetObj', extracts intermediate key
        String backwardMemberNameExpression, // Java expr using 'targetObj', extracts member name
        String backwardPreferExistingParam,  // nullable: BackwardConfig param controlling reuse of existing intermediate
        String backwardPreferSingleRoleParam, // nullable: BackwardConfig param controlling single-role preference
        List<String> sourceKeyAttributes,    // natural key attrs on sourceType for fingerprinting
        List<String> targetKeyAttributes     // natural key attrs on target type for fingerprinting
    ) {}

    /**
     * A configuration parameter for the backward direction of a role-based type mapping.
     * Unlike TransformationOption (which is referenced in attribute expressions),
     * BackwardConfig parameters control structural decisions (e.g., whether to reuse
     * existing intermediate objects, or which role has priority).
     *
     * These are exposed as parameters in the generated Options record.
     */
    public record BackwardConfig(
        String parameterName,  // camelCase identifier, e.g. "preferExistingFamily"
        String parameterType,  // Java type: "boolean" or "String"
        String defaultValue,   // default value, e.g. "true"
        String description     // human-readable explanation
    ) {}

    /**
     * Complete specification of a transformation, as extracted by the LLM
     * and potentially augmented through user input.
     */
    /**
     * Describes structural deduplication: many source objects with structurally equal
     * subtrees collapse into a single shared target object. Used when an expression tree
     * (AST) is transformed into a DAG where equal subexpressions are shared.
     *
     * The "key" is the recursive structural fingerprint of the whole subtree, not just
     * a single attribute. In the target, all shared objects are stored in a flat list
     * (targetContainerRef) and connected via cross-references (not containment).
     *
     * Forward: walk source tree recursively; deduplicate equal subtrees into one target
     *          object per unique structural fingerprint; collect all in targetContainerRef;
     *          set cross-references using childRefs.
     * Backward: find target root (no parents); clone DAG tree into source containment tree;
     *           shared target nodes produce N source copies (one per occurrence in tree).
     */
    public record StructuralDeduplicationMapping(
        String abstractSourceType,      // abstract supertype in source, e.g. "Expression"
        String abstractTargetType,      // abstract supertype in target, e.g. "Expression"
        List<ConcreteTypeDedup> concreteTypes,  // per-concrete-type info
        String sourceContainerType,     // EClass containing the source tree root, e.g. "Model"
        String sourceContainerRef,      // ref from sourceContainerType to tree root(s), e.g. "expr"
        boolean sourceContainerRefIsMany,  // true if sourceContainerRef is multi-valued
        String targetContainerType,     // EClass containing the flat target list, e.g. "Model"
        String targetContainerRef,      // ref from targetContainerType to flat list, e.g. "exprs"
        List<String> sourceKeyAttributes,  // for fingerprinting root container (usually [])
        List<String> targetKeyAttributes   // for fingerprinting root container (usually [])
    ) {}

    /**
     * Per-concrete-type information for a StructuralDeduplicationMapping.
     * Each concrete subtype of abstractSourceType gets one entry.
     */
    public record ConcreteTypeDedup(
        String sourceType,              // concrete source EClass, e.g. "Operator"
        String targetType,              // concrete target EClass, e.g. "Operator"
        List<String> keyAttributes,     // attributes forming the local identity key, e.g. ["op"]
        List<String> childRefs          // containment refs to follow recursively, e.g. ["left", "right"]
    ) {}

    /**
     * Maps multiple source objects of the same type to a single target object per distinct
     * key value. Used when a "bag" (multiset) in the source collapses to a distinct-element
     * set with a multiplicity attribute in the target.
     *
     * Forward: group sourceType instances by groupBySourceAttr; for each group create one
     *          targetType object with groupByTargetAttr = key and countTargetAttr = group size.
     * Backward: for each targetType object, create countTargetAttr many sourceType objects
     *           with groupBySourceAttr set to groupByTargetAttr value; leave other source
     *           attributes at their defaults (backward is informational only).
     */
    public record AggregationMapping(
        String sourceType,          // EClass being aggregated, e.g. "Element"
        String targetType,          // EClass representing one group, e.g. "Element"
        String groupBySourceAttr,   // grouping key on sourceType, e.g. "value"
        String groupByTargetAttr,   // corresponding key attr on targetType, e.g. "value"
        String countTargetAttr,     // target attr receiving the group size, e.g. "multiplicity"
        String sourceContainerType, // EClass containing sourceType objects, e.g. "MyBag"
        String sourceContainerRef,  // containment ref from sourceContainerType, e.g. "elements"
        String targetContainerType, // EClass containing targetType objects, e.g. "MyBag"
        String targetContainerRef,  // containment ref from targetContainerType, e.g. "elements"
        List<String> sourceKeyAttributes,  // natural key attrs on sourceType for fingerprinting
        List<String> targetKeyAttributes   // natural key attrs on targetType for fingerprinting
    ) {}

    /**
     * Describes how a reference between two source EClasses is materialized as an explicit
     * edge object in the target model. Used when a direct A→B reference in the source
     * becomes an A→Edge→B structure in the target (e.g., PetriNet trgT2P → TPEdge).
     *
     * Forward: for each (sourceOwnerType instance, element in sourceRef), create an edgeType
     *          object with edgeFromRef pointing to the mapped owner and edgeToRef pointing to
     *          the mapped element, then add the edge to the target root's edgeContainerRef.
     * Backward: for each edgeType instance in the target, read edgeFromRef and edgeToRef,
     *           look up their corresponding source objects, and set sourceRef accordingly.
     */
    public record EdgeMaterializationMapping(
        String sourceOwnerType,      // EClass whose reference triggers edge creation (e.g., "Transition")
        String sourceRef,            // Reference on sourceOwnerType to iterate (e.g., "trgT2P")
        String sourceRefTargetType,  // EClass at the other end of sourceRef (e.g., "Place")
        String edgeType,             // Target EClass to create per pair (e.g., "TPEdge")
        String edgeFromRef,          // Reference on edgeType → mapped sourceOwnerType (e.g., "fromTransition")
        String edgeToRef,            // Reference on edgeType → mapped sourceRefTargetType (e.g., "toPlace")
        String edgeContainerRef      // Reference on target root to hold edge objects (e.g., "elements")
    ) {}

    /**
     * A single dispatch branch within a ConditionalTypeMapping.
     * Branches are evaluated in order; the first whose condition holds wins.
     */
    public record ConditionalBranch(
        String condition,              // Java boolean expr on 'source'; null = else / unconditional
        String targetType,             // Target EClass to create; null = skip (create nothing for this source)
        String targetPlacementType,    // "PARENT" | "ROOT" | "REFERENCE_TARGET"
                                       //   PARENT           – add to the mapped parent object's container ref
                                       //   ROOT             – add to the target root object's container ref
                                       //   REFERENCE_TARGET – follow targetPlacementRef on source,
                                       //                       look up that EObject in objectMap, add there
        String targetPlacementRef,     // only for REFERENCE_TARGET: EStructuralFeature name on source, e.g. "eType"
        String targetContainerRef,     // EStructuralFeature name on the placement object to add the new target to
        String nameExpression,         // Java expr (uses 'source') to set the name; null = no setName call
        List<String> forwardAnnotations,   // annotation tag strings to attach to the created target object
        String backwardCondition,          // Java boolean expr on 'target' to identify this branch in backward dir
        String backwardParentExpression    // Java expr returning the source-side container EObject.
                                           // Available vars: target, objectMapInverse, sourceResource.
                                           // null = use objectMapInverse.get(target.eContainer()) (PARENT case)
    ) {}

    /**
     * Maps instances of one source EClass to different target EClasses depending on a
     * runtime condition on each source instance (conditional forward dispatch).
     *
     * Use when a single source type must produce different target structures depending on
     * a property of the instance (e.g. upperBound, containment flag, eOpposite presence).
     *
     * The sourceContainerOwnerType holds the sourceContainerRef feature; elements of that
     * feature that are instanceof sourceType are dispatched through the branch list.
     * sourceContainerRef may be either a containment or a cross-reference.
     *
     * Do NOT also list sourceType in typeMappings – ConditionalTypeMapping is exclusive.
     */
    public record ConditionalTypeMapping(
        String sourceType,               // EClass of the iterated elements, e.g. "EAttribute"
        String sourceContainerOwnerType, // EClass that owns the iterated feature, e.g. "EClass"
        String sourceContainerRef,       // feature name (containment OR cross-ref), e.g. "eStructuralFeatures"
        List<ConditionalBranch> branches,// ordered – first matching branch wins
        List<String> sourceKeyAttributes,// natural key attrs on sourceType for fingerprinting
        List<String> targetKeyAttributes // natural key attrs on target types for fingerprinting
    ) {}

    /**
     * Describes one column within a SyntheticObjectMapping.
     */
    public record SyntheticColumnDef(
        String name,             // column name, e.g. "id"
        String sqlType,          // SQL type string, e.g. "int"
        List<String> properties  // EEnum literal names on the Column.properties feature, e.g. ["NotNull", "AutoIncrement"]
    ) {}

    /**
     * Creates a fixed-structure target object once per instance of sourceContainerType,
     * without a corresponding source object (no objectMap entry).
     *
     * Use for sentinel / infrastructure objects that have no direct source counterpart,
     * e.g. a global "EObject" identity table created once per EPackage.
     *
     * The created object is added to the mapped source-container's target counterpart
     * via targetContainerRef.  If columns is non-empty the target object must have an
     * "ownedColumns" feature (reflective); createPrimaryKey adds a PrimaryKey referencing
     * the first column.
     */
    public record SyntheticObjectMapping(
        String sourceContainerType,      // created once per instance of this EClass, e.g. "EPackage"
        String targetType,               // target EClass to instantiate, e.g. "Table"
        String targetContainerRef,       // feature on the mapped target container to add the object to
        String nameExpression,           // Java expr (uses 'source') for the target object's name
        List<String> forwardAnnotations, // annotation tag strings to attach
        List<SyntheticColumnDef> columns,// columns to create inside the synthetic object (may be empty)
        boolean createPrimaryKey,        // if true, create a PrimaryKey referencing the first column
        List<String> targetKeyAttributes,// natural key attrs for fingerprinting (usually [])
        boolean nestedInMappedTarget     // if true: runs in Phase 1.5 (after CTM); the created object
                                         // is placed inside objectMap.get(source) via targetContainerRef;
                                         // createPrimaryKey creates PK in the container, not the new object;
                                         // columns[0] provides type/properties for the created object itself
    ) {}

    /**
     * Describes the metamodel structure of "link objects" in the target model.
     * A link object connects two endpoint objects (source endpoint + target endpoint)
     * and may carry constraint objects (e.g. delete events).
     * Covers SQL ForeignKey, UML Association, graph edges, RDF statements, etc.
     */
    public record TargetLinkMetamodel(
        String linkEClass,              // EClass name of the link object, e.g. "ForeignKey"
        String slotEClass,              // EClass name of the slot/column object, e.g. "Column"
        String constraintEClass,        // EClass name of the constraint object, e.g. "Event"; null = no constraints
        String linkContainerFeature,    // feature name on the node that owns links, e.g. "ownedForeignKeys"
        String linkSourceFeature,       // feature on the link pointing to the source endpoint, e.g. "column"
        String linkTargetFeature,       // feature on the link pointing to the target endpoint, e.g. "referencedTable"
        String linkConstraintsFeature,  // feature on the link owning constraint objects, e.g. "ownedEvents"; null = no constraints
        String slotContainerFeature,    // feature on the node owning slots/columns, e.g. "ownedColumns"
        String nodeContainerFeature,    // feature on the root owning nodes/tables, e.g. "ownedTables"
        String nodeNameFeature,         // feature on nodes for their name, e.g. "name"
        String slotNameFeature,         // feature on slots for their name, e.g. "name"
        String slotTypeFeature,         // feature on slots for their type, e.g. "type"
        String defaultSlotType,         // default slot type literal when no sqlTypeMapping entry exists, e.g. "int"
        String constraintConditionFeature, // feature on constraint for the condition, e.g. "condition"; null = no constraints
        String constraintActionFeature,    // feature on constraint for the action, e.g. "action"; null = no constraints
        String slotPropertiesFeature       // feature on slots for property literals, e.g. "properties"; null = no slot properties
    ) {}

    /**
     * Describes how to generate a link object (or a slot+link pair) in the target model.
     * Link creation happens in Phase 1.6, after all objects and id-slots are established.
     *
     * fkType values:
     *   INHERITANCE        – for each EClass with a supertype, add link id → supertype Node (Cascade)
     *   ROOT               – for each EClass with no supertype, add link id → rootTableName Node (Cascade)
     *   CROSS_REF          – for each single-valued non-containment EReference Slot, add link → ref target Node
     *   EOBJECT_TYPE_COLUMN – for each EClass, add Slot+link to eObjectTableName Node (Cascade)
     */
    public record TargetLinkMapping(
        String fkType,                  // INHERITANCE | ROOT | CROSS_REF | EOBJECT_TYPE_COLUMN
        String rootTableName,           // ROOT: fixed target node name, e.g. "EObject"
        String eObjectTableName,        // EOBJECT_TYPE_COLUMN: node to add slots to, e.g. "EObject"
        String deleteEvent,             // "Cascade" | "SetNull"
        List<String> fkAnnotations,     // annotation tags to attach to the created link
        List<String> columnProperties   // property literals to set on generated slots (e.g. "NotNull")
    ) {}

    public record TransformationSpec(
        String sourcePackageName,
        String targetPackageName,
        String generatedClassName,
        List<TypeMapping> typeMappings,
        List<AttributeMapping> attributeMappings,
        List<ReferenceMapping> referenceMappings,
        List<String> unresolvedMappings,                       // Ambiguities that still need clarification
        List<TransformationOption> transformationOptions,      // Non-deterministic configuration options
        List<RoleBasedTypeMapping> roleBasedTypeMappings,      // Role-distributed type mappings
        List<BackwardConfig> backwardConfigs,                  // Structural backward configuration params
        List<String> excludedAttributes,                       // Attribute names to exclude from mapping and fingerprinting
        List<EdgeMaterializationMapping> edgeMaterializationMappings,  // References materialized as edge objects
        List<AggregationMapping> aggregationMappings,          // Bag→set aggregation mappings (many source → one target)
        List<StructuralDeduplicationMapping> structuralDeduplicationMappings,  // Tree→DAG dedup mappings
        List<ConditionalTypeMapping> conditionalTypeMappings,  // Conditional dispatch: one source type → multiple target types
        List<SyntheticObjectMapping> syntheticObjectMappings,  // Fixed-structure objects without source counterpart
        String annotationContainerRef,  // feature name for annotation container on target objects, e.g. "ownedAnnotations"; null = no annotation support
        String annotationEClass,        // EClass name for annotation objects, e.g. "Annotation"; null = no annotation support
        String annotationTextAttr,      // attribute name for the annotation tag string, e.g. "annotation"; null = no annotation support
        List<TargetLinkMapping> targetLinkMappings,    // link patterns to generate in Phase 1.6
        java.util.Map<String,String> sqlTypeMapping,  // Java instanceClassName → SQL type, e.g. {"java.lang.String":"varchar(30)"}
        TargetLinkMetamodel targetLinkMetamodel        // metamodel structure of link objects; null = no Phase 1.6
    ) {}
}
