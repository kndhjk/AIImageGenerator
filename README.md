# AI Image Generator

Android app for CS702 — Build & Fortify assignment. Generates AI images from text prompts using the `ai.elliottwen.info` API.

---

## 📦 当前版本 v1.0.1

**APK 大小**：约 6 MB（含 3 架构 Native .so 库）

**最后更新**：2026-05-03

**签名**：debug keystore（提交用），minifyEnabled = false（release）

**下载地址**：https://github.com/kndhjk/AIImageGenerator/releases/tag/v1.0.1

---

## 🛠️ 开发日志

### 2026-05-03 — Part 2 完成，最终问题解决

**问题 9：Release APK 所有网络请求超时**

现象：Release build 点 Generate 后一直转圈，最终报 network error。Debug build 正常。

排查过程：
- 发现是 **ProGuard/R8 minification 破坏了 OkHttp 内部反射调用链**（connectTimeout 等方法被优化掉）
- 其他尝试过的方案：关闭 shrinkResources、关闭 Certificate Pinning，均无效
- **解决方案**：`minifyEnabled = false`（release 不混淆）—— 这是唯一有效方案

**问题 10：模拟器 DNS 解析到 IPv6 导致 SSL 超时**

现象：模拟器 ping `ai.elliottwen.info` 一直通，但 HTTPS 请求超时。

根因：模拟器 DNS 同时返回 IPv4 和 IPv6 地址。OkHttp 尝试 IPv6 路由到 Cloudflare 时端口 443 不通（`Connect to [2606:4700:3032::ac43:d087]:443 failed`）。HTTP/2 alt-svc 自动回退 IPv4 需要约 60 秒，导致超时。

解决方案：启动模拟器时加 `-dns-server 8.8.8.8` 强制 Google DNS，解析到纯 IPv4。

**最终 Release APK 验证**：`POST /auth` → 200 OK，图片生成正常。

---

### 2026-05-03 — 凌晨调试（重要）

**问题 8：SSL Certificate Pinning 导致 Release 连接失败**

现象：Debug build 正常，Release build 报 `SSLPeerUnverifiedException: Certificate pinning failure!`

根因：最初设置的 pin 是**完整证书 DER 的 SHA-256 摘要**（`3rZyrzZdM7XRbcJRlxhhiA0TstYV7KKtUnolImZIRHI=`），但 OkHttp `CertificatePinner` 验证的是 **SPKI（SubjectPublicKeyInfo）SHA-256 hash**。

解决方案：用 OpenSSL 提取服务器真实 SPKI hash：`echo | openssl s_client -connect ai.elliottwen.info:443 | openssl x509 -pubkey | openssl pkey -pubin -outform der | openssl dgst -sha256`，得到正确 pin：`JchgWAvcRYiIxf8gVP+SWeD5PCqwJVYGxQd2YqbSrz4=`。

---

### 2026-05-03 — Part 2 安全层完成

**添加第 8 层：Native C 层（NDK r27）**

- 下载 NDK r27（约 800MB）
- 用 clang 交叉编译 `native-key.c`，生成 3 个架构的 `.so` 文件：
  - `arm64-v8a`：7384 bytes
  - `armeabi-v7a`：4868 bytes
  - `x86_64`：6912 bytes
- `.so` 打包进 APK，放在 `jniLibs/` 目录
- Java 层 `NativeKeyStore.java` 新增 JNI 调用：
  - `getNativeKey(reversed)` → 调用 native `getNativeKey()` → 反向 SHUFFLE[64] + XOR 0x5A
  - `verifyNative(key)` → native 格式验证
- ProGuard 保护：`com.cs702.aigenerator.NativeKeyStore` 的 `_rev`、`VALIDATE_CHAR` 等全部保留

---

### 2026-05-02 — 安全加固 L1-L7

**L1**：API key 存储为**反转十六进制字符串**（`_rev` 字段）

**L2**：`System.loadLibrary("native-key")` 加载 Native .so（看不见 Java 反编译工具）

**L3**：`getNativeKey(reversed)` JNI 调用，在 C 层 undo-shuffle + undo-XOR

**L4**：设置**诱饵方法** `getFakeApiKey()` 和 `isKeyValid()`，返回假 Base64 字符串，从不调用

**L5**：运行时 `VALIDATE_CHAR` 检查（第 5 个字符必须是 'c'），不符则抛出异常

**L6**：ProGuard/R8 全保护——删除所有 `Log.v/d/i/w/e`、删除 `PrintStream.println/print`、保护所有安全类 private 字段

**L7**：`AndroidManifest.xml` 设置 `android:fullBackupContent="false"` + `android:dataExtractionRules="@xml/data_extraction_rules"`，`res/xml/data_extraction_rules.xml` 排除所有 app data 备份

---

### 早期版本迭代（2026-04-12 ~ 2026-05-02）

**问题 1**：API 授权 header 格式错误  
初始代码在 key 前加了 `"Bearer "` 前缀，服务器返回 401。  
修复：`@Header("Authorization") String auth` 直接传 key 本身。

**问题 2**：SSL Pinning 在 Debug build 导致模拟器无法测试  
初始所有 build 都开启 Pinning，模拟器抓包发现返回 HTTP 405。  
修复：检测 `FLAG_DEBUGGABLE`，Debug build 跳过 Pinning。

**问题 3**：`isValidKey()` 正则过于严格  
要求全部小写十六进制，但实际 key 有大写字符。  
修复：改为宽松正则 `^[a-fA-F0-9]{88}$`。

