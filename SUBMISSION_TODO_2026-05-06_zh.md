# AIImageGenerator 提交前待办（2026-05-06）

## 当前最重要结论
**现在仓库里仍然是课程 test key / placeholder key 思路，不能直接提交。**

在正式提交前，必须把 `NativeKeyStore.java` 中当前用于测试的 Authorization header / placeholder key，替换成你们组自己的 **真实 Canvas key**，否则有被判零分的风险。

---

## 一、必须先做（最高优先级）

### 1. 替换 test key / placeholder key
- 位置：`app/src/main/java/com/cs702/aigenerator/NativeKeyStore.java`
- 当前状态：仓库 README 已明确写了这里还是 placeholder key
- 必须改成：你们组真实的 Canvas Authorization header
- 注意：
  - 不要提交课程公开 test key
  - 不要在文档、截图、log、README 中泄漏真实 key

### 2. 用真实 key 做完整功能回归
需要重新验证以下功能在真实 key 下都正常：
- 输入 prompt
- `/auth` 成功返回 signature
- `/generate_image` 成功返回图片 URL
- 图片能显示
- Save 按钮能保存到相册
- Cancel / stop waiting 功能正常
- 整体无崩溃、无卡死

### 3. 确认标准 Android Studio 模拟器可运行
课程要求很硬：
- app 必须能在标准 Android Studio emulator 运行
- 如果这项不过，Build/Fortify 可能直接 0 分

提交前至少要重新验证：
- 冷启动正常
- Generate 正常
- Save 正常
- 不会因为安全加固把正常流程误杀

---

## 二、Build 部分待办

### 4. 检查 Build 要求是否全部满足
逐项确认：
- [ ] 文本输入框可输入 prompt
- [ ] prompt 能发往服务器
- [ ] 返回图片能显示
- [ ] Save 按钮能保存图片
- [ ] UI 直观、可用
- [ ] 请求期间不会无限卡死
- [ ] 用户可以取消等待
- [ ] 不崩溃、不冻结

### 5. 做一轮手工 smoke test 并留截图
建议至少保存这些截图用于最后整理：
- 主界面
- 输入 prompt
- 生成中
- 生成成功
- 保存成功提示
- 安全加固相关提示（如果需要展示）

---

## 三、Fortify 部分待办

### 6. 补齐 build document
提交要求里明确说了：
- 要附上 **如何生成 APK 的 build document**

建议文档至少包含：
- JDK / Android Studio / SDK / Gradle 版本
- 构建命令
- debug / release 区别
- release APK 是如何签名的
- GitHub Actions workflow 如何产出 APK

### 7. 补齐 obfuscator declaration
如果用了任何开源混淆器 / 工具，必须声明。

当前建议声明内容：
- 是否使用 ProGuard / R8
- 是否使用 NDK/native `.so`
- 是否使用其他开源 obfuscation 工具
- 明确说明：**未使用商业 obfuscator**

### 8. 检查 Fortify 文档是否和代码一致
你现在 repo 里已经有：
- `SECURITY_REPORT.md`

提交前要再次核对：
- 文档里写的安全措施，代码里真的存在
- 代码里新增的 hardening，文档里也写清楚
- 不要出现“文档吹得很大，代码没实现”的情况

### 9. 检查 release workflow 的 secrets
我已经加了 signed release workflow，但它依赖这些 GitHub secrets：
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_ALIAS_PASSWORD`

提交前如果你想走正式 release 签名流程，必须把这些 secrets 配好并成功跑一次。

---

## 四、Attack 部分待办

### 10. 完成 Part 3 文档
课程 Attack 分两块：
- Our Apps
- Other Groups' Apps

当前状态：README 里写的是 **Part 3 Attack 尚未开始**，这块必须补。

### 11. 完成 Our Apps（基础攻击）
需要对老师提供的 sample apps 做攻击，找出隐藏 API keys。

建议输出：
- 使用了什么工具
- 关键发现过程
- 最终提取到的 key
- 截图 / 证据

### 12. 完成 Other Groups' Apps（高级攻击）
需要对其他组 app 做攻击并提取 key。

建议每个目标保留：
- APK 来源
- 攻击路径
- 关键证据
- 结果（成功/失败）

### 13. 注意学术诚信边界
必须避免：
- 共享别组 key
- 使用第三方服务器中转流量
- DDoS 老师服务器

---

## 五、最终提交待办

### 14. 整理最终 ZIP
提交内容应至少包括：
- APK
- 所有 source code
- 资源文件
- build document
- Fortify 文档
- Attack 文档
- obfuscator declaration

### 15. 重新检查 ZIP 是否可复现
在提交前最好做一次“从零复现”检查：
- 解压 ZIP
- 按文档构建
- 生成 APK
- 安装运行

### 16. 检查是否残留敏感信息
提交前全仓库排查：
- [ ] 不包含 test key 作为最终提交 key
- [ ] 不包含真实 key 的明文截图
- [ ] 不包含调试日志泄漏 Authorization header
- [ ] 不包含临时攻击脚本中留下的敏感信息

---

## 六、建议的立即行动顺序

建议按下面顺序做：

1. **先换掉 test key / placeholder key**
2. **用真实 key 跑完整功能测试**
3. **确认模拟器可稳定运行**
4. **补 build document 和 obfuscator declaration**
5. **补 Attack 文档**
6. **跑一次 signed release workflow**
7. **打最终提交 ZIP**

---

## 当前 blockers

### Blocker 1
你现在不能直接提交，因为：
- **仍在使用 test key / placeholder key**

### Blocker 2
Attack 文档还没完整完成。

### Blocker 3
正式签名 release 流程虽然我已经配好了 workflow，
但如果 GitHub secrets 没配齐，就还不能稳定产出正式 release APK。

---

## 一句话总结
**现在项目已经接近可交，但还不能直接交。最关键的未完成项就是：把 test key 换成你们组真实 key，并把 Attack + 提交文档补齐。**
