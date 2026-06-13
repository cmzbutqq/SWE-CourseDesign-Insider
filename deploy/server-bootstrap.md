# Server Bootstrap

这份文档的目标只有一个：把这台服务器配置成可以稳定执行本项目的自动部署。

完成后应满足这 4 条：

1. 服务器上存在仓库目录 `/opt/scut-monitoring`
2. 服务器上手动执行 `bash deploy/deploy-main.sh` 会成功
3. GitHub Actions 可以通过 SSH 登录服务器并执行同一个脚本
4. 前端可以通过 `http://服务器公网IP/` 直接访问

## 先读这 6 条

1. `[本机]` 表示在你自己的电脑上执行，不是在云服务器上执行
2. `[服务器]` 表示先 SSH 登录云服务器，再执行；除非命令里写了 `sudo -u deploy`，否则默认以 `root` 身份执行
3. `[GitHub 页面]` 表示在 GitHub 网页上点选配置，不是在终端里执行
4. 文中所有形如 `REPLACE_WITH_...` 的内容都必须替换成真实值后再执行，不能原样照抄
5. 这套流程里有两把完全不同的 SSH key，绝对不要混用
6. 在确认新 SSH 登录方式可用之前，不要关闭你当前的 root 会话

## 两把 SSH key 的用途

### Key A: `本机 -> 服务器`

- 作用：让 GitHub Actions 登录你的服务器
- 生成位置：你自己的电脑
- 私钥保存位置：你自己的电脑，例如 `~/.ssh/scut-monitoring-actions`
- 公钥保存位置：服务器的 `/home/deploy/.ssh/authorized_keys`
- GitHub Secret：`DEPLOY_SSH_KEY`

### Key B: `服务器 -> GitHub`

- 作用：让服务器以只读方式从 GitHub 拉代码
- 生成位置：服务器
- 私钥保存位置：服务器的 `/home/deploy/.ssh/id_ed25519_github`
- 公钥保存位置：GitHub 仓库的 `Settings -> Deploy keys`
- 不能用于登录服务器

如果你把 Key A 和 Key B 搞反，会直接导致部署失败。

## 0. 先准备这 2 个真实值

后面会反复用到，请先确定：

- `SERVER_IP`：服务器公网 IP
- `GRAFANA_PASSWORD`：你自己设定的 Grafana 管理员密码

## 1. [服务器] 安装或确认 Docker

如果服务器上还没有 Docker，先执行：

```bash
apt update
apt install -y ca-certificates curl git
curl -fsSL https://get.docker.com | sh
```

无论你是刚装完 Docker，还是机器上本来就有 Docker，后面都要继续执行下面这几句：

```bash
systemctl enable --now docker
docker --version
docker compose version
```

成功标志：

- `docker --version` 有输出
- `docker compose version` 有输出

如果 `docker compose version` 报错或提示找不到命令，说明当前机器没有可用的 Compose 插件；先补齐 Docker/Compose，再继续后面的步骤。

## 2. [服务器] 创建部署用户

```bash
adduser --disabled-password --gecos "" deploy
usermod -aG docker deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
id deploy
```

成功标志：

- `id deploy` 有输出
- 输出里包含 `deploy`

## 3. [本机] 生成 Key A，用于 `本机 -> 服务器`

```bash
ssh-keygen -t ed25519 -f ~/.ssh/scut-monitoring-actions -C github-actions-deploy -N ""
cat ~/.ssh/scut-monitoring-actions.pub
```

成功标志：

- 本机生成了两个文件：
  - `~/.ssh/scut-monitoring-actions`
  - `~/.ssh/scut-monitoring-actions.pub`
- `cat ~/.ssh/scut-monitoring-actions.pub` 会输出一整行以 `ssh-ed25519` 开头的公钥

记住：

- 这一步在你自己的电脑上执行
- 后面 GitHub Secret `DEPLOY_SSH_KEY` 要填的是 `~/.ssh/scut-monitoring-actions`
- 不是 `.pub`
- 也不是服务器上的 `id_ed25519_github`

## 4. [服务器] 把 Key A 的公钥写入 `authorized_keys`

先在本机把上一步输出的整行公钥复制出来。然后在服务器执行：

```bash
touch /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys
printf '%s\n' 'REPLACE_WITH_THE_FULL_SINGLE_LINE_CONTENT_OF_scut-monitoring-actions.pub' >> /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys
```

注意：

- 把单引号里的整行内容替换成你本机 `~/.ssh/scut-monitoring-actions.pub` 的完整输出
- 单引号保留
- `REPLACE_WITH_...` 这串字不能原样保留

然后立刻在本机测试 Key A 是否能登录服务器：