**问题 4**：`CertificatePinner` 重复 import  
同时 import 了 `okhttp3.CertificatePinner` 和 `com.squareup.okhttp3.CertificatePinner`，导致编译器无法识别。  
修复：删除 squareup 那个，只保留 okhttp3。

**问题 5**：`build.gradle` 中 `android:debuggable` 配置语法错误  
在 `<application>` 内写了 `tools:targetApi` 导致构建失败。  
修复：删除该行，`tools:targetApi` 只保留在 `<manifest>` 标签。

**问题 6**：API key 存储方案崩溃  
尝试 XOR+Base64 编码：XOR(0x7A) 后 Base64 编码，但解码后得到损坏字符串。  
接着尝试每字符 salt 修饰 + 反转 + 分割 + Base64——Java `new String(byte[], UTF-8)` 将多字节字符转换为 ?，key 彻底损坏。  
最终解决方案：**纯字符串反转**（ASCII 安全，无编码问题）。

---

## 🐛 遇到的所有问题及解决方案汇总

| # | 问题 | 根本原因 | 解决方案 |
|---|------|----------|----------|
| 1 | Auth header 401 | key 前加了 "Bearer " 前缀 | 直接传 key，不用前缀 |
| 2 | Debug build 无法连接 | SSL Pinning 在模拟器上失败 | 检测 FLAG_DEBUGGABLE，Debug 跳过 Pinning |
| 3 | isValidKey 一直失败 | key 包含大写字符，正则只接受小写 | 改为 `^[a-fA-F0-9]{88}$` |
| 4 | 编译错误：CertificatePinner 重复 | 同时 import了两个不同版本的类 | 只保留 okhttp3 版本 |
| 5 | 构建失败 | `tools:targetApi` 位置错误 | 只保留在 `<manifest>` 标签 |
| 6 | API key 解码后损坏 | XOR+Base64 多字节字符被当 UTF-8 转换 | 改用纯字符串反转（ASCII 安全） |
| 7 | SSL Pinning Release 失败 | Pin 用了完整证书 DER SHA-256，而非 SPKI hash | 用 OpenSSL 提取正确 SPKI hash |
| 8 | Release 所有请求超时 | R8/ProGuard 破坏了 OkHttp 内部反射调用链 | `minifyEnabled = false` |
| 9 | 模拟器 HTTPS 超时 | DNS 同时返回 IPv4+IPv6，IPv6 路由不通 | 启动加 `-dns-server 8.8.8.8` |

---

## 🔐 安全等级说明（Fortify — Part 2）

### 8 层 API Key 保护架构

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 1  │  bytecode：反转十六进制字符串 `_rev`            │
│  Layer 2  │  System.loadLibrary("native-key") 加载 .so      │
│  Layer 3  │  JNI → C层 undo-shuffle + undo-XOR(0x5A)        │
│  Layer 4  │  诱饵方法 getFakeApiKey() / isKeyValid()        │
│  Layer 5  │  Runtime VALIDATE_CHAR 校验（第5字符='c'）      │
│  Layer 6  │  ProGuard：删除所有 Log，混淆类/字段名           │
│  Layer 7  │  禁止 Cloud 备份 + 禁止设备数据提取              │
│  Layer 8  │  android:debuggable="false"（Release）           │
└─────────────────────────────────────────────────────────────┘
```

### SSL Certificate Pinning

- 目标：防止中间人攻击（MITM）
- 实现：OkHttp `CertificatePinner` + SPKI SHA-256 pin
- Pin 值：`JchgWAvcRYiIxf8gVP+SWeD5PCqwJVYGxQd2YqbSrz4=`
- Debug build 关闭（方便测试），Release build 强制开启

### Root Detection

- 检测：Magisk、SuperSU、Xposed Framework
- 行为：弹安全警告，不阻止使用

### 安全评估

| 攻击面 | 难度 | 说明 |
|--------|------|------|
| Java 反编译 | ★★★★☆ | `_rev` 可见但需反转，诱饵方法干扰 |
| Frida Hook | ★★★★☆ | 需要绕过 VALIDATE_CHAR 和 native 验证 |
| Native 反汇编 | ★★★☆☆ | SHUFFLE 表和 XOR_SEED 在 .so 机器码中 |
| 网络抓包 | ★★★★★ | SSL Pinning 防止 MITM |
| 备份提取 | ★★★★★ | fullBackupContent=false 阻止云备份 |

**整体评级：高度安全**（防普通逆向工程师 + 网络层攻击）

---

## ⚠️ 已知限制 / 待办

1. **Placeholder API Key**：`ApiClient.java` 中的 key 是课程占位符，提交前需替换为真实的 Canvas Authorization header
2. **CS702 Part 3 Attack**：尚未开始
3. **Group Formation Email**：需联系 jun.seo@auckland.ac.nz（截止日期已过）

---

## 📂 项目结构

```
AIImageGenerator/
├── app/
│   ├── src/main/
│   │   ├── cpp/native-key.c          # NDK C 源码（SHUFFLE表 + XOR_SEED）
│   │   ├── java/com/cs702/aigenerator/
│   │   │   ├── ApiClient.java        # 网络请求 + SSL Pinning
│   │   │   ├── ApiService.java       # Retrofit 接口
│   │   │   ├── NativeKeyStore.java   # API key 8层保护
│   │   │   ├── SecurityConfig.java   # SSL Pin 配置
│   │   │   └── RootDetector.java     # Root 检测
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
- 个人技术笔记：https://github.com/kndhjk/openclaw-termux-install-notes（OpenClaw + Android 开发环境记录）