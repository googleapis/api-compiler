#!/bin/bash

# This script updates the build.gradle files to the next version number.
# This script is meant to be run manually (not by Travis)

# Argument (optional): new version number for build.gradle files
# Providing no argument defaults to incrementing revision number to
# x.y.z+1-SNAPSHOT if the current version is x.y.z OR to x.y.z if the
# current version is x.y.z-SNAPSHOT.

# Get the current project version.
CURRENT_VERSION=$(./gradlew properties | sed -n -e 's/^\s*version: //p')
echo "current version : $CURRENT_VERSION"

if [ $# -eq 1 ]; then
    NEW_VERSION=$1
elif [ "${CURRENT_VERSION##*-}" != "SNAPSHOT" ]; then
    NEW_VERSION="${CURRENT_VERSION%.*}.$((${CURRENT_VERSION##*.}+1))-SNAPSHOT"
else
    NEW_VERSION=${CURRENT_VERSION%%-*}
fi

echo "Changing version from $CURRENT_VERSION to $NEW_VERSION in build.gradle"
sed -i "s/version = \"$CURRENT_VERSION\"/version = \"$NEW_VERSION\"/" \
build.gradle
git add build.gradle

