# 数据上传存储部署与备份要求

## 1. 生产目录约定

- 开发环境默认目录：`${user.dir}/uploads/data`。
- 生产环境必须显式设置环境变量 `WBE_UPLOAD_DIR`。
- Docker 容器内统一使用 `/data/wbe/uploads`。
- 数据库中的 `stored_file_path` 是审核追溯数据。部署和迁移时不得批量改写既有路径，也不得删除既有 `uploads` 文件。

推荐使用宿主机目录挂载：

```bash
docker run \
  -e WBE_UPLOAD_DIR=/data/wbe/uploads \
  -v /srv/wbe/uploads:/data/wbe/uploads \
  ...
```

也可以使用 Docker 命名卷：

```bash
docker volume create wbe_uploads
docker run \
  -e WBE_UPLOAD_DIR=/data/wbe/uploads \
  -v wbe_uploads:/data/wbe/uploads \
  ...
```

本次改造不修改 Docker Compose 文件。后续在 Compose 中配置时，容器内目标目录仍应保持 `/data/wbe/uploads`。

## 2. 目录权限

容器启动前应完成以下检查：

1. 挂载目录已经存在；
2. 运行 Java 进程的 UID/GID 对目录有读取、创建、写入和原子重命名权限；
3. 目录不通过符号链接跳出受控存储根目录；
4. 磁盘剩余空间和 inode 数量有监控；
5. 生产环境没有把上传目录放在容器可写层。

建议在发布检查中实际创建、写入、重命名并删除一个探测文件，而不只检查目录权限位。

## 3. 备份范围

一次可恢复备份必须同时包含：

- MySQL 数据库；
- 上传目录全部文件，包括普通提交、完整整理包和历史遗留文件；
- 当前部署版本号及 `WBE_UPLOAD_DIR` 配置；
- 备份开始时间、结束时间、文件数量、总字节数和校验文件。

数据库和文件备份应处于同一个维护窗口。最稳妥的顺序是：

1. 暂停新上传、审核、终审和同步；
2. 执行 MySQL 一致性备份；
3. 备份上传目录；
4. 生成 SHA-256 校验清单；
5. 恢复业务操作。

示例：

```bash
mysqldump --single-transaction --routines --triggers \
  -h DB_HOST -u DB_USER -p DB_NAME > wbe-db.sql

tar -C /srv/wbe -czf wbe-uploads.tar.gz uploads

sha256sum wbe-db.sql wbe-uploads.tar.gz > SHA256SUMS
```

命令中的数据库地址、账号和备份目录应由运维环境提供，密码不要写入脚本或命令历史。

## 4. 恢复要求

恢复时应先停止写入，再恢复数据库和文件目录。恢复后至少核对：

- `data_upload_batches`、`data_upload_review_packages` 的记录数；
- 两张表中所有非空文件路径都能在允许的存储根目录中解析；
- 对应文件存在、为普通文件且可读；
- 上传目录文件数量和备份清单一致；
- 抽样文件的 SHA-256 与数据库或备份清单一致；
- 一个历史原文件和一个完整整理包可以通过权限校验后下载。

任何路径越界、文件缺失或校验失败都应阻断发布。

## 5. 保留与演练

- 日备份建议保留 7～14 天，周备份保留 4～8 周，月备份按合同和数据合规要求确定。
- 不得只备份数据库；数据库路径记录不能替代原始 Excel 文件。
- 至少每季度在隔离环境做一次完整恢复演练。
- 清理历史文件必须另立数据保留策略和审批流程；当前系统不自动删除任何既有上传文件。
