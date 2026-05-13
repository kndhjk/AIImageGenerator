# AI Image Generator

Android app for CS702 — Build & Fortify assignment. Generates AI images from text prompts using the `ai.elliottwen.info` API.

---

## 📦 当前版本 v1.0.3

**APK 大小**：约 6.3 MB（当前 debug artifact）

**最后更新**：2026-05-13

**签名**：debug keystore（提交用）；当前主线已完成 Windows / Android Studio 编译与模拟器运行验证

**当前主线提交**：`2483608` — `fix: remove obsolete native libs causing 16kb warning`

**关键安全提交**：`fcaafcf` — `security: require remote fragment for api key release flow`

**GitHub 在线打包**：
- Workflow：`Release APK`
- Run：`25783372526`
- Artifact：`AIImageGenerator-debug-apk`
- Artifact ID：`6963473196`
- SHA-256：`f053aa81a27702fc4161930c6945893c8e50e2d60fa0901994723b398e570d3f`

**下载地址**：
- 历史 Release：https://github.com/kndhjk/AIImageGenerator/releases/tag/v1.0.1
- 最新在线构建产物：GitHub Actions `Release APK` run `25783372526`

> 说明：旧版本 README 中关于 “含 3 架构 Native .so 库” 的描述已不再适用于当前主线。2026-05-13 版本已移除过时 JNI `.so`，当前实际保护链依赖 `AES-GCM + remote fragment`。

---

## 🛠️ 开发日志

### 2026-05-13 — Remote Fragment 双层放钥 + 在线打包 + Windows/Android Studio 验证

**问题 12：旧的本地可逆 key 保护强度不足，需要继续加固**

新增：
- `RemoteKeyProvider.java`
- 运行时先请求远端 fragment：`http://4.155.227.179/api/aig/fragment?v=1`
- 请求头要求：`X-App-Id: com.cs702.aigenerator`
- `NativeKeyStore.java` 现在必须拿到缓存的 remote fragment，才能导出最终 API key
- `MainActivity.java` 在点击 Generate 后，先 `ensureFragment(...)`，拿不到 fragment 或 key 校验失败则直接停止

后端配套：
- 复用现有服务器接口提供 fragment，避免另起服务破坏既有网站
- 线上接口已验证可返回 `{ version: 1, fragment: ... }`

提交：
- `fcaafcf` — `security: require remote fragment for api key release flow`

**问题 13：16KB page-size 模拟器兼容提示**

排查：
- `Medium_Phone_API_36` 模拟器会弹 “This app isn’t 16 KB compatible”
- 旧版 APK 内含 `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}/libnative-key.so`
- 但当前真实 key 释放流程已经不再依赖这些旧 JNI `.so`

修复：
- 删除过时 JNI `.so`
- 将 `NativeKeyStore` 中 `System.loadLibrary("native-key")` 相关逻辑退场
- 保留当前真实保护链：`AES-GCM + remote fragment`

结果：
- `Android CI` 成功
- 最新 GitHub 在线打包 `Release APK` 成功
- Windows 的 Android Studio 环境重新拉取、编译、安装成功
- `VaultDevice` 模拟器成功进入主界面
- `Medium_Phone_API_36` 的 16KB 提示更像 AVD/系统检查行为，而不是当前 APK 中仍残留 `.so`

提交：
- `2483608` — `fix: remove obsolete native libs causing 16kb warning`

**Windows / Android Studio 实测结果**
- 机器：`zyzmc@192.168.31.98`
- Android Studio 路径：`C:\Program Files\Android\Android Studio`
- 成功编译 APK：`C:\Users\zyzmc\AIImageGenerator\app\build\outputs\apk\debug\app-debug.apk`
- `VaultDevice` 模拟器已验证主界面可正常显示：
  - `✨ AI Image Generator`
  - `Turn your words into stunning visuals`
  - `Describe your image...`
  - `Generate`
  - `Save`

> 注：以下 2026-05-03 及更早内容保留为历史开发记录，不删除。


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
│  Layer 1  │  bytecode：翻转 128 hex string `_rev`           │
│  Layer 2  │  System.loadLibrary("native-key") 加载 .so       │
│  Layer 3  │  手动 char[] reverse（不用 StringBuilder JNI）   │
│  Layer 4  │  VALIDATE_CHAR='c' 运行时校验                   │
│  Layer 5  │  JNI verifyNative() native 层格式校验           │
│  Layer 6  │  clearKeyMemory() native 安全擦除               │
│  Layer 7  │  诱饵方法 getFakeApiKey() / isKeyValid()        │
│  Layer 8  │  ProGuard：删除所有 Log + 代码混淆               │
│  Layer 9  │  android:fullBackupContent="false"             │
│  Layer 10 │  Frida 检测（agent文件/proc maps/fd/端口/cmd） │
│  Layer 11 │  调试器检测 + APK 签名校验                       │
│  Layer 12 │  RootDetector 14 项检测（含 Magisk/Zygisk）    │
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
| Java 反编译 | ★★★★☆ | `_rev` 可见但需手动反转，诱饵方法干扰 |
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
| 6 | API key 解码后损坏 | XOR+Base64 多字节字符被当 UTF-8 转换 | 改用纯字符串反转（ASCII 安全） |
| 7 | SSL Pinning Release 失败 | Pin 用了完整证书 DER SHA-256，而非 SPKI hash | 用 OpenSSL 提取正确 SPKI hash |
| 8 | Release 所有请求超时 | R8/ProGuard 破坏了 OkHttp 内部反射调用链 | `minifyEnabled = false` |
| 9 | 模拟器 HTTPS 超时 | DNS 同时返回 IPv4+IPv6，IPv6 路由不通 | 启动加 `-dns-server 8.8.8.8` |
| 10 | getApiKey 始终返回空 | StringBuilder.reverse() JNI 编码异常；验证长度 88 而非 128 | 手动 char[] 交换；验证长度改为 128 |

---

## ⚠️ 已知限制 / 待办

1. **Placeholder API Key**：`NativeKeyStore.java` 中的 key 是课程占位符，提交前需替换为真实的 Canvas Authorization header
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
- Release APK：https://github.com/kndhjk/AIImageGenerator/releases/tag/v1.0.1
- 个人技术笔记：https://github.com/kndhjk/openclaw-termux-install-notes
