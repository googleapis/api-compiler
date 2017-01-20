#!/bin/bash

# This script updates the READMEs with the latest non-SNAPSHOT version number.
# Example: Suppose that before running this script, the build.gradle reads
# 7.8.9.  This script will replace all occurrences of #.#.# with 7.8.9 in the
# README files.

# Get the current project version.
RELEASED_VERSION=$(./gradlew properties | sed -n -e 's/^\s*version: //p')
echo "Released version is $RELEASED_VERSION"
echo "Changing version to $RELEASED_VERSION in README files"
sed -ri "s/[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?/$RELEASED_VERSION/g" README.md
git add README.md
