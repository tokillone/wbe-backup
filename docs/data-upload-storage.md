# 数据上传文件存储与备份

## 配置

上传根目录由 `wbe.storage.upload-dir` 控制，默认值仍为开发环境原目录：

```yaml
wbe:
  storage:
    upload-dir: ${WBE_UPLOAD_DIR:${user.dir}/uploads/data}
    max-file-size: 50MB
```

Docker 环境统一设置：

```text
WBE_UPLOAD_DIR=/data/wbe/uploads
```

目录必须预先规划为持久化存储，不应位于容器可写层。容器运行用户必须对目录拥有创建、写入、原子重命名和读取权限。

## Docker 卷挂载

本任务不修改 Docker Compose。部署配置应等价于：

```yaml
services:
  wbe-backup:
    environment:
      WBE_UPLOAD_DIR: /data/wbe/uploads
    volumes:
      - wbe_uploads:/data/wbe/uploads

volumes:
  wbe_uploads:
```

也可使用宿主机目录绑定：

```text
/srv/wbe/uploads:/data/wbe/uploads
```

临时文件与最终文件必须位于同一个挂载目录，才能优先使用同文件系统原子移动。不要只挂载其父目录中的其他子目录。

## 备份与恢复要求

- 数据库 `data_upload_batches.stored_file_path` 与上传目录必须作为一个备份单元；只备份数据库或只备份文件都不能完整恢复原文件下载。
- 不迁移、不重写已有 `stored_file_path`。恢复时应把卷恢复到备份时相同的绝对路径；Docker 环境即 `/data/wbe/uploads`。
- 建议使用存储快照，或在短暂停止上传后先取得数据库一致性备份，再立即备份上传卷，并记录两者共同的备份批次和时间点。
- 上传文件按 UUID 命名，备份工具不得改名、扁平化目录或按扩展名去重。
- 备份应包含隐藏的 `.wbe-upload-*.tmp` 文件，但恢复后可在确认没有正在执行的上传任务时清理长期遗留临时文件；不要自动删除数据库已引用的 `.xlsx` 文件。
- 至少定期演练：恢复数据库和卷、启动服务、抽样下载历史批次、核对 SHA-256、验证新上传和事务回滚清理。
- 对卷配置容量监控、只允许应用账户写入，并保留满足业务审计周期的版本化或不可变备份。
