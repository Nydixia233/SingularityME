# 部署旧 Mod Jar 被运行中的客户端或服务端锁定

## 错误现象

执行一键启动或部署命令时，构建已经成功，但部署阶段删除旧 `singularityme-*.jar` 失败，Windows 报告文件正被另一个进程使用。

## 触发场景

目标 `mods` 目录属于正在运行的 Minecraft 客户端或 GTNH 测试服务端；部署脚本需要先删除旧版本 jar，再复制新构建产物。

## 根本原因

`scripts/deploy-built-mod.ps1` 的 `Deploy-JarToTarget` 会在复制新 jar 前删除目标目录中的旧 `singularityme-*.jar`。当 Java 进程仍在加载旧 mod jar 时，`Remove-Item` 无法删除该文件；`scripts/start-test-env.ps1` 的 `Invoke-DeployOnce` 收到部署脚本非零退出码后会中断后续启动流程。

## 修复方案

部署脚本捕获删除或覆盖 jar 时的 `IOException` / `UnauthorizedAccessException`，返回明确诊断：先停止使用该 `mods` 目录的 Minecraft 客户端或服务端，再重新部署。

## 预防措施

- 用 `scripts/test-deploy-built-mod.ps1` 覆盖旧 jar 被独占打开时的错误提示。
- 需要更新服务端或客户端 jar 时，先关闭对应实例，再执行 `deploy-mod.bat -Once` 或 `start-test-env.bat`。
