# Versioning and releasing Google Api Compiler
This document states how we plan to version and release the Google Api Compiler.
1. How and when to release Google Api Compiler
2. How to setup development branch for day to day commits.

## Branches
This Api Compiler repo contains only _one_ branch:

`master` : This has the latest code into which all the contribution
(pull requests) goes. From this branch we will create a temporary release branch
through which we will do the release to the central repository. After the
release the temporary release branch will be deleted.

## Steps to release a new version
1. Once every month (ideally last Monday of every month) move the code from
master branch into the release branch (create it in the same repo).

2. Now in the release branch bump up the version number in the build.gradle by
   running
   ```
   utils/update_gradle_version.sh
   ```
3. Now update the README.md to match the new version.
   ```
   utils/update_doc_version.sh
   ```
4. Now create a PR with the changes to build.gradle and README.
5. Once your PR is merged in the release branch, ensure that it builds without
errors on the [travis PR build status page](https://travis-ci.org/googleapis/api-compiler/pull_requests)

6. Now push the jars to central repository.

   Step1: On you local machine create a file ~/.gradle/gradle.properties.
   Edit the file with the following content:
   ```
   # TODO: Add link to the document on how to get these Key and password.
   signing.keyId=YOUR-KEY-ID-HERE
   signing.password=YOUR-PASSWORD-HERE
   signing.secretKeyRingFile=/usr/local/google/home/YOUR-USER-NAME/.gnupg/secring.gpg

   privateOssrhUsername=<username that has access to the central repository>
   privateOssrhPassword=<password associated with the above username>
   ```

   Step2: Push the changes to the central repository using after_success.sh.
   This script uses the information in the  ~/.gradle/gradle.properties to
   successfully deploy jars to the central repository.
   ```
   # Run the command from the release branch.
   utils/after_success.sh
   ```
7. After successful push to central repository, create a label for that release
branch and then delete the branch.

## Setup master branch for development of the next release.
1. Now in the master branch pump up the version number _twice_ in the
   build.gradle by running. This is done to change version from
   x.y.z-SNAPSHOT to x.y.z+1-SNAPSHOT.
   ```
   # Below command will removing the '-SNAPSHOT' from the existing version.
   utils/update_gradle_version.sh
   # Run the command again to increment the version and also add the '-SNAPSHOT'
   # to the version.
   utils/update_gradle_version.sh
   ```
2. Now update the README.md to match the new version.
   ```
   utils/update_doc_version.sh
   ```
3. Now create a PR with the changes to build.gradle and README.
4. Once your PR is merged in the master branch, ensure that it builds without
   errors on the [travis PR build status page](https://travis-ci.org/googleapis/api-compiler/pull_requests)
5. Now your master branch is ready to accept contributions.

