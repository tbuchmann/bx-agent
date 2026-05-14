#!/bin/bash

java -jar bx-agent/target/bx-agent-1.0.0-SNAPSHOT.jar -s examples/f2p/Families.ecore -t examples/f2p/Persons.ecore -o generatedNew -d "Transform FamilyMember: combine member.eContainer.name + \", \" + member.name into single name field of Person, in backward transformation split name at the comma. Retrieve the matching family. two configuration parameters: prefer existing family and prefer parent over child role. Depending on the role in the family: father + sons a Male object from the persons model is created, for mother + daughters a Female object is created."
