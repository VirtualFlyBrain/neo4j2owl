#!/bin/bash

PLUGIN="/ws/neo4j2owl/target/neo4j2owl-1.0.jar"
NEODIR="/Users/matentzn/Library/Application Support/Neo4j Desktop/Application/neo4jDatabases/database-cbde95fd-fdd9-43b0-a4bb-764de88b716b/installation-3.3.3/"


IMPORTS=${NEODIR}"imports"
DATA=${NEODIR}"data/databases/graph.db"
LOGS=${NEODIR}"logs"
PLUGINS=${NEODIR}"plugins"

rm -r "${DATA}"
rm -r "${LOGS}"/*
#rm -r "${IMPORTS}"/*

cp "${PLUGIN}" "${PLUGINS}/"