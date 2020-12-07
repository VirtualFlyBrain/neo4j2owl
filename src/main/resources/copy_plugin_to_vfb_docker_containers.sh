#!/bin/bash

set -e

NEO4J2OWLDIR="/Users/matentzn/vfb/neo4j2owl"
PLUGIN="$NEO4J2OWLDIR/target/neo4j2owl-1.0.jar"
PLUGIN_OWL2CSV="$NEO4J2OWLDIR/target/owl2neo4jcsv.jar"
KB="/Users/matentzn/vfb/docker-neo4j-knowledgebase/neo4j2owl.jar"
PROD="/Users/matentzn/vfb/vfb-prod/neo4j2owl.jar"
DUMPS="/Users/matentzn/vfb/vfb-pipeline-dumps/scripts/owl2neo4jcsv.jar"

cd $NEO4J2OWLDIR || exit
mvn clean compiler:compile package

cp "${PLUGIN}" "${KB}"
cp "${PLUGIN}" "${PROD}"
cp "${PLUGIN_OWL2CSV}" "${DUMPS}"