```bash
ssh -i ~/.ssh/scut-monitoring-actions deploy@REPLACE_WITH_SERVER_IP 'whoami'
```

成功标志：

- 命令输出 `deploy`

如果这一步失败，不要继续后面的步骤，先修好这里。

## 5. [服务器] 生成 Key B，用于 `服务器 -> GitHub`

```bash
sudo -u deploy ssh-keygen -t ed25519 -f /home/deploy/.ssh/id_ed25519_github -N ""
cat /home/deploy/.ssh/id_ed25519_github.pub
```

成功标志：

- 服务器上生成了两个文件：
  - `/home/deploy/.ssh/id_ed25519_github`
  - `/home/deploy/.ssh/id_ed25519_github.pub`
- `cat` 会输出一整行以 `ssh-ed25519` 开头的公钥

记住：

- 这把 key 只给服务器拉 GitHub 代码用
- 不能拿它去登录服务器
- 不能把它填到 GitHub Secret `DEPLOY_SSH_KEY`

## 6. [GitHub 页面] 把 Key B 公钥加到仓库 Deploy Keys

打开仓库页面：

- `Settings`
- `Deploy keys`
- `Add deploy key`

填写方式：

- `Title`：随便起名，例如 `deploy@server`
- `Key`：粘贴服务器 `/home/deploy/.ssh/id_ed25519_github.pub` 的完整输出
- `Allow write access`：不要勾选，保持只读

保存后再回到服务器继续下一步。

## 7. [服务器] 配置 `deploy` 用户访问 GitHub，并克隆仓库

先执行：

```bash
mkdir -p /opt/scut-monitoring
chown -R deploy:deploy /opt/scut-monitoring

cat >/home/deploy/.ssh/config <<'EOF'
Host github.com
  HostName github.com
  User git
  IdentityFile /home/deploy/.ssh/id_ed25519_github
  IdentitiesOnly yes
EOF

chown deploy:deploy /home/deploy/.ssh/config
chmod 600 /home/deploy/.ssh/config
```

然后先测试 `deploy` 用户能不能连上 GitHub：

```bash
sudo -u deploy ssh -T git@github.com
```

成功标志：

- 输出里包含 `successfully authenticated`
- 同时出现 `GitHub does not provide shell access` 也属于正常现象

如果这里出现 `Permission denied (publickey)`，说明第 6 步的 Deploy Key 还没配对成功，不要继续克隆。

确认测试通过后，再克隆仓库：

```bash
sudo -u deploy git clone git@github.com:cmzbutqq/SWE-CourseDesign-Insider.git /opt/scut-monitoring
test -d /opt/scut-monitoring/.git
```

成功标志：

- `test -d /opt/scut-monitoring/.git` 不报错

## 8. [服务器] 从模板创建 `.env`，只修改服务器相关字段

```bash
cd /opt/scut-monitoring
cp .env.example .env
```

如果这台服务器已经有别的程序占用了宿主机 `80` 端口，例如 nginx、Caddy、Apache 或其他容器，请先停掉那个程序，或者明确决定不用 `80`。这份文档默认目标是公网直接访问 `http://服务器公网IP/`，所以这里使用 `FRONTEND_PORT=80`。

下面这组命令会直接把 `.env` 里的关键字段改成服务器部署需要的值。执行前，先把两行变量里的占位内容改成真实值：

```bash
SERVER_IP='REPLACE_WITH_SERVER_PUBLIC_IP'
GRAFANA_PASSWORD='REPLACE_WITH_A_STRONG_PASSWORD'

test "$SERVER_IP" != 'REPLACE_WITH_SERVER_PUBLIC_IP'
test "$GRAFANA_PASSWORD" != 'REPLACE_WITH_A_STRONG_PASSWORD'

sed -i "s/^PUBLIC_SCHEME=.*/PUBLIC_SCHEME=http/" .env
sed -i "s/^PUBLIC_HOST=.*/PUBLIC_HOST=${SERVER_IP}/" .env
sed -i "s/^FRONTEND_PORT=.*/FRONTEND_PORT=80/" .env
sed -i "s/^GRAFANA_ADMIN_PASSWORD=.*/GRAFANA_ADMIN_PASSWORD=${GRAFANA_PASSWORD}/" .env

grep -E '^(PUBLIC_SCHEME|PUBLIC_HOST|FRONTEND_PORT|GRAFANA_ADMIN_PASSWORD)=' .env
```

成功标志：

- `test` 两句都不报错
- 最后一条 `grep` 输出这 4 行：
  - `PUBLIC_SCHEME=http`
  - `PUBLIC_HOST=你的服务器公网IP`
  - `FRONTEND_PORT=80`
  - `GRAFANA_ADMIN_PASSWORD=你设置的密码`

