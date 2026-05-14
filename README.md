# AI Image Generator

Android app for CS702 — Build & Fortify assignment. Generates AI images from text prompts using the `ai.elliottwen.info` API.

---

## 📦 当前版本 v1.1.0

**APK 大小**：约 6 MB（含 3 架构 Native .so 库）

**最后更新**：2026-05-15

**签名**：debug keystore（提交用），`minifyEnabled = false`（release）

**MD5**：`c16a4aafd756ed04b8de94f6a3a9a3fb`

**下载地址**：发布后见 GitHub Releases

---

## 🛠️ 开发日志

### 2026-05-15 — 本地-only key 重构

- 去掉旧的 Java 侧翻转 key 方案
- 去掉旧的预编译 `jniLibs/*.so`，改为 Gradle + CMake 现场构建 JNI
- API key 改成 **仅存于 C 层**
- 新增自定义“类摩斯码”编码（`di / da / tu / ka`）
- 8 组分片乱序存储 + 位置相关 rolling mask + 位旋转还原
- Native checksum 校验：只接受唯一目标 key，其他值直接拒绝
- GitHub Actions 已补上 NDK + CMake 安装

### 2026-05-03 — Part 2 完成 + 生图最终修复

**问题 11：getApiKey() 始终返回空，图片生成失败**

排查：
- `apiKey length=0` — getApiKey() 内部的 VALIDATE_CHAR 检查失败
- 根因 1：`_rev` 存的是原始 key 直接翻转（无 XOR），但之前验证逻辑期望 88 字符（旧版 key 长度）
- 根因 2：`StringBuilder.reverse().toString()` 经 JNI `GetStringUTFChars` 传递时字符编码异常，导致长度始终多出一倍
- 根因 3：native `verifyNative()` 硬编码了 88 字符校验，而实际 key 是 128 字符

修复：
- 更新 `_rev` 为正确的 128 字符直接翻转 hex string
- Java 层：不用 StringBuilder，改为手动 char[] 交换反转（`for i < n/2: swap`）
- Native 层 `verifyNative()`：校验从 88 改为 128

实测：`POST /auth 200` → `POST /generate_image 200` → 图片 URL 返回 ✅

**问题 10（延续）： Frida 检测 + 安全增强**

- 新增 `SecurityEnforcer.java`：Frida 检测（5法）、调试器检测、APK 签名校验
- `RootDetector.java` 增强至 14 项检测（新增 Magisk / Zygisk 系统目录、magiskd 进程扫描）
- 集成至 MainActivity：`SecurityEnforcer.sweep()` 在 RootDetector 之后运行

---

### 2026-05-03 — 凌晨调试（重要）

**问题 9：Release APK 所有网络请求超时**

现象：Release build 点 Generate 后一直转圈，最终报 network error。Debug build 正常。

根因：**ProGuard/R8 minification 破坏了 OkHttp 内部反射调用链**（connectTimeout 等方法被优化掉）

解决方案：`minifyEnabled = false`（release 不混淆）—— 这是唯一有效方案

**问题 8：模拟器 DNS 解析到 IPv6 导致 SSL 超时**

现象：模拟器 ping `ai.elliottwen.info` 一直通，但 HTTPS 请求超时。

根因：模拟器 DNS 同时返回 IPv4 和 IPv6 地址。OkHttp 尝试 IPv6 路由到 Cloudflare 时端口 443 不通。HTTP/2 alt-svc 自动回退 IPv4 需要约 60 秒，导致超时。

解决方案：启动模拟器时加 `-dns-server 8.8.8.8` 强制 Google DNS，解析到纯 IPv4。

**问题 7：SSL Certificate Pinning 导致 Release 连接失败**

根因：最初设置的 pin 是**完整证书 DER 的 SHA-256 摘要**，但 OkHttp `CertificatePinner` 验证的是 **SPKI（SubjectPublicKeyInfo）SHA-256 hash**。

解决方案：用 OpenSSL 提取服务器真实 SPKI hash：`JchgWAvcRYiIxf8gVP+SWeD5PCqwJVYGxQd2YqbSrz4=`。

---

## 🔐 安全等级说明（Fortify — Part 2）

