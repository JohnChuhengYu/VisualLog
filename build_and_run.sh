#!/bin/zsh

# 自动指定 Java 18 路径
export JAVA_HOME=$(/usr/libexec/java_home -v 18)

echo "Using JAVA_HOME: $JAVA_HOME"

# 运行 Release 任务
./gradlew runReleaseDistributable
