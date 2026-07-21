#!/usr/bin/env python3
"""
OpenLumin 代码依赖分析工具

分析所有源文件，识别：
1. 版本无关代码（可提取到 common/）
2. 需要抽象的代码（需创建接口）
3. 版本特定代码（保留在模块内）
"""

import os
import re
from pathlib import Path
from collections import defaultdict

# Minecraft 特定 API 模式
MC_PATTERNS = [
    r'net\.minecraft\.',
    r'com\.mojang\.blaze3d\.',
]

# 需要抽象的 API（1.21.4 特有）
VERSION_SPECIFIC = [
    'WindowRenderState',
    'GameRenderState',
    'RenderPipelines',
    'ProjectionMatrixBuffer',
    'DynamicUniformStorage',
]

# 版本无关的安全 API
SAFE_APIS = [
    'org.joml',
    'org.lwjgl',
    'javax.annotation',
    'java.lang',
    'java.util',
    'java.nio',
]

class CodeAnalyzer:
    def __init__(self, src_dir):
        self.src_dir = Path(src_dir)
        self.results = {
            'pure': [],           # 纯净代码（可直接提取到 common）
            'abstractable': [],   # 需要抽象
            'version_specific': [], # 版本特定
        }

    def analyze_file(self, file_path):
        """分析单个文件"""
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        # 提取所有 import 语句
        imports = re.findall(r'import\s+([\w.]+);', content)

        mc_imports = []
        safe_imports = []
        version_specific_usage = []

        for imp in imports:
            # 检查是否是 Minecraft/Blaze3D API
            if any(re.match(pattern, imp) for pattern in MC_PATTERNS):
                mc_imports.append(imp)

                # 检查是否使用版本特定 API
                if any(api in imp for api in VERSION_SPECIFIC):
                    version_specific_usage.append(imp)

            # 检查是否是安全 API
            elif any(imp.startswith(api) for api in SAFE_APIS):
                safe_imports.append(imp)

        # 分类
        rel_path = file_path.relative_to(self.src_dir)

        if version_specific_usage:
            self.results['version_specific'].append({
                'file': str(rel_path),
                'reason': f"使用 {len(version_specific_usage)} 个版本特定 API",
                'apis': version_specific_usage
            })
        elif mc_imports:
            self.results['abstractable'].append({
                'file': str(rel_path),
                'reason': f"使用 {len(mc_imports)} 个 Minecraft API",
                'apis': mc_imports
            })
        else:
            self.results['pure'].append({
                'file': str(rel_path),
                'reason': "无 Minecraft 依赖"
            })

    def analyze_all(self):
        """分析所有 Java 文件"""
        java_files = self.src_dir.rglob('*.java')

        for file_path in java_files:
            try:
                self.analyze_file(file_path)
            except Exception as e:
                print(f"错误: {file_path}: {e}")

    def print_report(self):
        """打印分析报告"""
        print("=" * 80)
        print("OpenLumin Code Dependency Analysis Report")
        print("=" * 80)

        print(f"\n[PURE] Code without MC deps (can extract to common/core/): {len(self.results['pure'])} files")
        print("-" * 80)
        for item in self.results['pure']:
            print(f"  * {item['file']}")
            print(f"    Reason: {item['reason']}")

        print(f"\n[ABSTRACT] Needs abstraction (create interface in common/api/): {len(self.results['abstractable'])} files")
        print("-" * 80)
        for item in self.results['abstractable']:
            print(f"  * {item['file']}")
            print(f"    Reason: {item['reason']}")
            print(f"    APIs: {', '.join(item['apis'][:3])}{'...' if len(item['apis']) > 3 else ''}")

        print(f"\n[SPECIFIC] Version-specific (keep in version modules): {len(self.results['version_specific'])} files")
        print("-" * 80)
        for item in self.results['version_specific']:
            print(f"  * {item['file']}")
            print(f"    Reason: {item['reason']}")
            print(f"    Specific APIs: {', '.join(item['apis'])}")

        print("\n" + "=" * 80)
        print(f"Total: {len(self.results['pure']) + len(self.results['abstractable']) + len(self.results['version_specific'])} files")
        print("=" * 80)

        # 统计建议
        print("\nMigration Plan:")
        print(f"  1. Migrate to common/core/ immediately: {len(self.results['pure'])} files")
        print(f"  2. Design abstraction first: {len(self.results['abstractable'])} files")
        print(f"  3. Implement per version: {len(self.results['version_specific'])} files")

        # 估算工作量
        pure_hours = len(self.results['pure']) * 0.1  # 10分钟/文件
        abstract_hours = len(self.results['abstractable']) * 0.5  # 30分钟/文件
        specific_hours = len(self.results['version_specific']) * 1.0  # 1小时/文件

        total_hours = pure_hours + abstract_hours + specific_hours

        print(f"\nEstimated Work:")
        print(f"  * Pure code migration: {pure_hours:.1f} hours")
        print(f"  * Abstraction design: {abstract_hours:.1f} hours")
        print(f"  * Version-specific impl: {specific_hours:.1f} hours")
        print(f"  * Total: {total_hours:.1f} hours")

if __name__ == '__main__':
    src_dir = Path(__file__).parent / 'src' / 'main' / 'java' / 'io' / 'github' / 'openlumin'

    analyzer = CodeAnalyzer(src_dir)
    analyzer.analyze_all()
    analyzer.print_report()
