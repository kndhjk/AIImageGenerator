#!/bin/sh
APP_BASE_NAME=${0##*/}
APP_HOME="$(cd "$(dirname "$0")" && pwd)"

# Use the gradle wrapper jar
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Find java
if [ -x /usr/libexec/java_home ]; then
    JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null)
else
    JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(which java)")")")
fi

exec "$JAVA_HOME/bin/java" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
