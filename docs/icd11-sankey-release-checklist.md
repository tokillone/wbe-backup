# ICD11 四层交互桑基图上线验收清单

## 1. 数据口径基线

在与生产数据同版本的预发布库执行：

```sql
SELECT
  COUNT(*) AS included_rows,
  COUNT(DISTINCT target_category) AS category_count,
  COUNT(DISTINCT CONCAT_WS(
    '||',
    icd11_level1_name,
    icd11_level2_name,
    COALESCE(icd11_level3_name, ''),
    drug_name,
    biomarker_name
  )) AS aggregated_paths,
  SUM(CASE WHEN literature_count > 0 THEN literature_count ELSE 1 END) AS total_weight
FROM icd11_sankey_paths
WHERE in_sankey = TRUE;
```

验收标准：

- API `stats.mappingRows` 等于 `included_rows`。
- API `stats.relations` 等于 `aggregated_paths`。
- API `stats.totalWeight` 等于 `total_weight`。
- Level2 正式终止路径不补造 Level3；Level3 路径仍完整显示。
- ICD11 层级、映射、`in_sankey` 纳入条件和 `literature_count` 权重口径与上线前基线一致。

2026-07-23 本地数据只读核验基线：纳入 778 行、10 个目标类别、聚合 441 条路径。

## 2. 生产路由和 API 路径

```sh
curl -fsS https://YOUR_HOST/icd11-sankey \
  | grep -F '<div id="app"></div>'
curl -fsS -D /tmp/icd11-categories.headers \
  -o /tmp/icd11-categories.json \
  https://YOUR_HOST/api/icd11-sankey/categories
curl -fsS --compressed -D /tmp/icd11-graph.headers \
  -o /tmp/icd11-graph.json \
  'https://YOUR_HOST/api/icd11-sankey/graph-v2?category=ALL'
```

验收标准：

- 直接打开及刷新 `/icd11-sankey` 都返回 Vue 应用，不出现 Nginx 404。
- 浏览器请求保持同源 `/api/icd11-sankey/**`，生产包中不包含内网主机名或 IP。
- 分类和图接口返回 200，经过 Nginx 时大响应启用 gzip。

## 3. 查询、响应和缓存

首次请求后保存 ETag 并复验：

```sh
ETAG="$(sed -n 's/^[Ee][Tt]ag: *//p' /tmp/icd11-graph.headers | tr -d '\r')"
curl -sS -o /dev/null -D - \
  -H "If-None-Match: ${ETAG}" \
  'https://YOUR_HOST/api/icd11-sankey/graph-v2?category=ALL'
```

验收标准：

- 第二次请求返回 304；`Cache-Control` 包含 `public`、`no-cache` 和 `must-revalidate`。
- 服务端热缓存请求不重复查询数据库。
- 分类列表缓存 10 分钟；图缓存 10 分钟且最多保留 32 个类别图。
- 路径详情由同一份聚合 paths 的浏览器索引读取，点击节点、边和路径时没有额外详情接口或 N+1 请求。
- 当前 441 条聚合路径的本地基线约为 790 KB 原始 JSON、73 KB gzip；若生产响应显著增大，应先核查数据量与字段扩张再放行。

## 4. 数据同步后的失效

1. 请求 categories 和 graph-v2，记录 ETag、分类、关系数和总权重。
2. 通过 `/api/data-uploads/{uploadId}/sync` 完成一个已审核批次同步。
3. 同步接口成功返回后再次请求 categories 和 graph-v2。
4. 确认 ETag 已变化，分类、关系数或权重按同步内容更新。
5. 制造一次同步失败，确认旧缓存仍可正常读取且没有发布半成品数据。

## 5. 前端状态和交互

- 正常网络：加载中提示出现，成功后消失，筛选控件恢复可用。
- 超时：分类请求超过 10 秒或图请求超过 25 秒时显示“请求超时”和重新加载按钮。
- 空分类：预发布空表返回明确的“暂无可用分类数据”，不初始化空 ECharts。
- 空图：存在分类但无纳入路径时显示“当前分类没有可展示路径”。
- 接口 4xx/5xx、断网：显示失败信息和重试按钮；页面无未处理 Promise 错误。
- 搜索、显示模式、最小权重、Level1 范围、节点/边锁定、详情饼图和 PNG 导出均可用。

## 6. 大数据量和浏览器内存

1. Chrome DevTools Performance/Memory 中打开全量图。
2. 连续切换全量、Top 20、Top 50、Top 100 和 Level1 范围至少 20 轮。
3. 连续悬浮/点击不同节点和边至少 200 次，打开和关闭详情饼图。
4. 手动触发 GC，比较操作前后的 JS heap 和 DOM/Canvas 数量。

验收标准：

- 主图仅保留一个 ECharts Canvas；关闭的详情饼图实例已释放。
- JS heap 不随重复操作持续线性增长，GC 后回落到稳定区间。
- 主图 devicePixelRatio 不超过 1.5，高密度图高度不超过 4200 px。
- 筛选图缓存最多 24 份、悬浮高亮图缓存最多 12 份，不随交互无限增长。
- 全量图可滚动、可操作，主线程无持续长任务或浏览器标签崩溃。

## 7. 自动化和发布命令

```sh
cd wbe-ui
npm run test:unit -- --run
npm run type-check
npm run build

cd ../wbe-backup
./mvnw test
```

四条命令全部成功后，再执行预发布部署、以上接口/路由 smoke test 和人工数据口径抽样。
