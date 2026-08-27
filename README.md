# MCDP Demo Web

这是一个可直接共享给 Web/H5 开发人员的 mPaaS MCDP WebSDK 集成示例。业务代码只有静态 HTML、CSS、JavaScript 和 MCDP WebSDK，不依赖 npm、Vite，也不需要前端构建。

## 项目边界

- `public/`：可直接部署到任意静态 Web Server 的页面与 SDK。
- `server.mjs`：可选的零依赖 Node 本地服务器，仅用于调试。
- `Dockerfile`、`nginx/`：生产静态镜像，不包含 MGS/MAS 代理逻辑。
- `k8s/`：独立的 `mcdp-demo-web` Deployment、Service、Ingress。
- `docs/`：MCDP WebSDK 原始接入说明。

浏览器默认请求当前域名下的：

```text
/mpaas-proxy/mgw.htm
/mpaas-proxy/mdap/*
```

App Secret 不会写入页面或 Web 镜像。MGS 加签及 PrivateLink 访问由独立的 `mpaas-proxy` 服务负责。

## Windows 本地运行

只要求机器已有 Node.js，不需要执行 `npm install`：

PowerShell：

```powershell
$env:MPAAS_PROXY_URL="http://10.23.34.52:443/mpaas-proxy"
node server.mjs
```

CMD：

```bat
set MPAAS_PROXY_URL=http://10.23.34.52:443/mpaas-proxy
node server.mjs
```

浏览器打开：

```text
http://localhost:5179/mcdp_demo/
```

本地服务器会把浏览器的同源 `/mpaas-proxy` 请求转发到 ACS，不会直接访问 MGS/MAS，也不需要在浏览器中处理 CORS 或 PrivateLink 证书。

### localhost 直接调用 ACS ALB

测试浏览器跨域直连时，以端口 `3517` 启动：

```bash
PORT=3517 node server.mjs
```

浏览器打开：

```text
http://localhost:3517/mcdp_demo/
```

当页面是 `localhost:3517` 或 `127.0.0.1:3517` 时，默认 Endpoint 为
`ACS ALB direct (HTTP/443)`，请求地址是：

```text
http://10.23.34.52:443/mpaas-proxy
```

该模式由浏览器直接跨域请求 ALB，不经过 `server.mjs` 的同源反向代理。
其他 hostname 仍默认使用当前站点的 `/mpaas-proxy`。

## 修改集成参数

默认 App ID、Workspace ID、Tenant ID、展位码以及 MGS/MAS 预设均定义在：

```text
public/index.html
```

正式集成时需要保留：

```html
<script src="./vendor/mpaas-mcdp-h5-render/index.js"></script>
```

并调用：

```js
McdpView.init({
  appId: "your-app-id",
  workspaceId: "your-workspace-id",
  tenantId: "your-tenant-id",
  reportURL: `${window.location.origin}/mpaas-proxy`,
  uploadURL: `${window.location.origin}/mpaas-proxy/mdap`
});
```

当前页面按《展位介绍》实现 Cash in Hand 客户端场景：

- MCDP 展位名称：`CIH_Offer`
- MCDP 展位码：`offersCIH`
- 页面位置：Cash in Hand Programme 顶部
- CMS 素材类型：`信用卡个人横幅`
- 默认文案：`Cash out now and enjoy up to HK$10,000 cash rebate. T&C apply.`

展位容器使用 `data-mcdp-code="offersCIH"`。页面先显示带 `Demo preview` 标记的本地占位，MCDP 返回由 IMK 活动关联的 CMS 素材后，会替换为 `Live MCDP` 内容。代理或 MCDP 暂时不可达时，本地占位会保留，便于区分页面故障和投放链路故障。

Demo 的浏览器配置按 `App ID + Workspace ID` 使用独立的 localStorage 键，旧环境保存过的配置不会覆盖当前 UAT 默认值。切换配置后也可以点击页面里的“恢复默认”立即回到当前 UAT 环境。

需要打开页面后直接查询并展示展位内容时，后台应创建“主动营销活动”并关联展位与素材。“互动营销活动”只有在 MAS 记录到所配置的用户行为事件后才会命中，不适合作为这个页面直出的基础活动。

## 构建与发布镜像

```bash
IMAGE_TAG=20260826080830 bash scripts/build-image.sh
IMAGE_TAG=20260826080830 bash scripts/publish-acr.sh
```

发布镜像：

```text
bcm-dev-registry-vpc.cn-hongkong.cr.aliyuncs.com/bcm-dev/mcdp-demo-web:20260826080830
```

## 部署 ACS

能传文件时：

```bash
kubectl apply -f k8s/cms-dev.yaml
```

不能传文件时，在 Mac 生成一条可粘贴到 ACS 终端的命令：

```bash
bash scripts/print-cms-dev-kubectl-command.sh | pbcopy
```

部署后访问：

```text
http://10.23.34.52:443/mcdp_demo/
```

CMS 模型、三语素材、IMK 展位映射和 ACS 更新命令见：

```text
../../../deploy/acs/OFFERS-CIH-DEMO.md
```
