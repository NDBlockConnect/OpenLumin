# OpenLumin v26.0 生态提案：3D 资产与 Demo 录制

# OpenLumin v26.0 Ecosystem Proposals: 3D Assets & Demo Recording

> 提案编号：PRO-001（BlockBuster 全家桶）、PRO-002（模型支持）、PRO-003（CS2 Demo）
> 状态：提案阶段 · Status: proposal stage
> 关联 Alpha：Alpha 4.5 · GitHub@NDBlockConnect | BlockConnect@StarsailsClover

---

## PRO-001 — BlockBuster 全家桶（自研）

### 1. 背景与目标

**BlockBuster**（历史 Modrinth/GitHub mod，`@Foghrye4` 等维护）是在 Minecraft 中制作定格动画的事实标准工具链：导演镜头、模型导入、关键帧编排、场景编辑、导出影片。

OpenLumin 的 BlockBuster 全家桶（`LuminBlockbuster` 子模块）**不 fork 原始 BlockBuster 代码**（LGPL 许可），而是利用 OpenLumin 现有渲染管线做一个**功能超集**：

- BlockBuster 导演镜头功能 → `LuminDirector`（自研）
- BlockBuster 模型加载 → `LuminModel`（自研，详见 PRO-002）
- BlockBuster 场景图/关键帧 → `LuminScene` + `LuminAnimator`（自研）
- BlockBuster 导出 → 自研导出器（`.lumiscn` + 视频编码）
- **超越 BlockBuster**：输出格式更广（`.lumiscn` = 可重放、可交互）、录制与回放引擎内嵌、LuminLang UI 集成、OpenLumin 后处理全链路（抗锯齿/光影/超分）

### 2. 核心组件设计

#### 2.1 `LuminDirector`（导演）

```java
// 场景录制/导演 API（伪代码）
public class LuminDirector {
    void beginRecording(SceneConfig config);  // 开始录制：世界状态快照
    void bindModel(String actorId, LuminModel model);  // 绑定模型到演员槽
    void bindActor(String actorId, UUID entityUUID);  // 绑定到游戏中真实实体（玩家/NPC/生物）
    void attachCamera(CameraTask task);  // 挂载镜头任务（路径/跟随/轨道/静态）
    void attachAnimation(String actorId, LuminAnimationClip clip);  // 绑定动画片段
    void markKeyframe(float timeSeconds);  // 标记关键帧（导演手动打点）
    LuminScene exportScene();  // 导出场景为可重放对象
}
```

镜头任务类型：
- `PathCameraTask`：贝塞尔路径 + 时间参数（3D 空间位置 + 旋转 + FOV）
- `FollowCameraTask`：跟随实体 + 偏移 + 平滑参数
- `OrbitCameraTask`：围绕目标轨道 + 速度
- `CutCameraTask`：瞬切（对应 BlockBuster 的 cut 镜头）

#### 2.2 `LuminScene`（场景图）

```java
public class LuminScene {
    List<LuminActor> actors;         // 演员列表
    List<LuminCameraKeyframe> camera; // 镜头关键帧序列
    List<LuminEffectTrack> effects;   // 特效轨道（粒子/后处理）
    LuminWorldState worldState;       // 世界快照（方块状态 + 时间 + 天气等）
    String lumiscnVersion;            // 格式版本

    // 序列化：.lumiscn = JSON（场景结构）+ 关联 .lumiframe（帧数据）
    void serialize(Path outputDir);
    static LuminScene deserialize(Path inputDir);
}
```

`.lumiscn` 格式：
```
movie/
├── scene.json          # 场景结构（LuminScene，可 JSON 可读）
├── actors/
│   ├── player.lumimodel  # 演员模型（OpenLumin 模型格式）
│   └── entity_xxx.lumimodel
├── animation/
│   └── walk_loop.lumiclip  # 动画片段
├── frames/             # 关键帧数据（可选：含世界快照用于离线回放）
│   ├── frame_0000.lumiframe
│   └── ...
└── audio/
    └── bgm.wav         # 音频轨道
```

#### 2.3 `LuminAnimator`（关键帧编辑器）

功能：
- 关键帧编辑：位置、旋转、缩放、可见性、材质参数
- 曲线编辑器：贝塞尔/EaseIn/EaseOut/Sine/Step（对标 BlockBuster 的 Keyframe Editor）
- 骨骼动画绑定：与 `LuminModel` 的骨骼系统深度集成（PRO-002）
- 导出器：`.lumiscn`（可重放）+ 视频编码（ffmpeg 集成，输出 MP4/AVI/WebM）

