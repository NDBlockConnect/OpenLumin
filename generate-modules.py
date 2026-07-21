#!/usr/bin/env python3
"""
OpenLumin 模块生成器
自动生成所有版本的构建配置和目录结构
"""

import os
from pathlib import Path

# 版本配置矩阵
VERSIONS = {
    "neoforge-26.2": {"mc": "26.2", "loader": "26.2.0"},
    "neoforge-26.1": {"mc": "26.1", "loader": "26.1.0"},

    "neoforge-1.21.10": {"mc": "1.21.10", "loader": "21.10.0"},
    "fabric-1.21.10": {"mc": "1.21.10", "loader": "0.15.11", "api": "0.100.0+1.21"},
    "forge-1.21.10": {"mc": "1.21.10", "loader": "1.21.10-54.0.27"},

    "neoforge-1.21.4": {"mc": "1.21.4", "loader": "21.4.27-beta"},
    "fabric-1.21.4": {"mc": "1.21.4", "loader": "0.15.11", "api": "0.100.0+1.21"},
    "forge-1.21.4": {"mc": "1.21.4", "loader": "1.21.4-54.0.27"},

    "neoforge-1.21.3": {"mc": "1.21.3", "loader": "21.3.0-beta"},
    "fabric-1.21.3": {"mc": "1.21.3", "loader": "0.15.11", "api": "0.100.0+1.21"},
    "forge-1.21.3": {"mc": "1.21.3", "loader": "1.21.3-54.0.0"},

    "neoforge-1.21.1": {"mc": "1.21.1", "loader": "21.1.0"},
    "fabric-1.21.1": {"mc": "1.21.1", "loader": "0.15.11", "api": "0.100.0+1.21"},
    "forge-1.21.1": {"mc": "1.21.1", "loader": "1.21.1-52.0.0"},

    "neoforge-1.20.6": {"mc": "1.20.6", "loader": "20.6.0"},
    "fabric-1.20.6": {"mc": "1.20.6", "loader": "0.15.11", "api": "0.92.2+1.20.1"},
    "forge-1.20.6": {"mc": "1.20.6", "loader": "1.20.6-50.1.0"},

    "neoforge-1.20.5": {"mc": "1.20.5", "loader": "20.5.0"},
    "fabric-1.20.5": {"mc": "1.20.5", "loader": "0.15.11", "api": "0.92.2+1.20.1"},
    "forge-1.20.5": {"mc": "1.20.5", "loader": "1.20.5-50.0.0"},

    "neoforge-1.20.4": {"mc": "1.20.4", "loader": "20.4.237"},
    "fabric-1.20.4": {"mc": "1.20.4", "loader": "0.15.11", "api": "0.92.2+1.20.1"},
    "forge-1.20.4": {"mc": "1.20.4", "loader": "1.20.4-49.0.50"},

    "neoforge-1.20.2": {"mc": "1.20.2", "loader": "20.2.88"},
    "fabric-1.20.2": {"mc": "1.20.2", "loader": "0.15.11", "api": "0.92.2+1.20.1"},
    "forge-1.20.2": {"mc": "1.20.2", "loader": "1.20.2-48.1.0"},

    "fabric-1.20.1": {"mc": "1.20.1", "loader": "0.15.11", "api": "0.92.2+1.20.1"},
    "forge-1.20.1": {"mc": "1.20.1", "loader": "1.20.1-47.3.0"},

    "fabric-1.19.4": {"mc": "1.19.4", "loader": "0.15.11", "api": "0.76.0+1.19.2"},
    "forge-1.19.4": {"mc": "1.19.4", "loader": "1.19.4-45.2.0"},

    "fabric-1.19.3": {"mc": "1.19.3", "loader": "0.15.11", "api": "0.76.0+1.19.2"},
    "forge-1.19.3": {"mc": "1.19.3", "loader": "1.19.3-44.1.23"},

    "fabric-1.19.2": {"mc": "1.19.2", "loader": "0.15.11", "api": "0.76.0+1.19.2"},
    "forge-1.19.2": {"mc": "1.19.2", "loader": "1.19.2-43.3.13"},

    "fabric-1.18.2": {"mc": "1.18.2", "loader": "0.15.11", "api": "0.73.0+1.18.2"},
    "forge-1.18.2": {"mc": "1.18.2", "loader": "1.18.2-40.2.0"},

    "fabric-1.17.1": {"mc": "1.17.1", "loader": "0.15.11", "api": "0.42.0+1.17"},
    "forge-1.17.1": {"mc": "1.17.1", "loader": "1.17.1-37.1.1"},

    "fabric-1.16.5": {"mc": "1.16.5", "loader": "0.15.11", "api": "0.42.0+1.16"},
    "forge-1.16.5": {"mc": "1.16.5", "loader": "1.16.5-36.2.39"},

    "fabric-1.15.2": {"mc": "1.15.2", "loader": "0.15.11", "api": "0.28.5+1.15"},
    "forge-1.15.2": {"mc": "1.15.2", "loader": "1.15.2-31.2.57"},

    "fabric-1.14.4": {"mc": "1.14.4", "loader": "0.15.11", "api": "0.28.5+1.14"},
    "forge-1.14.4": {"mc": "1.14.4", "loader": "1.14.4-28.2.26"},

    "fabric-1.13.2": {"mc": "1.13.2", "loader": "0.15.11", "api": "0.28.5+1.13"},
    "forge-1.13.2": {"mc": "1.13.2", "loader": "1.13.2-25.0.223"},
}

