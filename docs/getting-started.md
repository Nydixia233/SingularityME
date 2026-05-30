# Singularity ME — 快速入门

**目标读者**：第一次接触本项目的新协作者
**预计时间**：首次搭建约 15 分钟

---

## 一、环境要求

| 需求 | 说明 |
|------|------|
| **JDK** | 17 或更高（`enableModernJavaSyntax = jabel` 需要 JDK 17 编译 Java 8 字节码） |
| **Minecraft** | 1.7.10 + Forge 10.13.4.1614 |
| **GTNH 整合包** | 推荐 `GT-New-Horizons 2.9.0+` |
| **启动器** | PrismLauncher（开发测试用） |
| **OS** | Windows（构建脚本为 PowerShell） |
| **Gradle** | 通过 `gradlew` 自动下载，无需手动安装 |

### Gradle 缓存目录

为避免 Gradle 缓存写入用户目录占用 C 盘空间，**执行所有 gradle 命令前必须先设置环境变量**：

```powershell
$env:GRADLE_USER_HOME = "$env:USERPROFILE\.gradle"
```

---

## 二、克隆与首次构建

```powershell
# 1. 克隆仓库
git clone <repo-url> SingularityME
cd SingularityME

# 2. 首次编译（跳过代码格式化检查，节省时间）
$env:GRADLE_USER_HOME = "$env:USERPROFILE\.gradle"
.\gradlew.bat compileJava compileMixinJava -x spotlessJavaCheck

# 3. 完整构建
.\gradlew.bat build -x spotlessJavaCheck
```

构建产物位于 `build/libs/singularityme-*.jar`。

> **注意**：首次运行 Gradle 会下载约 500MB 的依赖（Minecraft、Forge、GTNH 各模组），请保持网络通畅。

---

## 三、本地测试

### 方式一：通过 runClient 运行（推荐调试）

```powershell
$env:GRADLE_USER_HOME = "$env:USERPROFILE\.gradle"
.\gradlew.bat runClient
```

这会启动一个带有本模组的 Minecraft 开发环境。

### 方式二：部署到 PrismLauncher 实例

```powershell
# 先构建（确保 jar 存在）
.\gradlew.bat build -x spotlessJavaCheck

# 一键部署（监控模式，每次 jar 更新自动部署）
.\deploy-mod.bat

# 或只部署一次
.\deploy-mod.bat -Once
```

默认部署目标由 `scripts/deploy-built-mod.ps1` 中的 `$ModsDir` 参数决定，可通过命令行传入：

如需修改目标目录，在 `scripts/deploy-built-mod.ps1` 中调整 `$ModsDir` 参数或直接在命令行传入：

```powershell
.\deploy-mod.bat -Once -ModsDir "你的实例mods目录路径"
```

---

## 四、第一个调试任务：验证网格激活

1. 启动 Minecraft 并进入一个存档世界。
2. 在创造模式或 JEI/NEI 中查找 `SingularityProbe`（奇点探针）并放置。
3. 右键打开探针界面——如果显示网格状态正常，说明核心代码正常工作。
4. 放置一个 `SingularityPowerCore` 并连接 GT EU 电源，观察能量条变化。
5. 放置一个 `SingularityDrive` 并插入存储元件，在 `SingularityTerminal` 中查看物品。

**预期结果**：终端中能看到 Drive 中存储元件的内容，且无需铺设任何 ME 线缆。

---

## 五、常见构建错误

### 1. `spotlessJavaCheck` 失败
```
> Task :spotlessJavaCheck FAILED
```
**解决**：手动执行格式化后再构建：
```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat build -x spotlessJavaCheck
```

### 2. Qz UILib 依赖解析失败
```
Could not find club.heiqi.uilib:Qz-UILib-4.1.3-LTS
```
**原因**：Qz UILib 通过 `mavenLocal()` 解析，需要先将 JAR 安装到本地 Maven 仓库。
**解决**：
1. 确保 `Qz-UILib-4.1.3-LTS` 源码在本地并已构建
2. 或从 GTNH 实例的 `mods/` 目录复制 `qz_uilib-4.1.3-LTS.jar`，手动安装到本地 Maven：
```powershell
mvn install:install-file -Dfile=qz_uilib-4.1.3-LTS.jar `
  -DgroupId=club.heiqi.uilib -DartifactId=Qz-UILib-4.1.3-LTS `
  -Dversion=NO-GIT-TAG-SET -Dpackaging=jar
```

### 3. `Unsupported class file major version XX`
**原因**：JDK 版本过低。GTNH 约定使用 Jabel 转译，但编译过程需要 JDK 17+。
**解决**：安装 JDK 17 或更高版本，确保 `JAVA_HOME` 指向正确的 JDK。

### 4. Mixin 编译失败
```
> Task :compileMixinJava FAILED
```
**原因**：Mixin 源集需要引用 AE2 内部类。确保 `dependencies.gradle` 中 AE2 版本正确。
**解决**：检查 `dependencies.gradle` 中的 AE2 坐标应与 GTNH 实例版本一致（当前 `rv3-beta-944-GTNH`）。

### 5. `runClient` 黑屏或崩溃
**原因**：LWJGL3ify 或其他 GTNH 核心模组版本冲突。
**解决**：确保 `run/` 目录干净（删除后重新 `runClient`），或在 PrismLauncher 实例中测试完整整合包环境。

---

## 六、下一步

- 阅读 [模组概览](singularity-me-overview.md) 了解整体设计
- 阅读 [设备参考手册](device-reference.md) 了解每个设备的详细参数
- 阅读 [架构白皮书](singularity-me-architecture-whitepaper.md) 深入理解网络模型
- 阅读 [开发者指南](dev-guide.md) 学习如何添加新设备

---

## 七、有用链接

- [GTNH 官方 Wiki](https://gtnh.miraheze.org/)
- [AE2 非官方 GTNH 版文档](https://github.com/GTNewHorizons/Applied-Energistics-2)
- [Qz UILib 文档](../../Qz-UILib-4.1.3-LTS/)（同工作区）