编辑器 UI：独立 GUI 窗口（JavaFX/Swing），内置 LuminLang 渲染预览面板。

#### 2.4 录制 vs 导演模式

| 模式 | 说明 |
|---|---|
| **录制模式** | 实时捕捉玩家操作：输入 → 实体位移/旋转 → 骨骼动画状态 → 摄像机 → 世界变化 → 打包为 `.lumiscn` |
| **导演模式** | 手动编排：拖拽演员到场景 → 拖拽关键帧 → 调整曲线 → 预览渲染 → 导出 |

### 3. 依赖 OpenLumin 现有能力

- `LuminRenderPipelines`：场景内实体的 GPU 渲染
- `LuminRenderTarget`：多视角渲染（导演多机位）
- `LuminModel`：实体模型抽象（PRO-002）
- `LuminAnimation`（Alpha 3 成果）：骨骼动画驱动
- `LuminLang`（Alpha 4 成果）：导演 UI

### 4. 与 BlockBuster 的差异矩阵

| 特性 | BlockBuster（原始） | OpenLumin LuminBlockbuster |
|---|---|---|
| 渲染管线 | 独立渲染 | 复用 OpenLumin 管线（Iris 光影 + 后处理）|
| 输出格式 | 视频 | 视频 + `.lumiscn`（可交互回放）|
| 场景回放 | 离线 | 在线 + 离线（世界快照回放）|
| UI | 独立编辑器 | LuminLang DSL 驱动编辑器 |
| 多人支持 | 有限 | 完整（录制输入流可 replay 到任意实体）|
| 可扩展性 | Java Mixin | OpenLumin 插件 API |

---

## PRO-002 — 3D 模型支持与 YesSteveModels 兼容

### 1. 背景

OpenLumin 当前使用 Minecraft 原生 `PlayerModel`（六面体拼装）。BlockBuster 全家桶（PRO-001）需要加载任意 3D 模型；YesSteveModels（YSM，MC 1.7.10 时代的实体模型工具）代表一类 legacy 格式需要兼容。

### 2. `LuminModel` 抽象层

```java
// 模型加载/渲染的统一抽象
public interface LuminModel {
    AABB getBounds();                      // 模型包围盒
    void setPose(LuminPose pose);          // 设置姿态（绑定骨骼动画）
    void render(LuminRenderContext ctx);   // 渲染到给定上下文
    List<LuminBone> getBones();            // 骨骼列表（支持骨骼动画）
    LuminModelData getMetadata();          // 贴图路径、动画元数据等
}
```

### 3. 支持格式

#### 3.1 Minecraft 原生（基线）
- `PlayerModel` → `VanillaPlayerModelAdapter` 实现 `LuminModel`
- 覆盖原版 Steve/Alex + 披风 + 护甲

#### 3.2 BlockBuster 系（BBM / BBE / BBAnim）

BlockBuster 模型格式（`.bbm` = 模型几何，`.bbe` = 实体定义，`.bb_anim` = 动画）：
- 解析器：`BBMparser`（几何）+ `BBAnimator`（关键帧）
- 顶点格式：位置、UV、顶点色、法线；支持多材质
- 骨骼系统：BlockBuster 专用骨骼（Hisher、Bone 继承体系）→ 映射到 `LuminBone`
- 动画：从 `.bb_anim` 提取关键帧 + 插值曲线 → `LuminAnimationClip`
- 验证：BlockBuster 开源格式（参考 `Flammarras/Blockbuster` GitHub）

#### 3.3 YesSteveModels（YSM，legacy 兼容）

YSM 使用 JSON 格式（`models/entity/*.json`）定义多部件方块堆叠实体（头部、身体、四肢独立方块）：
- 格式：JSON 部件列表 + 位置/旋转/缩放 + 贴图引用
- 兼容性层：`YSMLegacyAdapter`：将 JSON 部件映射为 `LuminBone`（每个部件 = 一个 Bone）
- 动画：YSM 无骨骼动画，仅支持静态 Pose + lerp 过渡 → 用 `LuminInterpolation` 实现平滑
- 迁移工具：`YSM2LuminConverter`：`python` 脚本将 YSM JSON 批量转换为 `LuminModel` 的 `.lumimodel` 二进制格式

