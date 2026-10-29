#!/bin/zsh

# Automatically specify Java 18 path
export JAVA_HOME=$(/usr/libexec/java_home -v 18)

echo "Using JAVA_HOME: $JAVA_HOME"

# Run Release task
./gradlew runReleaseDistributable
