# CS702 Part 3 Attack — API Key 提取手册

## 目标 APK
- 文件：`AIImageGenerator-release.apk`（MD5: `a9ac0eb74d0bf96490bf5de2764723f2`）
- 来源：https://github.com/kndhjk/AIImageGenerator/releases/tag/v1.0.1

---

## 方法一：静态反编译（最简单，5分钟）

### 步骤 1 — 反编译 APK
```bash
# 下载并解压 APK
curl -L -o AIImageGenerator-release.apk \
  "https://github.com/kndhjk/AIImageGenerator/releases/download/v1.0.1/AIImageGenerator-release.apk"

# 解压为 ZIP（APK 就是 ZIP）
unzip -q AIImageGenerator-release.apk -d apk_contents
```

### 步骤 2 — 用 jadx 反编译 Java 代码
```bash
# 下载 jadx（https://github.com/skylot/jadx/releases）
curl -L -o jadx.zip "https://github.com/skylot/jadx/releases/download/v1.5.1/jadx-1.5.1.zip"
unzip -q jadx.zip -d jadx_dir

# 反编译（--no-res 不需要资源文件，--no-debug-info 去掉调试信息）
java -Dfile.encoding=UTF-8 -Xmx2048m \
  -cp jadx_dir/lib/jadx-1.5.1-all.jar \
  jadx.cli.JadxCLI \
  -d decompiled \
  --no-res --no-debug-info \
  AIImageGenerator-release.apk
```

### 步骤 3 — 找到关键文件
```bash
# NativeKeyStore.java 就是存储 API key 的地方
cat decompiled/sources/com/cs702/aigenerator/NativeKeyStore.java
```

**反编译结果：**
```java
public class NativeKeyStore {
    private static final String _rev = "<redacted-sample-blob>";

    public static String getApiKey() {
        try {
            String sb = new StringBuilder(_rev).reverse().toString();
            return sb.charAt(0) != VALIDATE_CHAR ? HttpUrl.FRAGMENT_ENCODE_SET : sb;
        } catch (Exception unused) {
            return HttpUrl.FRAGMENT_ENCODE_SET;
        }
    }
    ...
}
```

### 步骤 4 — Python 提取 API Key
```python
# _rev 是翻转后的十六进制字符串，直接翻转回来即可
_rev = "<redacted encoded public test key fragments>"
api_key = _rev[::-1]  # Python 翻转字符串
print(api_key)
# 输出：The extracted value matched the public test key provided in the assignment brief. The actual key is redacted here.
```

**难度**：★☆☆☆☆（`_rev` 就是翻转后的字符串，任何会 Python 的人 5 分钟内都能提取）

---

## 方法二：Frida 动态 Hook（运行时拦截）

### 环境准备
```bash
# PC 上安装 frida-tools
pip install frida-tools

# 手机/模拟器上需要运行 frida-server
adb push frida-server /data/local/tmp/
adb shell "chmod 755 /data/local/tmp/frida-server"
adb shell "/data/local/tmp/frida-server &"
```

### Hook 脚本 — 提取 API Key
```javascript
// extract_key.js
Java.perform(function() {
    var NativeKeyStore = Java.use("com.cs702.aigenerator.NativeKeyStore");

    // Hook getApiKey() — 直接拿到返回值
    NativeKeyStore.getApiKey.implementation = function() {
        var key = this.getApiKey();
        console.log("[+] API Key intercepted: " + key);
        return key;
    };

    console.log("[+] NativeKeyStore.getApiKey() hooked!");
});
```

### 运行
```bash
# 附加到 app 进程
frida -U -f com.cs702.aigenerator --no-pager -e "Java.perform(function() { var NK = Java.use('com.cs702.aigenerator.NativeKeyStore'); NK.getApiKey.implementation = function() { var k = this.getApiKey(); console.log('[+] Key: ' + k); return k; }; });" -o key_log.txt

# 或保存脚本后运行
frida -U -f com.cs702.aigenerator -l extract_key.js
```

**难度**：★★☆☆☆（需要 Frida 环境，但代码非常短）

---

## 方法三：Frida 绕过 SSL Certificate Pinning（抓 HTTPS 流量）

