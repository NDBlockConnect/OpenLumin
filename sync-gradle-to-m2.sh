#!/bin/bash
# 把 Gradle modules-2 缓存批量转换为 Maven 本地仓库结构
# 解决 maven.neoforged.net SSL 连接失败导致 NeoGradle detachedConfiguration 解析中止的问题
set -e

GRADLE_CACHE="$HOME/.gradle/caches/modules-2/files-2.1"
M2_REPO="$HOME/.m2/repository"

count=0
# Gradle 缓存结构: GROUP/ARTIFACT/VERSION/HASH/FILE
# Maven 结构:     GROUP(/替换.)/ARTIFACT/VERSION/FILE
find "$GRADLE_CACHE" -type f \( -name "*.jar" -o -name "*.pom" -o -name "*.module" \) | while read -r src; do
    rel="${src#$GRADLE_CACHE/}"          # GROUP/ARTIFACT/VERSION/HASH/FILE
    group=$(echo "$rel" | cut -d'/' -f1)
    artifact=$(echo "$rel" | cut -d'/' -f2)
    version=$(echo "$rel" | cut -d'/' -f3)
    file=$(echo "$rel" | cut -d'/' -f5)
    [ -z "$file" ] && continue
    groupPath=$(echo "$group" | tr '.' '/')
    dst="$M2_REPO/$groupPath/$artifact/$version"
    mkdir -p "$dst"
    if [ ! -f "$dst/$file" ]; then
        cp "$src" "$dst/$file"
    fi
done

echo "✅ Gradle 缓存已同步到 Maven 本地仓库"
