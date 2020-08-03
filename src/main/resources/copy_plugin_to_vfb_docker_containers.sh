#!/bin/bash

NEO4J2OWLDIR="/Users/matentzn/vfb/neo4j2owl"
PLUGIN="$NEO4J2OWLDIR/target/neo4j2owl-1.0.jar"
KB="/Users/matentzn/vfb/docker-neo4j-knowledgebase/neo4j2owl.jar"
PROD="/Users/matentzn/vfb/vfb-prod/neo4j2owl.jar"

cd $NEO4J2OWLDIR
mvn clean compiler:compile package

cp "${PLUGIN}" "${KB}"
cp "${PLUGIN}" "${PROD}"