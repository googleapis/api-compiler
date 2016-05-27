#!/bin/bash

# This script is meant to be used by Travis-CI to publish artifacts (binary, source and javadoc jars) when releasing snapshots.
# Right now it will be manually triggered to push jars to nexus server.

SITE_VERSION=$(./gradlew properties | sed -n -e 's/^\s*version: //p')
NEXUS_SNAPSHOTS_DIR=$(cat ./build.gradle | sed -ne 's/def\s+privateSnapshotRepo\s*=\s*"\(.*\)".*/\1/p')
if [ "$SITE_VERSION" == "" ]; then
    echo "Could not determine the version, so we're exiting."
    exit 1
fi
if [ "${SITE_VERSION##*-}" != "SNAPSHOT" ]; then
    URL=${NEXUS_SNAPSHOTS_DIR}io/gapi/gapi-tools-framework/$SITE_VERSION/
    if curl --output /dev/null --silent --head --fail "$URL"; then
        echo "Not deploying artifacts because it seems like they already exist."
        echo "Existence was checked using the url $URL"
    else
        ./gradlew uploadArchives
        echo "Deployed successfully to $URL"
    fi
else
    ./gradlew uploadArchives
    echo "Deployed successfully to snapshot"
fi