def generate_neoforge_gradle(module: str, config: dict) -> str:
    return f"""plugins {{
    id("net.neoforged.gradle.userdev") version "7.0.163"
}}

dependencies {{
    implementation(project(":common"))

    // NeoForge & Minecraft
    implementation("net.neoforged:neoforge:{config['loader']}")

    // Annotations
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.1")
}}

tasks {{
    jar {{
        from(project(":common").sourceSets.main.get().output)
        archiveBaseName.set("OpenLumin-{module}")
    }}

    processResources {{
        from(project(":common").sourceSets.main.get().resources)
    }}
}}
"""

def generate_fabric_gradle(module: str, config: dict) -> str:
    return f"""plugins {{
    id("fabric-loom") version "1.7-SNAPSHOT"
}}

dependencies {{
    implementation(project(":common"))

    // Fabric
    minecraft("com.mojang:minecraft:{config['mc']}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:{config['loader']}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:{config['api']}")

    // Annotations
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.1")
}}

tasks {{
    jar {{
        from(project(":common").sourceSets.main.get().output)
        archiveBaseName.set("OpenLumin-{module}")
    }}

    processResources {{
        from(project(":common").sourceSets.main.get().resources)

        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {{
            expand("version" to project.version)
        }}
    }}
}}
"""

def generate_forge_gradle(module: str, config: dict) -> str:
    return f"""import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {{
    id("net.minecraftforge.gradle") version "6.0.24"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}}

dependencies {{
    implementation(project(":common"))

    // Forge & Minecraft
    minecraft("net.minecraftforge:forge:{config['loader']}")

    // Annotations
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.1")
}}

tasks {{
    named<ShadowJar>("shadowJar") {{
        from(project(":common").sourceSets.main.get().output)
        archiveBaseName.set("OpenLumin-{module}")
        configurations = listOf(project.configurations.runtimeClasspath.get())
    }}

    processResources {{
        from(project(":common").sourceSets.main.get().resources)

        inputs.property("version", project.version)

        filesMatching("META-INF/mods.toml") {{
            expand("version" to project.version)
        }}
    }}
}}
"""

def generate_fabric_mod_json(config: dict) -> str:
    return f"""{{
  "schemaVersion": 1,
  "id": "openlumin",
  "version": "${{version}}",
  "name": "OpenLumin",
  "description": "High-performance 2D/3D rendering library for Minecraft",
  "authors": [
    "NDBlockConnect Team"
  ],
  "contact": {{
    "homepage": "https://github.com/NDBlockConnect/OpenLumin",
    "sources": "https://github.com/NDBlockConnect/OpenLumin"
  }},
  "license": "GPL-3.0-only",
  "environment": "*",
  "entrypoints": {{
  }},
  "depends": {{
    "fabricloader": ">=0.15.0",
    "minecraft": "{config['mc']}"
  }}
}}
"""

