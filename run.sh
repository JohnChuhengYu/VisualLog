#!/bin/bash
export JAVA_HOME=$(/usr/libexec/java_home -v 18 2>/dev/null || /usr/libexec/java_home)
./gradlew run
