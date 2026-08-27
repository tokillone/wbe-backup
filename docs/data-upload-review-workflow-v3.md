# 数据上传与审核流程 V3（单表投稿、五表审核、增量发布）

## 角色与文件

- 普通用户上传一个且仅一个名为 `原始数据` 的 Sheet。每行是一项原始指标，首次投稿的 `投稿行ID` 留空。
- 系统校验后生成不可变 `投稿行ID`；退回重传在同一批次创建新版本，旧版本和原始文件均保留。
- 审核人员下载系统预填的五 Sheet 草稿：`规范数据记录`、`文献基础信息`、`点位关联表`、`采样方法审计`、`ICD11映射`。
- `核心标记物优先级识别`、正式宽表、绘图字段和统计字段不是人工上传内容。

## 状态机

```text
VALIDATION_FAILED / REVISION_REQUIRED
  -> PENDING_REVIEW
  -> READY_TO_PUBLISH
  -> PUBLISHING
  -> PUBLISHED

发布失败 -> PUBLISH_FAILED（正式事务回滚，可重试）
```

审核人员在 `PENDING_REVIEW`、`READY_TO_PUBLISH` 或 `PUBLISH_FAILED` 可以退回；退回原因必填。审核包校验通过后只保留一次 `确认入库`，不再设置初审、八项终审和独立同步操作。

## 关键一致性规则

- 审核覆盖使用不可变 `投稿行ID`，不使用会因纠正内容而变化的整行指纹。
- 每个投稿行必须且只能被标记为 `发布` 或 `排除`；排除原因必填。
- 审核包中的原始快照字段不可修改；标准身份、数值、单位或时间改变时必须填写纠正原因，并写入字段级审计表。
- DOI 可空，但必须有标题、年份、期刊/来源和源文件；复用已有文献时，文献编号与 DOI 必须一致。
- 同一记录组内不得无解释地重复同一指标类型和统计量。
- 正式业务键不包含上传批次或 Excel 行号；重复确认和跨批次重复数据不会重复写入。

## 发布语义

- 发布只插入当前批次的新文献、维度、记录、点位桥和 ICD 路径，不执行任何全表清空。
- 第一版不通过投稿流程更新或删除历史正式记录。
- 发布、点位桥、首页事实及地图派生刷新处于同一事务；失败时全部回滚。
- `dataset_releases` 保存发布状态、操作者、增量行数、跳过重复数和清单。
- 旧 `/preview`、`/approve`、`/source-review/accept`、`/sync` 接口仅保留历史兼容；新页面不再调用。

## 新接口

```text
GET  /api/data-uploads/submission-template
POST /api/data-uploads
POST /api/data-uploads/{id}/submission-revisions
GET  /api/data-uploads/{id}/review-draft
POST /api/data-uploads/{id}/review-packages
POST /api/data-uploads/{id}/return
POST /api/data-uploads/{id}/publish
```
