#!/bin/sh
# Bootstrap launcher. CI replaces this with the official Gradle 8.11.1 launcher.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ ! -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
    echo 'Gradle Wrapper JAR 尚未生成。请运行 GitHub Actions Android CI，下载 gradle-wrapper 产物；云端成功构建后会自动提交官方 Wrapper。' >&2
    exit 1
fi
if [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    JAVA_CMD=java
fi
exec "$JAVA_CMD" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
