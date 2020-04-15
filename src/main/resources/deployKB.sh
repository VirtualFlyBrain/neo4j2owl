#!/bin/bash

KB="/Users/matentzn/ws/kb2_data/data/databases/graph.db"
PLUGIN="/Users/matentzn/ws/neo4j2owl/target/neo4j2owl-1.0.jar"
NEODIR="/Users/matentzn/Library/Application Support/Neo4j Desktop/Application/neo4jDatabases/database-f7c0d058-01bd-491e-ae1b-11c580bbe29d/installation-3.4.0/"


IMPORTS=${NEODIR}"imports"
DATA=${NEODIR}"data/databases/graph.db"
LOGS=${NEODIR}"logs"
PLUGINS=${NEODIR}"plugins"

rm -r "${DATA}"
rm -r "${LOGS}"/*
rm -r "${IMPORTS}"/*

cp -r "${KB}" "${DATA}/"
cp "${PLUGIN}" "${PLUGINS}/"