def generate_neoforge_toml(config: dict) -> str:
    mc_version = config['mc']
    mc_next = f"{mc_version.rsplit('.', 1)[0]}.{int(mc_version.rsplit('.', 1)[1]) + 1}"

    return f"""modLoader = "neoforge"
loaderVersion = "[4,)"
license = "GPL-3.0-only"

[[mods]]
modId = "openlumin"
version = "${{version}}"
displayName = "OpenLumin"
description = "High-performance 2D/3D rendering library for Minecraft"
authors = "NDBlockConnect Team"

[[dependencies.openlumin]]
    modId = "neoforge"
    type = "required"
    versionRange = "[{config['loader'].split('-')[0]},)"
    ordering = "NONE"
    side = "BOTH"

[[dependencies.openlumin]]
    modId = "minecraft"
    type = "required"
    versionRange = "[{mc_version},{mc_next})"
    ordering = "NONE"
    side = "BOTH"
"""

def generate_forge_toml(config: dict) -> str:
    mc_version = config['mc']
    mc_next = f"{mc_version.rsplit('.', 1)[0]}.{int(mc_version.rsplit('.', 1)[1]) + 1}"
    forge_major = config['loader'].split('-')[1].split('.')[0]

    return f"""modLoader = "javafml"
loaderVersion = "[{forge_major},)"
license = "GPL-3.0-only"

[[mods]]
modId = "openlumin"
version = "${{version}}"
displayName = "OpenLumin"
description = "High-performance 2D/3D rendering library for Minecraft"
authors = "NDBlockConnect Team"

[[dependencies.openlumin]]
    modId = "forge"
    type = "required"
    versionRange = "[{forge_major},)"
    ordering = "NONE"
    side = "BOTH"

[[dependencies.openlumin]]
    modId = "minecraft"
    type = "required"
    versionRange = "[{mc_version},{mc_next})"
    ordering = "NONE"
    side = "BOTH"
"""

def create_module(module: str, config: dict):
    """创建单个模块的目录结构和构建文件"""
    loader_type = module.split('-')[0]

    # 创建目录
    module_path = Path(module)
    (module_path / "src" / "main" / "java").mkdir(parents=True, exist_ok=True)
    (module_path / "src" / "main" / "resources").mkdir(parents=True, exist_ok=True)

    # 生成 build.gradle.kts
    if loader_type == "neoforge":
        gradle_content = generate_neoforge_gradle(module, config)
    elif loader_type == "fabric":
        gradle_content = generate_fabric_gradle(module, config)
    elif loader_type == "forge":
        gradle_content = generate_forge_gradle(module, config)
    else:
        return

    (module_path / "build.gradle.kts").write_text(gradle_content)

    # 生成模组元数据文件
    if loader_type == "fabric":
        (module_path / "src" / "main" / "resources" / "fabric.mod.json").write_text(
            generate_fabric_mod_json(config)
        )
    elif loader_type == "neoforge":
        meta_dir = module_path / "src" / "main" / "resources" / "META-INF"
        meta_dir.mkdir(parents=True, exist_ok=True)
        (meta_dir / "neoforge.mods.toml").write_text(generate_neoforge_toml(config))
    elif loader_type == "forge":
        meta_dir = module_path / "src" / "main" / "resources" / "META-INF"
        meta_dir.mkdir(parents=True, exist_ok=True)
        (meta_dir / "mods.toml").write_text(generate_forge_toml(config))

    # 复制源码 (仅创建符号链接或引用说明)
    readme = f"""# {module}

本模块的源码从以下位置引用：
- Java 代码: ../src/main/java (通过 build.gradle.kts 的 from() 引入)
- 资源文件: ../common/src/main/resources (着色器等)

实际开发时，如需版本特定的适配代码，在 src/main/java 中添加。
"""
    (module_path / "README.md").write_text(readme)

    print(f"[OK] 创建模块: {module}")

def main():
    print("OpenLumin 模块生成器")
    print("=" * 50)
    print(f"总计模块数: {len(VERSIONS)}")
    print()

    for module, config in VERSIONS.items():
        create_module(module, config)

    print()
    print("=" * 50)
    print(f"[OK] 完成！已创建 {len(VERSIONS)} 个模块")
    print()
    print("下一步:")
    print("  1. 运行 ./gradlew build 验证所有模块")
    print("  2. 或使用 ./build-phase1.sh 分阶段构建")

if __name__ == "__main__":
    main()
