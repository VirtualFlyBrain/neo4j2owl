#!/bin/bash

set -e

KB="/Users/matentzn/ws/kb2_data/data/databases/graph.db"
NEODIR="/Users/matentzn/Library/Application Support/com.Neo4j.Relate/Data/dbmss/dbms-b824802f-99fc-48bc-af0d-34b011b9b7f7/"

NEO4J2OWLDIR="/Users/matentzn/vfb/neo4j2owl"
PLUGIN="$NEO4J2OWLDIR/target/neo4j2owl-1.0.jar"


cd $NEO4J2OWLDIR
#mvn clean compiler:compile package

IMPORTS=${NEODIR}"imports"
DATA=${NEODIR}"data/databases/graph.db"
LOGS=${NEODIR}"logs"
PLUGINS=${NEODIR}"plugins"

rm -rf "${DATA}"
rm -rf "${LOGS}"/*
#rm -rf "${IMPORTS}"/*

#cp -r "${KB}" "${DATA}/"
cp "${PLUGIN}" "${PLUGINS}/"