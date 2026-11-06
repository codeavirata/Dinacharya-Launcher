#!/bin/bash
# Draws the setup scenes on this computer and writes one PNG sheet per scene. Usage: tools/preview/run.sh [outdir] [scene]
cd "$(dirname "$0")"
SRC=../../app/src/main/java/app/dinacharya/wallpaper
B=$(mktemp -d)
javac -nowarn -d "$B" $(find shim -name '*.java') $SRC/Tr.java $SRC/FeatureScenes.java Preview.java 2>&1 | grep -v "^Picked\|^Note" 
java -Djava.awt.headless=true -cp "$B" app.dinacharya.wallpaper.Preview "$@" 2>&1 | grep -v "^Picked"
rm -rf "$B"
