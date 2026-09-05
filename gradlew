#!/usr/bin/env sh
APP_BASE_NAME=$(basename "$0")
DIRNAME=$(dirname "$0")

CLASSPATH=$DIRNAME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" "-classpath" "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"