#### 3.4 预留：Bedrock 几何（.geo / .anim）

Bedrock 的 `entity .json` + `.geo.json` + `.anim.json` 格式与 Java 完全不同：
- 预留后缀路径检测（`*.geo.json` / `*.anim.json`）
- 解析器骨架：`GEO_Parser`（顶点网格 + UV + 碰撞盒）+ `BedrockAnimator`
- 优先级低于 Java 格式（Java 模型优先加载）

### 4. 模型缓存与热重载

- `LuminModelCache`：LRU 缓存（`Map<String, SoftReference<LuminModel>>`）
- 资源包变更触发热重载：`ResourcePackListener` → `invalidate(key)` → 下次加载从磁盘读取
- `reload models` 命令：`/lumin reload models` → 清空缓存 + 重新扫描资源包

### 5. 玩家皮肤集成

```java
// 玩家皮肤渲染
public class LuminPlayerRenderer implements LuminModel {
    PlayerModel baseModel;         // 原版 Steve/Alex 适配
    LuminModelOverride skinModel; // 皮肤资源包覆盖
    LuminModelOverride capeModel; // 披风模型

    void loadSkin(PlayerProfile profile);  // 从 Mojang API 或本地缓存加载皮肤贴图
    void applyCustomModel(CustomModelData cmd);  // OptiFine CMT 支持
}
```

---

## PRO-003 — 游戏内录制（类 CS2 Demo）

### 1. 背景

CS2 的 `dem` 文件是完整的游戏状态录制——任意时刻回放、切换视角、自由视角（Deweloper mode）。VCR-mod / Minecraft Movie Maker 等是 MC 生态中的近似实现，但各有局限（仅录制视频、无法重放、视角固定）。

`LuminRecorder` + `LuminReplay` 提供 **CS2 风格的 Demo 录制与回放**：输入流录制 → 紧凑二进制格式 → 任意视角/时间回放 → 可交互（切换视角、速度、时间线拖动）。

### 2. 录制架构

#### 2.1 录制数据流

```
游戏Tick ──→ LuminRecorder
                ├── 输入事件捕获（鼠标/键盘/滚轮，按时间戳序列化）
                ├── 世界状态差异（WorldDelta：tick 内改变的方块/实体/实体数据）
                ├── 摄像机状态（位置、旋转、FOV、跟随目标）
                ├── 实体状态快照（位置、旋转、动画帧、骨骼 Pose，每 N tick 全量）
                └── 音频时间戳（音乐/音效触发时间）

             └── → .lumidemo（紧凑二进制，写入磁盘）
```

#### 2.2 `.lumidemo` 文件格式

```python
# 紧凑二进制格式（TLV = Type-Length-Value）
Header:
  magic: 4 bytes = b'LDMO'
  version: uint8
  gameVersion: string    # "1.21.10" 等
  startTime: int64        # Unix ms
  tickRate: uint16       # 通常 20
  worldSeed: int64
  totalTicks: uint32

Chunks（流式写入，tick-aligned）:
  EVENT_CHUNK   = input events (mouse/key/tick)
  WORLD_DELTA   = changed blocks/entities
  ENTITY_SNAP   = full entity list every N ticks (N = configurable)
  CAMERA        = camera keyframe (pos/rot/fov/follow_target)
  AUDIO_MARKER  = audio trigger timestamp
```

**压缩策略**：
- 事件流：霍夫曼编码（鼠标移动聚束，每 tick 增量）
- 世界 delta：LZ4 压缩区块变化
- 实体快照：每 10 tick 全量，中间 tick 用位置差分

**预期大小**：1 小时录制 ≈ 50–200 MB（取决于世界变化频率；CS2 `dem` 约为同长度视频的 0.1%）

#### 2.3 录制触发

- **手动**：`/lumin record start [name]` → `/lumin record stop`
- **自动**：比赛服/录制服（检测到开始事件自动触发）
- **后台录制**：最小化性能影响（录制线程异步，数据队列 batch 写入）

### 3. 回放架构

#### 3.1 `LuminReplay` 核心

