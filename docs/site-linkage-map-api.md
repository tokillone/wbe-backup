# 地图点位关联与 QC

## 统计口径

- 点位身份固定为 `reported:<reported_site_key>`。
- 同一文献内根据文献编号、国家、省/州、市和原始点位标识关联。
- `confirmed_site_id` 仅作为人工核查候选，当前版本不执行跨文献自动合并。
- 多点位记录为每个关联点位增加覆盖，浓度、PNDL 等指标记录仍只计算一次。
- 国家、省/州、市三级都从当前筛选后的原始记录重新去重，不对子区域点位数求和。

## 7.22 基线

| 指标 | 数量 |
| --- | ---: |
| 点位关联表行 | 4,328 |
| 计入点位 | 3,820 |
| 不计入点位 | 508 |
| 已映射点位 | 3,813 |
| 未映射点位 | 7 |
| 数据记录 | 22,738 |
| 精确匹配记录 | 21,410 |
| 多点位记录 | 898 |
| 地区回退匹配 | 92 |
| 排除记录 | 152 |
| 未匹配国家记录 | 186 |
| 中国全部筛选点位 | 2,700 |

未映射点位为 `WBE0023-S001` 至 `WBE0023-S006` 以及 `WBE0121-S001`。这些行缺少可用的国家/省州/城市定位，保留在 QC 中等待人工补齐。

## 接口

### 区域统计

```http
GET /api/wbe-map/regions?targetClass=ALL&category=全部目标物质类别&subcategory=全部小类&biomarkerKey=ALL&year=全部年份&levels=country,admin1,city
```

响应区域行保留 `pointCount`，并附带：

- `pointCountBasis: "reported_site_key"`
- `crossDocumentMergeEnabled: false`
- `pointGeometryBasis: "region_centroid"`

### 区域点位明细

```http
GET /api/wbe-map/regions/china/sites?level=country&targetClass=ALL&category=全部目标物质类别&subcategory=全部小类&biomarkerKey=ALL&year=全部年份
```

返回当前区域与筛选条件下的 `reportedSiteKey`、文献、原始/规范名称、候选 `confirmedSiteId`、匹配状态和覆盖记录数。

### 点位关联 QC

```http
GET /api/wbe-map/site-link-qc
```

返回最新一次导入基线、各匹配状态数量和未映射点位明细。