### 12 层 API Key 保护架构

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 1  │  key 仅存于 C/JNI，不在 Java 明文出现            │
│  Layer 2  │  自定义类摩斯码编码（di / da / tu / ka）         │
│  Layer 3  │  8 组分片乱序存储                                │
│  Layer 4  │  rolling mask（按字符位置变化）                  │
│  Layer 5  │  位旋转还原（bit rotation）                      │
│  Layer 6  │  JNI verifyNative() + FNV-1a checksum 校验      │
│  Layer 7  │  native 临时缓冲区安全擦除                       │
│  Layer 8  │  ProGuard：删除所有 Log + 代码混淆               │
│  Layer 9  │  android:fullBackupContent="false"             │
│  Layer 10 │  Frida 检测（maps/TracerPid/端口/包名）         │
│  Layer 11 │  调试器检测 + suspicious runtime blocking        │
│  Layer 12 │  RootDetector 14 项检测（含 Magisk/Zygisk）     │
└─────────────────────────────────────────────────────────────┘
```

### SSL Certificate Pinning

- 目标：防止中间人攻击（MITM）
- 实现：OkHttp `CertificatePinner` + SPKI SHA-256 pin
- Pin 值：`JchgWAvcRYiIxf8gVP+SWeD5PCqwJVYGxQd2YqbSrz4=`
- Debug build 关闭（方便测试），Release build 强制开启

### Root Detection（14 项）

| 检测项 | 方法 |
|--------|------|
| su 二进制路径 | 检查 16 个常见路径 |
| test-keys | Build.TAGS 是否含 testkeys |
| Root 包名 | 包列表含 Xposed/Substrate/Magisk |
| 系统属性 | ro.build.tags / ro.secure |
| su 命令执行 | 运行 `su -c id` 检查返回 |
| **Magisk 目录** | `/data/adb/magisk`, `/data/adb/zygisk` |
| **Magisk Manager** | `com.topjohnwu.magisk` 包名 |
| **Zygisk 模块** | `/data/adb/modules/` 内容扫描 |
| **MagiskSU** | `/data/adb/su/bin/su`, `magisk-su` |
| **magiskd 进程** | `ps -A` 扫描 `magiskd` |

### Frida 检测（5 法）

1. `/data/local/tmp/frida-agent-arm64.so` 文件存在检测
2. `/proc/self/maps` 含 "frida" / "linjector" 检测
3. `/proc/self/fd` Frida 相关文件描述符检测
4. Frida 默认端口（23947/23948/23949）监听检测
5. `/proc/self/cmdline` 含 "frida" 检测

### 安全评估

| 攻击面 | 难度 | 说明 |
|--------|------|------|
| Java 反编译 | ★★★★☆ | Java 层看不到真实 key，只能看到 JNI 入口 |
| Frida Hook | ★★★★☆ | 5 种检测方法，部分阻断调试 |
| Native 反汇编 | ★★★☆☆ | .so 仅含简单 reverse 操作 |
| 网络抓包 | ★★★★★ | SSL Pinning 防止 MITM |
| 备份提取 | ★★★★★ | fullBackupContent=false 阻止云备份 |

**整体评级：高度安全**（防普通逆向工程师 + 网络层攻击）

---

## 🐛 遇到的所有问题及解决方案汇总

| # | 问题 | 根本原因 | 解决方案 |
|---|------|----------|----------|
| 1 | Auth header 401 | key 前加了 "Bearer " 前缀 | 直接传 key，不用前缀 |
| 2 | Debug build 无法连接 | SSL Pinning 在模拟器上失败 | 检测 FLAG_DEBUGGABLE，Debug 跳过 Pinning |
| 3 | isValidKey 一直失败 | key 包含大写字符，正则只接受小写 | 改为 `^[a-fA-F0-9]{88}$` |
| 4 | 编译错误：CertificatePinner 重复 | 同时 import 了两个不同版本的类 | 只保留 okhttp3 版本 |
| 5 | 构建失败 | `tools:targetApi` 位置错误 | 只保留在 `<manifest>` 标签 |
| 6 | API key 解码后损坏 | 早期字符串方案容易出错 | 迁移到 native-only 自定义编码 |
| 7 | SSL Pinning Release 失败 | Pin 用了完整证书 DER SHA-256，而非 SPKI hash | 用 OpenSSL 提取正确 SPKI hash |
| 8 | Release 所有请求超时 | R8/ProGuard 破坏了 OkHttp 内部反射调用链 | `minifyEnabled = false` |
| 9 | 模拟器 HTTPS 超时 | DNS 同时返回 IPv4+IPv6，IPv6 路由不通 | 启动加 `-dns-server 8.8.8.8` |
| 10 | getApiKey 始终返回空 | StringBuilder.reverse() JNI 编码异常；验证长度 88 而非 128 | 手动 char[] 交换；验证长度改为 128 |

---

## ⚠️ 已知限制 / 待办

1. **客户端密钥永远不是绝对安全的**：当前实现是高强度加固，不是数学意义上的不可提取
2. **CS702 Part 3 Attack**：尚未开始
3. **提交前任务清单**：见 [`SUBMISSION_TODO_2026-05-06_zh.md`](./SUBMISSION_TODO_2026-05-06_zh.md)

---

## 📂 项目结构

```
AIImageGenerator/
├── app/
│   ├── src/main/
│   │   ├── cpp/native-key.c          # NDK C 源码（reverse + verify + secure clear）
│   │   ├── java/com/cs702/aigenerator/
│   │   │   ├── ApiClient.java        # 网络请求 + SSL Pinning
│   │   │   ├── ApiService.java       # Retrofit 接口
│   │   │   ├── ApiModels.java       # 数据模型
│   │   │   ├── NativeKeyStore.java  # API key 12层保护
│   │   │   ├── SecurityConfig.java  # SSL Pin 配置
│   │   │   ├── RootDetector.java    # Root/越狱检测（14项）
│   │   │   ├── SecurityEnforcer.java# Frida检测+调试器+签名校验
│   │   │   └── MainActivity.java    # 主界面 + 调用各安全模块
│   │   ├── res/xml/
│   │   │   ├── network_security_config.xml
│   │   │   └── data_extraction_rules.xml
│   │   └── AndroidManifest.xml
│   └── proguard-rules.pro
└── README.md
```

---

## 🔗 相关链接

- GitHub Repo：https://github.com/kndhjk/AIImageGenerator
- Release APK：发布后见 GitHub Releases
- 个人技术笔记：https://github.com/kndhjk/openclaw-termux-install-notes
