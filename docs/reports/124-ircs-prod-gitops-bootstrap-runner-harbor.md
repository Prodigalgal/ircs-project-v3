# 124 ircs-prod GitOps bootstrap runner Harbor evidence

状态：完成当前切片

日期：2026-06-14

## 结果

已创建新的 Gitea 代码仓库并完成 runner -> Jib -> Harbor 自动构建推送链路。

```text
repo: https://gitea.mnnu.eu.org/gitea-admin/ircs-project-v3
branch: main
workflow: .gitea/workflows/build-and-push.yml
successful run: #6
commit: a9a813b160df7c638604bfbefa26dcd88666b876
image tag: sha-a9a813b160df
runner label: ircs
target platform: linux/arm64
```

## 修复过程

- 初始 `runs-on: ubuntu-latest` 与现有 runner label 不匹配，改为 `runs-on: ircs`。
- `gradlew` 缺少 executable bit，补 `chmod +x gradlew` 并提交 git mode `100755`。
- Gitea 1.24 不支持 `actions/upload-artifact@v4`，改为直接打印 `build-image-manifest.txt`。
- 新增 path filter，避免文档-only push 触发全量镜像构建。

## Harbor 核验证据

Harbor project：`ircs`

| Image | Tag | Digest |
| --- | --- | --- |
| `ircs-migrator` | `sha-a9a813b160df` | `sha256:64223fa99f7c2793b0145cc539bafa4b0c70fa3cc0af0e9059a2fed3bf7a2437` |
| `ircs-admin-bff` | `sha-a9a813b160df` | `sha256:e0314abeba097d5f2a25809bf4c7691cdb720b524841e94c522a3f7270baf899` |
| `ircs-aggregation-worker` | `sha-a9a813b160df` | `sha256:154db2f8da961cb300308f7d06337edc0ab11a3784a3fa432d23bf9cb2460e87` |
| `ircs-catalog-service` | `sha-a9a813b160df` | `sha256:c9c4aed0785e8d9716268b1b9f8f34d7bcd66ec5e3f1389c206185f72fa765e3` |
| `ircs-config-service` | `sha-a9a813b160df` | `sha256:b19eeefe3dbac9483dbb01589afb79e89e43b702619186e6bb7ba988f1e7069e` |
| `ircs-content-service` | `sha-a9a813b160df` | `sha256:414245458ad96793c17e88a74c1aa702c8aa593331d64d217d53fa6234bcb617` |
| `ircs-credential-service` | `sha-a9a813b160df` | `sha256:327c7307de0547d7d320010cffcee2f098cddb4ade6a94d8020c864809078eda` |
| `ircs-identity-service` | `sha-a9a813b160df` | `sha256:adda80c8a9c0986a465da376eb527096e3c8ed0cb60fc914cf0df3968533ccb0` |
| `ircs-ingestion-worker` | `sha-a9a813b160df` | `sha256:7429f763a0c07e767018ea473836ff16546f4d91085f0e5c6c28af31f25093fc` |
| `ircs-interaction-service` | `sha-a9a813b160df` | `sha256:540f6edb8357fb30208a5fa9de957e25506dca9c49a66ff16833438d6eab3ac7` |
| `ircs-magnet-service` | `sha-a9a813b160df` | `sha256:190b98486dd4fd662d9433e1032a41c768d6b4cc4c11aeae91f1f7142ebf09b5` |
| `ircs-metadata-worker` | `sha-a9a813b160df` | `sha256:556d9e0e90c3686974d42f9844d46c46684ce9e8c44cea8c3af653cbebdde5db` |
| `ircs-normalization-worker` | `sha-a9a813b160df` | `sha256:399ca4e645c20a8fef850499a5b1c4223fc6308ca36598f778641e1efbb8eaf8` |
| `ircs-notification-worker` | `sha-a9a813b160df` | `sha256:c0234ec40b13b3a24925ec2bcfb5b6298622335f44284003d12f09199de3fdd3` |
| `ircs-ops-service` | `sha-a9a813b160df` | `sha256:ec5a8ec36286e45fa4d85e07b861c7e349dd914017e1b0a03639833ab20b686f` |
| `ircs-portal-bff` | `sha-a9a813b160df` | `sha256:713932a12c577f2dcb910d85f96a5f1c7489542d6cda8a2089efceb3195918d1` |
| `ircs-portal-service` | `sha-a9a813b160df` | `sha256:13273b98a366bf5690fb372316248d91711f41afb4906de0cf8e737ce76fc59c` |
| `ircs-scraper-service` | `sha-a9a813b160df` | `sha256:57d7a63db890dd5be03d3981f428f23a56c46ea367ddcdcebc9d018e873ce457` |
| `ircs-search-service` | `sha-a9a813b160df` | `sha256:b935a800abe3c92320408f3410c1883f6367736117e92dd574edced6bb3434e0` |
| `ircs-storage-service` | `sha-a9a813b160df` | `sha256:d84e61ee19b84a77a0cd1c608eb51cf673b0d8fc47571366742542ff5a00b88c` |
| `ircs-task-service` | `sha-a9a813b160df` | `sha256:74482cc394083003bb3dc52d9290e3473d3f17b1e5bad01ad91123facf31709f` |

## 剩余

- 需要准备 `ircs-prod` GitOps config repo / ArgoCD Application。
- 需要把上述 image tag/digest 写入生产清单。
- 需要准备旧 `ircs-system` 数据兼容迁移 dry-run 和切流回滚方案。