说明：

- 这里是“修改复制出来的 `.env`”，不是把 `.env` 改成只剩 4 行
- `.env` 里其他默认配置保留不动

## 9. [服务器] 手动跑一次部署脚本

首次部署前，先手动执行一次。这里要故意用 `deploy` 用户执行，和 GitHub Actions 未来的执行身份保持一致：

```bash
chown -R deploy:deploy /opt/scut-monitoring
sudo -u deploy test -w /opt/scut-monitoring/.env
sudo -u deploy -H bash -lc 'cd /opt/scut-monitoring && bash deploy/deploy-main.sh'
```

说明：

- 首次运行可能持续几分钟
- 期间会构建并启动多个容器

成功标志：

- 脚本最后输出 `Deploy succeeded at commit ...`

如果脚本失败，不要继续配置 GitHub Actions，先把这个问题解决。

手动部署成功后，可以在服务器上再做这 2 个快速校验：

```bash
curl -fsS http://localhost
curl -fsS http://localhost:18081/api/overview
```

成功标志：

- 第一条能返回前端 HTML
- 第二条能返回后端 JSON

## 10. [本机] 再次验证 Key A 登录服务器

在禁用密码登录前，再从本机测试一次：

```bash
ssh -i ~/.ssh/scut-monitoring-actions deploy@REPLACE_WITH_SERVER_IP 'docker --version'
```

成功标志：

- 命令能执行成功
- 输出里包含 Docker 版本

如果这一步失败，不要禁用密码登录。

## 11. [服务器] 禁用密码登录并限制 root

这一段在服务器当前的 root 会话里执行。执行后不要立刻退出，先用新的终端测试 `deploy` 是否还能正常登录。

```bash
cp /etc/ssh/sshd_config /etc/ssh/sshd_config.bak.$(date +%F-%H%M%S)
sed -i 's/^#\\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sed -i 's/^#\\?PubkeyAuthentication.*/PubkeyAuthentication yes/' /etc/ssh/sshd_config
sed -i 's/^#\\?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
systemctl reload ssh
```

然后立刻在本机新开一个终端，测试：

```bash
ssh -i ~/.ssh/scut-monitoring-actions deploy@REPLACE_WITH_SERVER_IP 'whoami'
```

成功标志：

- 输出 `deploy`

如果这一步失败：

- 不要关闭你当前已经登录着的 root 会话
- 先用备份把 `sshd_config` 恢复回去，再 `systemctl reload ssh`

## 12. [GitHub 页面] 添加 Actions Secrets

打开仓库页面：

- `Settings`
- `Secrets and variables`
- `Actions`

添加这 4 个 Secret：

- `DEPLOY_HOST`：服务器公网 IP，例如 `103.236.55.76`
- `DEPLOY_PORT`：`22`
- `DEPLOY_USER`：`deploy`
- `DEPLOY_SSH_KEY`：本机文件 `~/.ssh/scut-monitoring-actions` 的完整私钥内容

`DEPLOY_SSH_KEY` 的正确取值方式是在本机执行：

```bash
cat ~/.ssh/scut-monitoring-actions
```

注意：

- 填的是这个文件的内容
- 不是 `~/.ssh/scut-monitoring-actions.pub`
- 不是服务器上的 `/home/deploy/.ssh/id_ed25519_github`

## 13. [GitHub 页面] 触发一次自动部署

有两种方式：

1. 推送新的 commit 到 `main`
2. 打开 `Actions -> Deploy Main -> Run workflow` 手动触发

成功标志：

- GitHub Actions 里的 `Deploy Main` 工作流变成绿色
- 服务器上仓库已经更新到最新 `main`

如果你想在服务器上确认当前部署 commit，可以执行：

```bash
cd /opt/scut-monitoring
git rev-parse --short HEAD
```

## 14. 公网访问地址

按这份文档执行完后，默认访问地址应为：

- 前端：首页 `http://服务器公网IP/`
- 后端概览接口：`http://服务器公网IP:18081/api/overview`
- Grafana：`http://服务器公网IP:13000`
- Prometheus：`http://服务器公网IP:19090`
- SkyWalking UI：`http://服务器公网IP:18082`

## 15. 如果你启用了防火墙

这份项目代码不会替你改云厂商控制台里的安全组或防火墙规则。

如果你的云平台或系统防火墙默认拦截入站流量，你还需要额外放行这些 TCP 端口：

- `22`
- `80`
- `18081`
- `13000`
- `19090`
- `18082`

如果不放行这些端口，容器虽然可能已经启动成功，但公网访问仍然会失败。
