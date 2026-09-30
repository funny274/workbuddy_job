# 项目长期记忆

## Spring AI 教程项目的云端落点（重要约定）

用户要求：**《Spring AI 2.0 全面学习教程》之后的所有改动，直接更新到资料库云端版本**。

- 云端节点：`Spring-AI-2.0-全面学习教程`，`kind=doc`
- nodeId / pageId：`DkLa7xZAlFBWnVNuGFRESw`
- spaceId：`39PaVCWIYplriBMsa4LjLX`（**个人空间**，用户为 owner）
- 链接：https://www.workbuddy.cn/space/d/DkLa7xZAlFBWnVNuGFRESw
- 本地工作副本：`Spring-AI-2.0-全面学习教程.md`（仓库根目录）

### 该文档的更新方式（踩过的坑，别再试错）

**这份文档体量很大（>30 万字符），`get_doc_reviews.py` 读取会稳定返回 `E_PAYLOAD_TOO_LARGE`**（内联代理有载荷上限）。后果：

1. **无法读取正文 ⇒ 无法获取 blockId ⇒ 常规的按块增量编辑（`submit_doc_edit.py` / `submit_review_edit.py`）这条路走不通。**
2. **`create_doc.py --confirm-overwrite` 也不可用**：正文含大量 GFM 表格，官方明确「带表格的正文可靠地 524 超时」。
3. **可行路径 = 大文件导入覆盖**（已验证成功，2026-09-30）：
   1. `space.importer.get-upload-credential --file-name <名> --file-size <字节> --node-block-id DkLa7xZAlFBWnVNuGFRESw` → 返回 `uploadUrl`(COS 签名 PUT)、`cosKey`、`taskId`
   2. `curl -X PUT --data-binary @<本地md> "<uploadUrl>"` → 期望 HTTP 200（该签名只签了 host，无需额外头）
   3. `space.importer.import-local-file --space-id 39PaVCWIYplriBMsa4LjLX --node-block-id DkLa7xZAlFBWnVNuGFRESw --cos-key <cosKey> --file-name <名> --title <标题> --task-id <taskId>`
      → 成功时返回**同一** `nodeBlockId` + `nodeKind=doc`（是替换正文，不新建副本）
   4. 校验：`space.workspace.node-info --node-id ...` 看 `version` 是否 +1

**注意**：临时文件（含签名 URL / cosKey）必须放在工作区内的临时目录并及时删除，不要用 `/tmp`（Git Bash 与原生 Windows Python 的路径映射不一致）。

### 完整操作配方（下次直接照做）

```bash
# 1) 改本地 md（唯一可编辑的副本）
# 2) 同步到云端
cd <repo> && mkdir -p .tmp
SZ=$(wc -c < Spring-AI-2.0-全面学习教程.md)
python3 "<LIB>/space_api.py" space.importer.get-upload-credential \
  --file-name "Spring-AI-2.0-全面学习教程.md" --file-size "$SZ" \
  --node-block-id DkLa7xZAlFBWnVNuGFRESw > .tmp/cred.json
# 取 uploadUrl / cosKey / taskId（用 python 解析，注意用工作区相对路径）
curl -s -w "%{http_code}" -X PUT --data-binary @"Spring-AI-2.0-全面学习教程.md" "<uploadUrl>"
python3 "<LIB>/space_api.py" space.importer.import-local-file \
  --space-id 39PaVCWIYplriBMsa4LjLX --node-block-id DkLa7xZAlFBWnVNuGFRESw \
  --cos-key "<cosKey>" --file-name "Spring-AI-2.0-全面学习教程.md" \
  --title "Spring-AI-2.0-全面学习教程" --task-id "<taskId>"
rm -rf .tmp
```

`<LIB>` = `D:/Program Files/WorkBuddy/resources/app.asar.unpacked/resources/plugins/workbuddy-builtin/skills/library`

### 版本基线

- 本地与云端当前均为 **v1.1**（2026-09-30）：Spring AI 2.0.1 / Spring Boot 4.1.x 基线，含 27 章正文 + 附录 A/B/C。
- v1.1 相对 v1.0 的增量：A2A 协议（§19.4）、`spring-ai-session`（§6.7 扩写）、重试与错误分类（§23.5）、底层官方 SDK 替换、工具循环 token 累加口径、2 处勘误、附录 C 修订记录。

## HTML 阅读版（带左侧目录树）

- 云端页面节点：`Spring-AI-2.0-全面学习教程.html`，nodeId=`AGyK3vefBmseOFzlbtXgla`
  - 编辑态：https://www.workbuddy.cn/space/d/AGyK3vefBmseOFzlbtXgla
  - 分享态：https://workbuddy.link/p/AGyK3vefBmseOFzlbtXgla
- 本地文件：`Spring-AI-2.0-全面学习教程.html`（自包含，无外链图片）
- **生成脚本（可复用）**：`.workbuddy/scripts/build_tutorial_html.py`
  - 运行环境：`C:/Users/issuser/.workbuddy/binaries/python/envs/md2html/Scripts/python.exe`（已装 markdown + pygments）
  - 用法：先改 md，再跑脚本（脚本内路径按工作区相对路径），产出 `.tmp/springai-tutorial.html`
  - 特性：左栏 H1/H2/H3 三级目录树、搜索过滤、滚动高亮、分组折叠、锚点 323 对全对齐、代码高亮、响应式抽屉
- **重生成后同步到云端**：`page/import_html.py "<html>" --node-block-id AGyK3vefBmseOFzlbtXgla`（注意：重新导入需 admin 权限，个人空间用户为 owner 可满足）
- 踩坑：`slugify` 对纯 emoji 标题会产出空串 → 脚本已退化为 `sec-N`；过滤「目录」标题必须放在 `build_tree()` 之前，否则建树用了未过滤列表
