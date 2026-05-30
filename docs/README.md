# Singularity ME — 文档导航

> Singularity ME 是一个 Minecraft 1.7.10 / GTNH 附属模组，让玩家**不铺设任何 ME 线缆**即可使用完整的 AE2 物流功能。
> 设计原则：**加法而非修改**——不改动 AE2 现有行为，只新增一套平行设备体系。

---

## 按读者分类导航

### 🧑 新玩家 / 新协作者

| 文件 | 说明 |
|------|------|
| [快速入门](getting-started.md) | 环境搭建、构建命令、第一个调试任务、常见错误 |

### 👨‍💻 开发者

| 文件 | 说明 |
|------|------|
| [模组概览](singularity-me-overview.md) | 背景动机、核心特性、完整设备清单、设计理念 |
| [设备参考手册](device-reference.md) | 12 种设备的技术参数：类名、接口、功耗、升级卡 |
| [架构白皮书](singularity-me-architecture-whitepaper.md) | 网络模型、节点注入、生命周期、跨维度、能量流、权限 |
| [开发者指南](dev-guide.md) | 项目结构、添加新设备步骤、关键扩展点、调试技巧 |

### 🔧 维护者

| 文件 | 说明 |
|------|------|
| [兼容性配置](compat-profile.md) | AE2FC、WCT 等可选依赖的启用方式与 smoke test 清单 |
| [错误记录](errors/README.md) | 已知错误索引，避免重复踩坑 |
| [../AGENTS.md](../AGENTS.md) | AI 协作规范、代码风格、Git 提交约定 |

---

## 完整文件索引

| 文件 | 类型 | 面向 | 一句话说明 |
|------|------|------|-----------|
| `getting-started.md` | 教程 | 所有人 | 从零搭建到第一个设备运转 |
| `singularity-me-overview.md` | 概述 | 所有人 | 模组是什么、有什么、怎么设计 |
| `device-reference.md` | 参考 | 开发者 | 每个设备的技术参数手册 |
| `singularity-me-architecture-whitepaper.md` | 设计 | 开发者 | 网络模型、生命周期、注入机制的深度解析 |
| `dev-guide.md` | 指南 | 开发者 | 如何贡献代码、添加新设备 |
| `compat-profile.md` | 配置 | 维护者 | AE2 生态兼容测试方案 |
| `errors/README.md` | 运维 | 维护者 | 历史错误索引与教训 |

> **注意**：`AI记忆文档.md` 已移至 `docs/internal/`，属于 AI 工作记忆，不属于协作者文档。如需了解构建命令、入口边界等技术细节，请参阅 [快速入门](getting-started.md) 和 [开发者指南](dev-guide.md)。
