#!/bin/bash
java -jar target/bxagent-1.0.0-SNAPSHOT.jar \
   -s examples/pdb/PersonsDB1.ecore \
   -t examples/pdb/PersonsDB2.ecore \
   -o generated \
   -e incrementalID \
   -d "Transform Person: combine firstName + ' ' + lastName into single name field, in backward transformation split name at a blank (depending on configuration option the first or the last blank), take first part for firstName, second part for lastName, keep age unchanged"
