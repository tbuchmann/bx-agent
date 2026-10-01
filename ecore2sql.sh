#!/bin/bash
java -jar bx-agent/target/bx-agent-1.0.0-SNAPSHOT.jar \
  -s examples/ecore2sql/Ecore.ecore \
  -t examples/ecore2sql/SQL.ecore \
  -o generated \
  --from-json examples/ecore2sql/mapping-llm-response.json \
  -d "Transform Ecore class diagrams to SQL database schemas. Source package: 'ecore'. Target package: 'sql'. Key mappings: EPackage → Schema (annotated 'package'); EClass → Table (annotated 'class', plus 'abstract'/'concrete'); EAttribute with upperBound==1 → Column inside the owner class Table (annotated 'attribute','single'); EAttribute with upperBound!=1 → separate auxiliary Table named <ClassName>_<attrName> (annotated 'attribute','multi'); a synthetic sentinel Table named 'EObject' with a single id INT NOT NULL AUTO_INCREMENT PRIMARY KEY column is created once per Schema. The annotation mechanism uses ModelElement.ownedAnnotations (EClass: Annotation, attribute: annotation). Use conditionalTypeMappings for EAttribute dispatch and syntheticObjectMappings for the sentinel EObject table."