```java
public class LuminReplay {
    LumiDemoFile demo;  // .lumidemo 内存映射
    LuminWorldSimulator simulator;  // 世界模拟器（重放 tick 到任意时刻）

    // 核心方法
    void seekToTick(int tick);     // 跳转到指定 tick
    void setPlaybackSpeed(float speed);  // 0.25x / 0.5x / 1x / 2x / 4x
    void setCameraMode(CameraMode mode); // FOLLOW / FREE / CUT / ORBIT

    // 摄像机模式
    enum CameraMode {
        FOLLOW_ENTITY,  // 跟随录制时的主视角玩家
        FREE_CAMERA,   // 自由相机（键盘 WASD + 鼠标）
        ORBIT,         // 绕实体轨道
        CUT_SCENE,     // 按 demo 内的摄像机关键帧播放
    }

    // 时间线 UI（由 LuminLang DSL 驱动）
    LuminReplayTimeline timeline; // 进度条 + 缩略图预览
}
```

#### 3.2 `LuminWorldSimulator`

回放的核心：不是播放视频，而是**重跑 tick**：
- 从 tick 0 开始，按世界 delta 重建 tick 状态
- 实体模拟：位置/旋转/动画 → 每 tick 渲染
- 性能：回放模式跳过物理/AI 计算（实体是记录的快照，仅做插值渲染）

#### 3.3 时间线 UI（LuminLang DSL 驱动）

```luminlang
// LuminLang 时间线 UI（示例）
TimelineUI {
    position: 0, parent.height - 48, parent.width, 48
    background: #000000 @ 0.7
    opacity: $replayActive ? 1.0 : 0.0
    transition: opacity 0.3s ease

    // 进度条
    ProgressBar {
        x: 16, y: 8, width: parent.width - 160, height: 8
        value: $currentTick / $totalTicks
        onSeek: seekToTick($value * $totalTicks)
    }

    // 当前时间标签
    Label {
        x: parent.width - 140, y: 4, text: formatTime($currentTick)
        color: #ffffff
    }

    // 控制按钮
    Button { icon: replay, onClick: togglePlay() }
    Button { icon: speedUp, onClick: cycleSpeed() }
    Button { icon: camera, onClick: cycleCamera() }
    Button { icon: export, onClick: exportVideo() }

    // 速度显示
    Label { x: parent.width - 100, y: 20, text: "{$playbackSpeed}x" }
}
```

### 4. 视频导出

`LuminReplay.exportVideo()`：
- 内部使用 LuminRecorder 的录制数据（世界状态 + 摄像机关键帧）
- 每帧调用渲染器：按当前 tick 渲染到 `LuminRenderTarget` → `ImageIO.writePNG`
- 音频：`AudioRecorder` 捕获 BUFFERED_AUDIO_LINE，混音后按时间戳与帧对齐
- 输出：MP4（H.264 via ffmpeg process）或 WebM（VP9）

### 5. 与现有工具的差异

| 特性 | VCR-mod | Movie Maker | LuminReplay |
|---|---|---|---|
| 格式 | 视频文件 | 视频文件 | `.lumidemo`（可交互）+ 视频 |
| 视角切换 | 固定 | 固定 | 任意 |
| 时间拖动 | 不支持 | 不支持 | 任意拖动 |
| 输出大小 | 大（视频） | 大（视频） | 小（二进制状态） |
| 离线回放 | 否（需游戏关闭） | 否 | 是（关闭游戏也可打开 demo）|
| 开放格式 | 否 | 否 | 是（`.lumidemo` spec 公开）|

---

## 依赖关系与开发顺序

```
Alpha 3 (LuminAnimation)
    └─→ Alpha 4 (LuminLang)
              └─→ PRO-001 LuminDirector + LuminScene
                          └─→ PRO-001 LuminAnimator
                                      └─→ PRO-003 LuminReplay + Timeline UI

PRO-002 LuminModel（独立推进）
    └─→ PRO-001 演员绑定
    └─→ PRO-003 实体状态序列化

Alpha 4.5 RHI 优化
    └─→ PRO-003 多视角录制（多 LuminRenderTarget 同时写入）
```

---

## 开放决策

1. `.lumiscn` 格式：JSON（可读）还是二进制（高效）？建议：外层 JSON + 内层二进制帧文件（兼顾可调试与高效）
2. `.lumidemo` 规范公开程度：完整 spec 公开 → 第三方工具可制作/播放；封闭 → OpenLumin 独占生态
3. 视频编码器：ffmpeg 进程集成（需 native） vs Java 内置（H.265/VP9 pure Java codec 可行性评估）
4. 多人录制：录制服务器端的 tick 流 vs 录制客户端输入（服务器录制更完整，但需要服务端 mod）

---

*GitHub@NDBlockConnect | BlockConnect@StarsailsClover*
