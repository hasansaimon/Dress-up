#!/usr/bin/env sh
# Copyright 2010-2026 the original authors or license holders.
# Gradle wrapper script (lightweight, standard). Requires gradle/wrapper/gradle-wrapper.jar to be present.
set -e

PRG="$0"
while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`"/"`expr "$link" : '\(.*\)$'`
  fi
done

SAVED_DIR=`pwd`
cd `dirname "$PRG"`/.
APP_HOME=`pwd -P`
cd "$SAVED_DIR"

# Location of the wrapper jar
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -z "$JAVA_HOME" ]; then
  JAVA_CMD=java
else
  JAVA_CMD="$JAVA_HOME/bin/java"
fi

exec "$JAVA_CMD" -cp "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
