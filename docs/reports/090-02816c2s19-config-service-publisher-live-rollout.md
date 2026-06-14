# 090 028-16-C2-S19 config-service publisher live rollout 报告

状态：完成

日期：2026-06-09

关联任务：

- `tasks/done/028-16-c2-s19-config-service-change-publisher-live-rollout.md`
- `tasks/in-progress/028-16-c2-s17-real-live-rabbit-config-event-smoke.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`

## 结论

C2-S19 已完成。config-service 的 `SystemConfigChangePublisher` 已构建进 dev 镜像并在
`ircs-dev` rollout；Rabbit 连接参数已修正为 dev Rabbit 实际用户 `admin`；重跑 C2-S17
真实 smoke 后，临时 SystemConfig create 和 restore delete 均观察到 Rabbit publish /
deliver / ack，且 `q.content.config_changed` 保持 `consumers=1`、`messages=0`。

C2-S17 仍保留一个非事件链路阻塞：content-service live deployment 存在
`APP_CONTENT_SCRAPER_BASE_URL` 运行时覆盖，所以 DB fallback read probe 不能直接观察临时 DB
值。该项不影响本轮 Rabbit event publish/consume/ack 验收，但后续如需证明 read-through
行为，需要单独临时移除 override 或增加只读诊断端点。

## 变更

- `deploy/k8s/dev/config-service-dev.yaml`
  - 已确认包含 `SPRING_RABBITMQ_HOST`、`SPRING_RABBITMQ_PASSWORD`。
  - 将 `SPRING_RABBITMQ_USERNAME` 从错误的 `ircs` 改为 dev Rabbit 实际用户 `admin`。
  - 保持 `replicas=1`、`ClusterIP`、`requests cpu=25m/memory=128Mi`、`limits cpu=250m/memory=512Mi`。
- `docs/requirements/028-16-c2-s19-config-service-change-publisher-live-rollout.md`
- `tasks/done/028-16-c2-s19-config-service-change-publisher-live-rollout.md`
- `tasks/in-progress/028-16-c2-s17-real-live-rabbit-config-event-smoke.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`

未修改 content-service listener 代码，未新增 HTTPRoute、Service 暴露或临时 Secret。

## 命令与证据

本地测试：

```text
.\gradlew.bat --no-daemon :shared:ircs-messaging:test :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL
```

K8S dry-run：

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\config-service-dev.yaml
deployment.apps/ircs-config-service configured (server dry run)
service/ircs-config-service unchanged (server dry run)
```

镜像推送：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\docker\push-service-image-jib.ps1 -Service ircs-config-service -Tag dev -Architecture arm64 -Os linux
BUILD SUCCESSFUL
Pushed harbor.mnnu.eu.org/ircs/ircs-config-service:dev for linux/arm64
```

Jib digest：

```text
image=harbor.mnnu.eu.org/ircs/ircs-config-service:dev
imageId=sha256:3f599053af0efe8b64367692cc7285a8247dd70d6c2a2236656b13680bca82a3
imageDigest=sha256:de6cc3f758ca62ff42bea22b422b2c570e488ac341da7380effdc9ce93472f05
```

rollout：

```text
kubectl apply -f deploy\k8s\dev\config-service-dev.yaml
deployment.apps/ircs-config-service configured
service/ircs-config-service unchanged

kubectl -n ircs-dev rollout status deployment/ircs-config-service --timeout=180s
deployment "ircs-config-service" successfully rolled out
```

live pod：

```text
pod=ircs-config-service-658d7dd578-xs6tb
ready=true
restartCount=0
created=2026-06-09T10:37:41Z
imageID=harbor.mnnu.eu.org/ircs/ircs-config-service@sha256:de6cc3f758ca62ff42bea22b422b2c570e488ac341da7380effdc9ce93472f05
```

env 与 class：

```text
SPRING_RABBITMQ_HOST from configmap ircs-dev-app-config, key RABBITMQ_HOST
SPRING_RABBITMQ_USERNAME=admin
SPRING_RABBITMQ_PASSWORD from secret ircs-dev-secrets, key RABBITMQ_PASSWORD

unzip -l /app/classpath/ircs-config-service-0.1.0-SNAPSHOT-plain.jar
com/prodigalgal/ircs/config/SystemConfigChangePublisher.class
```

config-service Rabbit 连接日志：

```text
Created new connection: rabbitConnectionFactory#1ef93e01:0/SimpleConnection@35040f0a [delegate=amqp://admin@10.101.99.87:5672/, localPort=52104]
```

第一次 smoke 失败根因：

```text
SPRING_RABBITMQ_USERNAME=ircs
AmqpAuthenticationException: ACCESS_REFUSED - Login was refused using authentication mechanism PLAIN.
```

修复后 C2-S17 smoke：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\config-event-content-live-02816c2-s17-smoke.ps1 -ConfirmLiveRabbit
MARK 1/7 kubectl can access target namespace
MARK 2/7 config-service and content-service deployments are ready
MARK 3/7 Rabbit content config queue is present with a consumer
MARK 4/7 original config-service value captured before write
MARK 5/7 config-service wrote temporary non-sensitive value
MARK 6/7 Rabbit publish and content consumer ack observed for temporary update
MARK 7/7 content-service read probe was not fully observable in current live environment
status=PARTIAL
reason=CONTENT_READ_BLOCKED_BY_RUNTIME_OVERRIDE
```

Rabbit update evidence：

```text
temporary-update:
before publish=0 deliver=0 deliverGet=0 ack=0 messages=0 consumers=1
after  publish=1 deliver=1 deliverGet=1 ack=1 messages=0 consumers=1
publishDelta=1
deliverDelta=2
ackDelta=1
```

Rabbit restore evidence：

```text
restore-original:
before publish=1 deliver=1 deliverGet=1 ack=1 messages=0 consumers=1
after  publish=2 deliver=2 deliverGet=2 ack=2 messages=0 consumers=1
publishDelta=1
deliverDelta=2
ackDelta=1
deleteStatus=204
verifiedOriginalState=true
```

队列收尾：

```text
q.content.config_changed      0 0 0 1
q.content.config_changed.dlq  0 0 0 0
```

DB 清理：

```text
target_rows=0
temp_rows=0
```

port-forward 残留：

```text
Get-CimInstance Win32_Process ... kubectl.exe port-forward ... 19087/19088/19089
无输出，未发现本轮 port-forward 残留。
```

## 遗留风险

- C2-S17 的 content read probe 仍受 `APP_CONTENT_SCRAPER_BASE_URL` runtime override 阻塞，不能直接观察
  DB fallback 临时值；如要补齐该读证据，需要独立任务临时调整 deployment 或增加只读诊断端点。
- Rabbit outage / missed-message 下是否需要短 TTL fallback 仍未决，继续留在
  `028-16-C2 SystemConfig cache/event` 父任务。
