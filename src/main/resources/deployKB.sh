#!/bin/bash

KB="/data/kb/graph.db"
PLUGIN="/ws/neo4j2owl/target/neo4j2owl-1.0.jar"
NEODIR="/Users/matentzn/Library/Application Support/Neo4j Desktop/Application/neo4jDatabases/database-70e49721-cbd8-40ab-ab39-ec4a54a2a8b2/installation-3.3.3/"


IMPORTS=${NEODIR}"imports"
DATA=${NEODIR}"data/databases/graph.db"
LOGS=${NEODIR}"logs"
PLUGINS=${NEODIR}"plugins"

rm -r "${DATA}"
rm -r "${LOGS}"/*
rm -r "${IMPORTS}"/*

#cp -r "${KB}" "${DATA}/"
cp "${PLUGIN}" "${PLUGINS}/"