#!/bin/sh

# Gradle start up script for POSIX generated for Penumbra (Gradle 9.6.1 wrapper).

APP_BASE_NAME=${0##*/}
APP_HOME=$( cd "${0%/*}" >/dev/null 2>&1 ; pwd -P ) || exit

DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"
