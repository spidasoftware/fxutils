# fxutils
Extensions to OpenJDK and OpenJFX classes

<!-- toc -->
- [Testing](#testing)
- [Building](#building)
    + [Tasks](#tasks)
    + [Environment Variables](#environment-variables)
<!-- tocstop -->

Testing
-------

Building
-------

#### Tasks

    ./gradlew clean

Resets the contents of the build directory.

    ./gradlew build

Creates a jar and runs all tests.

    ./gradlew test

Builds and runs all tests.

    ./gradlew install

Builds and installs a new jar in the local Maven repository.

    ./gradlew uploadArchives

Builds and uploads a new jar to the remote Maven repository. Requires environment variables be set. Check version information in build.gradle before running.


#### Environment Variables