### Hook 脚本 — 禁用 CertificatePinner
```javascript
// bypass_pinning.js
Java.perform(function() {
    // 找到 CertificatePinner$Builder
    var Builder = Java.use("okhttp3.CertificatePinner$Builder");

    // 把所有 host 的 pin 都替换为已知值（空实现）
    Builder.build.implementation = function() {
        console.log("[+] Bypassing CertificatePinner...");
        return this.callSuper();
    };

    // 或者直接 hook check() 方法，让它永远返回 true
    var Pinner = Java.use("okhttp3.CertificatePinner");
    Pinner.check.overload("java.lang.String", "java.util.List").implementation = function(host, pins) {
        console.log("[+] CertificatePinner.check() bypassed for: " + host);
        // 不抛出异常 = 验证通过
    };
});
```

### 运行
```bash
frida -U -f com.cs702.aigenerator -l bypass_pinning.js
# 然后用 Wireshark /mitmproxy 抓 HTTPS 明文流量
```

**难度**：★★★☆☆（需要理解 OkHttp CertificatePinner 机制）

---

## 方法四：反汇编 Native .so（提取 native 层逻辑）

### 提取 .so 文件
```bash
unzip -q AIImageGenerator-release.apk -d apk_contents
# 找到 native 库
ls apk_contents/lib/arm64-v8a/libnative-key.so
cp apk_contents/lib/arm64-v8a/libnative-key.so ./
```

### 用 objdump / IDA Free 反汇编
```bash
# 查看导出符号（知道有哪些 JNI 函数）
arm-linux-gnueabi-objdump -T libnative-key.so | grep -i "Java\|getNativeKey\|verify"

# 或用 readelf
readelf -s libnative-key.so | grep Java
```

### IDA Free 打开 .so
1. 打开 IDA Free
2. File → Open → 选择 `libnative-key.so`
3. 等分析完成
4. 看 `Java_com_cs702_aigenerator_NativeKeyStore_getNativeKey` 函数
5. 函数逻辑：翻转输入字符串并返回（简单 reverse）

**关键发现**：.so 里的 `getNativeKey` 只是简单翻转。攻击者也可以直接在 Java 层做 `sb.reverse()` 而不用分析 ARM 汇编。

**难度**：★★★☆☆（IDA Free 免费，native 代码逻辑简单）

---

## 方法五：直接解包 classes.dex（最底层）

```bash
# APK 解压后有 classes.dex
unzip -q AIImageGenerator-release.apk classes.dex

# 用 baksmali / smali 反汇编为 smali 汇编
java -jar baksmali-2.5.2.jar disasm classes.dex dex_out/

# 在 dex_out 里搜索 _rev
grep -r "_rev\|const-string.*b0e1d7" dex_out/ | head
# 结果：找到 NativeKeyStore.smali，_rev 字符串值直接硬编码在其中
```

**难度**：★★☆☆☆（smali 知识要求，但搜索最直接）

---

## 总结：各方法难度与耗时

| 方法 | 工具 | 耗时 | 难度 | 能否提取 key |
|------|------|------|------|------------|
| 1. jadx 反编译 | jadx | 10 分钟 | ★☆☆☆☆ | ✅ 直接拿到 _rev |
| 2. Frida hook | frida | 5 分钟 | ★★☆☆☆ | ✅ Hook getApiKey() |
| 3. Frida SSL bypass | frida + mitmproxy | 15 分钟 | ★★★☆☆ | ✅ 抓 HTTPS |
| 4. .so 反汇编 | IDA Free | 20 分钟 | ★★★☆☆ | ✅ 看懂 reverse 逻辑 |
| 5. DEX 直接搜索 | baksmali | 10 分钟 | ★★☆☆☆ | ✅ 字符串搜索 |

---

## 防御建议（供 Fortify 思考）

以上方法全部有效，说明当前保护层主要作用是：
- **增加提取时间**（让非专业人员止步）
- **展示多层防御意识**（符合课程要求）
- **SSL Pinning 有效阻止网络层抓包**（方法三需要先绕过）

真正的商业级保护需要：
- 代码壳（VMP / OLLVM 混淆）
- Frida 检测阻止调试
- 服务器端授权（key 不存在 APK 里）
