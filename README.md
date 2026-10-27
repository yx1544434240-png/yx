# 颐邻 · 社区居家养老服务与智能派单系统

本科毕业设计：**《基于 Java 和微信小程序的社区居家养老服务与智能派单系统的设计与实现》**。

本项目包含微信小程序、Web 管理后台和 Java 后端，支持居民预约、家属代约、工作人员履约、管理员审核及智能派单。

**本指南以 Windows 10/11、本地 MySQL、不使用 Docker 为主线。** 请第一次使用时按顺序操作；已经有数据的老用户直接阅读“已有项目如何更新”，不要重新初始化数据库。

> 文档中的 `D:\serviceForTheAged` 是示例项目路径。如果你的项目放在其他地方，请替换为自己的路径。文中的密码占位符需要替换，不能原样输入。所有运行命令均标注了使用位置。

## 目录

1. [先理解需要启动什么](#1-先理解需要启动什么)
2. [安装必备软件](#2-安装必备软件)
3. [下载项目](#3-下载项目)
4. [第一次初始化数据库](#4-第一次初始化数据库)
5. [启动 Java 后端](#5-启动-java-后端)
6. [启动 Web 管理后台](#6-启动-web-管理后台)
7. [配置并运行微信小程序](#7-配置并运行微信小程序)
8. [手机和平板真机连接](#8-手机和平板真机连接)
9. [账号、注册和多角色演示](#9-账号注册和多角色演示)
10. [完成一次完整业务演示](#10-完成一次完整业务演示)
11. [每天如何启动和关闭](#11-每天如何启动和关闭)
12. [已有项目如何更新](#12-已有项目如何更新)
13. [常见报错与处理](#13-常见报错与处理)
14. [测试与构建](#14-测试与构建)
15. [备份和迁移到另一台电脑](#15-备份和迁移到另一台电脑)
16. [项目结构与设计说明](#16-项目结构与设计说明)
17. [运行成功检查表](#17-运行成功检查表)

## 1. 先理解需要启动什么

项目不是双击一个文件就能运行的单机软件。需要让下列部分配合工作：

| 部分 | 通俗解释 | 默认位置或端口 | 如何运行 |
| --- | --- | --- | --- |
| MySQL | 保存账号、订单、排班等信息的数据库 | 电脑上的 `3306` 端口 | Windows 服务 |
| Java 后端 | 处理登录、预约、审核和派单的程序 | `http://localhost:8080/api` | Maven 或 IDEA |
| Web 管理后台 | 管理员在浏览器中操作的网页 | `http://localhost:3000` | Node.js / Vite |
| 微信小程序 | 居民、家属、工作人员使用的界面 | 微信开发者工具或手机微信 | 导入小程序项目 |

运行顺序：**MySQL → Java 后端 → Web 后台 → 微信小程序**。

- `localhost` 表示“当前这台设备自己”。电脑里的 localhost 是电脑，手机里的 localhost 是手机。
- `8080` 是后端端口，`3000` 是后台网页端口，不能混用。
- 小程序接口地址必须包含 `/api`；后台网页直接打开 `/login`。
- 本地运行无需安装 Redis、Nginx、Docker 或 Kubernetes。
- 首次下载依赖和真实微信登录需要互联网；只有局域网连接并不足以完成真实微信登录。

## 2. 安装必备软件

### 2.1 软件清单

| 软件 | 本项目建议 | 用途 / 官方入口 |
| --- | --- | --- |
| JDK | **JDK 17**，也可使用 JDK 21 | 编译运行后端；[Eclipse Temurin](https://adoptium.net/temurin/releases/) |
| Apache Maven | **3.9.x** | 下载 Java 依赖并启动后端；[安装说明](https://maven.apache.org/install.html) |
| MySQL Server | **8.0.x** | 数据库服务；[Windows 安装说明](https://dev.mysql.com/doc/refman/8.0/en/windows-installation.html) |
| Node.js | **24 LTS**，或 **22.12 及以上的 22.x** | 运行管理后台；[下载](https://nodejs.org/en/download) |
| 微信开发者工具 | 官方稳定版 | 编译和真机调试；[下载](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html) |
| Git | 可选，但推荐安装 | 下载与更新项目；[官网](https://git-scm.com/) |
| IntelliJ IDEA | 可选 | 图形化运行与修改 Java 代码；[官网](https://www.jetbrains.com/idea/) |

不要使用 Node.js 16/18。本项目 `package.json` 要求 Node.js ≥22，当前 Vite 依赖进一步要求在 22.x 中至少使用 22.12。

### 2.2 什么是终端、PowerShell 和 PATH

在 Windows 开始菜单搜索 **PowerShell** 并打开。本文的 `powershell` 代码块粘贴到这里执行。

- `cd D:\serviceForTheAged`：切换当前操作目录。
- `$env:变量名 = '值'`：给当前终端及从它启动的程序设置环境变量。
- 新开一个 PowerShell 窗口，不会自动继承另一个窗口刚设置的 `$env:` 变量。
- `PATH`：系统查找 `java`、`mvn` 等命令的目录列表。
- 安装软件或修改 PATH 后，关闭旧终端，重新打开再验证。
- 代码块前面的 `PS D:\...>`、`mysql>` 如果出现在截图里，是提示符，不要一并输入。

### 2.3 安装 JDK 和 Maven

1. 安装 JDK 17 的 Windows 安装包，记下安装路径。
2. 在 Windows 搜索“编辑系统环境变量”，打开“环境变量”。
3. 设置 `JAVA_HOME` 为 JDK 安装目录，例如 `C:\Program Files\Eclipse Adoptium\jdk-17...`，不要写到 `bin`。
4. 在 Path 中添加 `%JAVA_HOME%\bin`。
5. 下载 Maven 的 **Binary zip archive** 并解压，例如到 `D:\Tools\apache-maven-3.9.11`。
6. 将 Maven 的 `bin` 目录加入 Path，例如 `D:\Tools\apache-maven-3.9.11\bin`。
7. 重新打开 PowerShell，执行：

```powershell
java -version
javac -version
mvn -v
```

成功标准：能看到 Java/Javac 17 或 21，以及 Maven 版本；`mvn -v` 显示的 Java 版本也应正确。若 java 是 17、Maven 却使用其他版本，需要修正 JAVA_HOME 和 Path。

不配置 Maven 的 Path，也可以使用完整路径运行：

```powershell
& 'D:\Tools\apache-maven-3.9.11\bin\mvn.cmd' -v
```

这里的路径需要改成你自己的安装位置。

### 2.4 安装 MySQL

1. 安装 **MySQL Server**；只安装 Workbench 等管理客户端并不等于安装数据库服务。
2. 配置数据库端口为 `3306`。
3. 设置 root 密码并记住。这个密码是数据库密码，和网页管理员密码是两回事。
4. 配置为 Windows 服务，并允许开机启动；服务名常见为 `MySQL80`，也可能不同。
5. 将 MySQL 安装目录下的 `bin` 加到 Path，例如 `C:\Program Files\MySQL\MySQL Server 8.0\bin`。
6. 新开 PowerShell，执行：

```powershell
mysql --version
mysql -h 127.0.0.1 -P 3306 -u root -p
```

出现 `Enter password:` 后输入你安装 MySQL 时设置的密码。输入时屏幕不显示字符属于正常情况。

成功标准：看到 `mysql>` 提示符。在此处输入下面的 SQL：

```sql
SELECT VERSION();
exit;
```

如果连接失败，按 `Win + R` 输入 `services.msc`，找到 MySQL 服务并启动，再重试。

### 2.5 安装 Node.js

安装 Node.js Windows 安装包，保留 npm 和添加到 Path 的选项。重新打开 PowerShell：

```powershell
node -v
npm.cmd -v
```

成功标准：显示符合清单要求的 Node.js 版本和 npm 版本。本文统一使用 `npm.cmd`，避免 Windows 的 `npm.ps1` 执行策略报错。

## 3. 下载项目

### 3.1 使用 Git 下载

在 PowerShell 中执行：

```powershell
cd D:\
git clone https://github.com/2033537858qqcom-hash/serviceForTheAged.git
cd D:\serviceForTheAged
```

如果 D 盘已经有同名目录，不要覆盖；已有 Git 项目按第 12 节更新。

### 3.2 不会使用 Git

打开[项目仓库](https://github.com/2033537858qqcom-hash/serviceForTheAged)，选择 **Code → Download ZIP**，解压到本地。若仓库要求登录，需要有仓库访问权限。

解压后的文件夹可能叫 `serviceForTheAged-main`，可改名为 `serviceForTheAged`。推荐放到简单路径，例如 `D:\serviceForTheAged`。

### 3.3 确认打开了正确目录

项目根目录应包含：

```text
serviceForTheAged/
├─ backend/          Java 后端
├─ admin-web/        管理后台
├─ miniprogram/      微信小程序
├─ sql/              数据库脚本
├─ docs/             设计、验证及演示说明
├─ scripts/          辅助验证脚本
├─ project.config.json
└─ README.md
```

后续“项目根目录”指的就是这个目录，不是其中的 backend 或 admin-web。

## 4. 第一次初始化数据库

> **本节只适用于首次安装且没有需要保留的数据。`sql/schema.sql` 包含 DROP TABLE，会删除并重建同名业务表。已经运行过本项目的用户不要再执行它，应阅读第 12 节。**

### 4.1 登录数据库并检查是否已有项目库

在 PowerShell 中执行：

```powershell
mysql -h 127.0.0.1 -P 3306 -u root -p --default-character-set=utf8mb4
```

进入 `mysql>` 后执行：

```sql
SHOW DATABASES LIKE 'aged_care_db';
```

如果已经有这个库，请先确认其用途。不要把下面的初始化命令当作普通启动步骤。

### 4.2 首次安装：导入结构和演示数据

确认没有需要保留的已有项目数据后，在 **mysql 提示符**下依次执行，路径使用正斜杠：

```sql
SOURCE D:/serviceForTheAged/sql/schema.sql;
SOURCE D:/serviceForTheAged/sql/data.sql;
USE aged_care_db;
SHOW TABLES;
SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema = 'aged_care_db';
SELECT username, role, status FROM sys_user;
exit;
```

每个 SOURCE 执行后检查是否有 `ERROR`；出现错误应先排查，不要继续假定导入成功。

成功标准：当前版本应有 **18 张业务表**，账号列表包含 `admin`、`resident01`、`family01`、`staff01` 等。

说明：

- `schema.sql` 是最新完整结构，首次导入后**不需要再执行 migration_v2～v7**。
- `data.sql` 是演示资料，包含账号、服务项目、工作人员、技能与排班等；不保证首页已有完整订单。
- `data.sql` 不要重复导入，否则会报主键冲突。
- 不要在 PowerShell 中直接照抄 Linux 的 `mysql < 文件.sql` 写法；本文使用 MySQL 自带的 SOURCE 命令。
- 演示排班只覆盖**导入当天及其后两天**。之后演示需要在后台重新安排日期对应的班次。

### 4.3 如果只想使用空白系统

只导入 schema、不导入 data 时，没有演示账号和服务资料。首次启动后端前需设置 `BOOTSTRAP_ADMIN_PASSWORD`，至少 12 个字符且不超过 72 个 UTF-8 字节，建议使用英文、数字与符号组合。

后端会创建管理员 `admin`，密码为你设置的值。此时 **admin / 123456 不适用**，也没有 resident01、staff01 等预置账号，需要自行维护业务资料。初次学习建议使用上一节的演示数据方式。

## 5. 启动 Java 后端

### 5.1 先分清三个密码/密钥

| 名称 | 用在哪里 | 应填什么 |
| --- | --- | --- |
| `DB_PASSWORD` | Java 连接 MySQL | 你安装 MySQL 时设置的密码 |
| 网页管理员密码 | Web 登录页面 | 导入演示数据后为 `123456`；已改过则使用修改后的密码 |
| `JWT_SECRET` | 服务端签发登录凭证 | 自己生成并妥善保存的随机密钥，至少 32 字节 |

另外，微信的 `AppSecret` 是小程序服务端密钥，与上述三项都不同。

### 5.2 推荐方式：PowerShell 启动

打开一个 PowerShell 窗口作为“后端窗口”，执行以下内容：

```powershell
cd D:\serviceForTheAged\backend
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_USER = 'root'
$databasePassword = Read-Host '请输入本机 MySQL 密码' -AsSecureString
$env:DB_PASSWORD = [Net.NetworkCredential]::new('', $databasePassword).Password
$env:APP_DEV_MODE = 'true'

# 生成本次运行使用的随机 JWT 密钥，兼容 Windows PowerShell 5.1
$jwtBytes = New-Object byte[] 32
$jwtGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
$jwtGenerator.GetBytes($jwtBytes)
$jwtGenerator.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)

mvn spring-boot:run
```

第一次启动需要下载依赖，耗时取决于网络。出现下载日志时先等待，不要反复点击运行。

**不要关闭这个窗口。** 后端正常运行时会持续占用终端，不返回输入提示符。

这里显式设置 `dev` 和 `APP_DEV_MODE=true`，用于本地学习和答辩演示。项目默认环境是 prod，仅设置 APP_DEV_MODE 而不设置 dev，不能代替开发环境配置。

此命令每次重新执行会生成不同 JWT 密钥，因此之前登录的用户可能需要重新登录。如果需要跨重启保留会话，可将自己生成的密钥保存到本机私有运行配置中，并持续使用同一个值；不要提交到仓库。

**根目录 `.env` 不会被这条 Maven 命令自动加载。** 把内容写进 `.env` 并不等于设置好了 Spring Boot 环境变量。

如使用空白系统，在执行 Maven 前还需设置：

```powershell
$bootstrapPassword = Read-Host '请设置至少12个字符的管理员密码' -AsSecureString
$env:BOOTSTRAP_ADMIN_PASSWORD = [Net.NetworkCredential]::new('', $bootstrapPassword).Password
```

已有管理员时，该变量不会覆盖原密码。

### 5.3 如何判断后端已经启动

查看日志：

- 有 `Tomcat started on port 8080`，上下文路径为 `/api`。
- 有 `Started AgedCareApplication`。
- 启动初始化结束后没有新的异常或 `APPLICATION FAILED TO START`，进程仍在运行。

在电脑浏览器访问：

```text
http://localhost:8080/api/swagger-ui.html
```

dev 环境可打开接口文档；prod 环境默认关闭。浏览器直接访问 `http://localhost:8080/` 不一定有页面，这不是项目首页。

也可以在另一个 PowerShell 窗口检查管理员登录接口。以下仅适用于导入演示账号且未修改密码的情况：

```powershell
$loginBody = @{ username = 'admin'; password = '123456' } | ConvertTo-Json
$loginResult = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
$loginResult | Select-Object code, message
```

成功标准：`code` 为 `200`。这一步会校验数据库账号并签发登录凭证，不会修改管理员密码。不要截图或分享完整 token。

### 5.4 可选方式：使用 IDEA

PowerShell 启动和 IDEA 启动选一种即可，**不要同时启动两个后端**。

1. 用 IDEA 打开项目，找到 `backend/pom.xml`，作为 Maven 项目导入；等待依赖加载完成。
2. 将 Project SDK 和 Maven Runner JRE 设置为 JDK 17 或 21。
3. 找到 `backend/src/main/java/com/agedcare/AgedCareApplication.java`。
4. 创建该启动类的运行配置，打开“编辑配置”。
5. 工作目录设置为 `D:\serviceForTheAged\backend`，保持与命令行方式一致，避免上传文件存放位置变化。
6. 若有“有效配置文件 / Active profiles”，填写 `dev`；没有此项则在环境变量中设置 `SPRING_PROFILES_ACTIVE=dev`。
7. 通过“修改选项 → 环境变量”填写以下变量，将占位内容换成自己的值：

```text
SPRING_PROFILES_ACTIVE=dev;DB_USER=root;DB_PASSWORD=你的MySQL密码;APP_DEV_MODE=true;JWT_SECRET=你自己生成且至少32字节的随机密钥
```

8. 保存并运行，按上一节检查日志和接口。

“环境变量”和“VM 选项”不是同一个输入框。不要把上面的分号列表直接放进 VM 选项。密码若包含分号，使用 IDE 的环境变量表格逐项录入。

IDEA 中也可使用 PowerShell 生成 JWT 密钥，再把结果复制到私有运行配置。项目忽略了 `.idea/`，不要另外把密钥贴进代码、文档或截图。

## 6. 启动 Web 管理后台

后端保持运行，**另外打开一个 PowerShell 窗口**：

```powershell
cd D:\serviceForTheAged\admin-web
npm.cmd ci
npm.cmd run dev
```

- `npm ci` 安装锁定版本的依赖，第一次运行或依赖更新后执行。
- `npm run dev` 启动网页开发服务器，运行后保持窗口打开。
- 不要在已经运行中的前端目录反复执行 npm ci；Windows 可能因文件占用报 EPERM。

看到 Vite 输出本地访问地址后，浏览器打开：

```text
http://localhost:3000/login
```

导入演示数据且未改密码时：

| 项目 | 内容 |
| --- | --- |
| 账号 | `admin` |
| 密码 | `123456` |

成功标准：登录后进入管理页面，能读取服务项目、工作人员等数据库资料。

网页默认把 `/api` 请求代理到 `http://localhost:8080`，通常不需要手动配置接口地址。

如果 Vite 提示 3000 被占用并改到 3001，请先确认是否已有该项目的前端在运行，优先继续使用原进程。不要看到不同端口就认为 Java 后端也要改端口。

## 7. 配置并运行微信小程序

### 7.1 准备自己的小程序开发权限

完整真实微信登录需要：

- 一个可用于开发的小程序 AppID。
- 对应的 AppSecret。
- 扫码运行的人拥有该小程序需要的开发/体验权限。

从[微信公众平台](https://mp.weixin.qq.com/)管理小程序，查看开发信息并按需添加开发成员。**仓库里的 AppID 属于原项目，不表示下载代码后自动拥有其开发权限。**

如果只是先学习后台，可以先完成第 6 节；完整小程序验收再完成微信配置。

### 7.2 保证三处 AppID 一致

替换下列位置为同一个、你有权限使用的 AppID：

1. 根目录 `project.config.json` 的 `appid`。
2. `miniprogram/project.config.json` 的 `appid`。
3. 后端的 `WECHAT_APPID` 环境变量或本地微信配置文件。

AppSecret **只给后端**，不要写入小程序 JS、project.config.json 或管理后台代码。

### 7.3 配置后端微信密钥

推荐创建本机私有文件：

```text
D:\serviceForTheAged\backend\config\wechat-local.properties
```

如果目录不存在，先创建 `config` 目录。文件内容：

```properties
wechat.miniapp.appid=填写你自己的AppID
wechat.miniapp.secret=填写对应的AppSecret
```

保存时确认文件名不是 `wechat-local.properties.txt`。Windows 文件资源管理器可开启“显示文件扩展名”。

此文件已在 `.gitignore` 中排除，dev 环境会加载。修改后**重启后端**。

也可以在启动后端的同一终端设置 `WECHAT_APPID`、`WECHAT_SECRET`，或在 IDEA 环境变量中设置。若两种方式都配置了，建议删除不再使用的旧值，避免混淆。

开发模式下，后端在微信配置缺失时有模拟 OpenID 降级逻辑。它仅用于联调，可能随登录 code 变化创建不同账号；不要把这种成功当作真实微信登录验证通过。稳定演示应完成上述真实配置。

### 7.4 导入开发者工具

1. 打开微信开发者工具并用有开发权限的微信扫码登录。
2. 选择“导入项目”。
3. **推荐选择项目根目录 `D:\serviceForTheAged`**；根目录配置已指定小程序代码目录为 `miniprogram/`。
4. 确认 AppID 正确，类型为小程序。本项目使用独立 Java 后端，无需开启云开发。
5. 若改为直接导入 `D:\serviceForTheAged\miniprogram` 也可以，但不要将两个导入方式混用并误改另一份项目配置。
6. 在“详情 → 本地设置”中，为本地联调勾选“不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书”等对应选项。
7. 点击“编译”，查看模拟器是否正常显示页面。
8. 勾选服务协议后点击微信快捷登录。

小程序是原生 WXML/WXSS/JS 项目，正常运行不需要在 miniprogram 目录执行 npm install 或“构建 npm”。

项目曾配置基础库 3.3.4。若工具提示某 API 基础库要求，请选择工具可用的稳定基础库，并重新验证；答辩前固定已验证版本，不临时切换灰度版本。

电脑模拟器默认请求 `http://localhost:8080/api`。模拟器能打开页面但不能登录时，先检查后端、AppID 和 AppSecret，而不是只重编译界面。

## 8. 手机和平板真机连接

### 8.1 手机与电脑要能互相访问

1. 手机和平板与电脑连接同一个可信的 Wi-Fi / 局域网。
2. 学校访客网络可能隔离设备，即使 Wi-Fi 名称相同也无法互通；必要时换到允许设备互通的网络。
3. 检查 VPN、虚拟网卡或代理是否导致识别出错误 IP。
4. 保持后端运行，电脑不要休眠。

### 8.2 同步电脑局域网 IP

后端 dev 启动时会尝试自动同步真机地址。也可在**项目根目录**手动执行：

```powershell
cd D:\serviceForTheAged
node miniprogram/scripts/sync-lan-ip.cjs
```

成功输出类似：

```text
[lan-sync] updated http://192.168.1.23:8080/api
```

这里的 IP 只是示例，以你的输出为准。脚本生成 `miniprogram/config/local.js`，该文件不提交 Git。

然后在开发者工具中**重新编译，再启动真机调试并扫码**。换 Wi-Fi、换电脑或 IP 改变后，需要重新同步和编译。

### 8.3 检查网络与域名校验

在手机浏览器访问电脑的开发接口文档地址，例如：

```text
http://192.168.1.23:8080/api/swagger-ui.html
```

若手机浏览器也连接超时，先解决网络、防火墙或后端监听问题。域名校验开关无法解决 TCP 网络不通。

仅在本地真机调试时，在真机调试窗口中确认“不校验合法域名”等选项已生效；工具版本不同，入口可能不同。电脑模拟器勾选成功，不代表所有真机调试会话都已生效。

Windows 防火墙弹出 Java 网络访问提示时，仅允许可信私有网络。需要手动处理时，在“Windows Defender 防火墙 → 高级设置 → 入站规则”添加 TCP 8080，范围限制为可信私有网络/本地子网，不需要关闭整个防火墙，也不需要开放 MySQL 3306 给手机。

自动 IP 选择错误时，用 `ipconfig` 找到实际连接网络的 IPv4。可在后端启动后修正 `miniprogram/config/local.js`：

```javascript
module.exports = { LAN_BASE_URL: 'http://你的电脑IPv4地址:8080/api' };
```

替换示例值并重新编译。后端重启可能再次自动生成该文件，多网卡环境应重新确认。

### 8.4 开发版、体验版和正式版的区别

当前 `miniprogram/app.js` 的正式环境地址为占位值：

```text
https://care.example.com/api
```

**不要直接上传体验版后期待它继续连接本机 IP。** 体验版和正式版固定走正式地址，并隐藏开发演示入口。

本指南的单手机多角色答辩使用开发版真机调试。若要部署体验版/正式版，需要另行部署可访问的 HTTPS 后端、替换正式地址、配置微信合法域名，并完成真实环境验收。关闭开发校验不能替代这些步骤。

## 9. 账号、注册和多角色演示

### 9.1 预置账号

以下账号来自 `sql/data.sql`，仅供本地学习和演示：

| 账号 | 身份 | 使用方式 |
| --- | --- | --- |
| `admin` | 管理员 | Web 后台账号密码登录，初始密码 `123456` |
| `resident01` | 老人赵德柱 | 小程序开发演示入口切换 |
| `family01` | 已授权家属赵志强 | 小程序开发演示入口切换 |
| `staff01` | 服务人员张玉兰 | 小程序开发演示入口切换 |
| `staff02`、`staff03` | 其他演示工作人员 | 数据库中预置；当前快捷切换白名单不包含这两项 |
| `family02` | 待审核家属 | 数据库中预置；当前快捷切换白名单不包含此项 |

当前小程序登录页只有微信快捷登录，不提供账号密码表单。不要把 admin、root 或 resident01 输入微信登录界面。Web 后台使用管理员身份，不用居民和工作人员账号进入管理页面。

### 9.2 一个微信为什么能演示不同角色

AppID 标识小程序，OpenID 标识该小程序中的微信用户，项目数据库账号标识业务身份。

真实微信首次登录注册为居民。开发演示入口允许切换指定的独立业务测试账号，不修改真实微信 OpenID，也不把管理员权限开放给普通用户。

操作顺序：

1. 后端以 `dev` 且 `APP_DEV_MODE=true` 启动。
2. 开发版小程序完成微信登录。
3. 进入“我的 → 答辩演示 · 切换演示身份”。
4. 切换预置老人、家属或工作人员；也可创建演示新用户。
5. 需要回到原微信账号时，选择“返回微信登录”并重新登录。

### 9.3 演示新用户成为工作人员

1. 创建演示新用户，完善姓名。
2. 选择“申请成为服务人员”，填写姓名、手机号、服务区域和经历。
3. 在 Web 后台“服务人员 → 入驻申请审核”查看申请。
4. 可以先退回并写明原因，再在手机补充资料重新提交。
5. 审核通过前，管理员核对出发站点和经纬度；默认演示坐标不代表真实居住地。
6. 手机刷新审核结果，点击“更新身份并进入工作台”。
7. 进入“我的资质证明”，上传证明材料；后台审核对应技能。
8. 管理员安排班次，工作人员调整为可接单状态，再参与派单。

新入驻人员默认休息状态。**入驻通过不等于已具备所有技能，也不等于自动开始接单。**

当前每个账号只有一个系统角色，入驻通过会将居民转换为工作人员。演示两种角色业务请使用不同测试账号。重复创建人员时，手机号不能与已有账号冲突。

详细说明见 [单设备注册与多角色答辩演示](docs/18-单设备注册与多角色答辩演示.md)。

## 10. 完成一次完整业务演示

### 10.1 演示前准备

管理员先检查：

- 服务项目已上架，价格、服务时长和所需技能设置正确。
- 老人档案或家属绑定已通过相应流程，地址和坐标一致。
- 候选工作人员账号正常、入驻及技能审核通过、服务区域覆盖目标地址。
- **预约当天有有效班次**，并且人员处于可接单状态。
- 预约时间应在未来，预留足够出行时间；不要选择“现在立刻上门”。
- 如果演示日期超过数据导入后的两天，重新维护排班。

### 10.2 正常服务流程

1. 切换 resident01，或使用已有老人档案的居民账号。
2. 选择服务项目，填写预约日期、时间和服务地址，提交预约。
3. 后台查看订单及派单评分，解释候选人为什么入选或被排除。
4. 查看实际被派给哪位工作人员，再切换对应可演示身份接单。
5. 单手机演示常用 staff01；如派给 staff02/staff03，当前快捷入口无法切换到它们。应事先安排明确的演示班次/候选人员，或按业务规则在后台协调，不要临场绕过权限。
6. 工作人员上门签到，填写服务记录、上传真实有效的测试图片，提交完成。
7. 切回居民或已授权家属，确认服务并评价。
8. 管理员查看订单时间线、履约材料和评价结果。

自动派单默认等待接单 120 秒，超时会释放预留并重派；不要在接单前讲解过久。

实际签到、定位等操作可能要求设备权限与业务条件，提前在演示设备上试跑。示例坐标位于演示社区，不能直接当作现场真实位置证明。

### 10.3 异常与反馈流程

- 无合适候选人或多次拒单：展示“待人工处理”以及排除原因，再由管理员协调。
- 人员材料不完整：管理员退回，申请人补充后重新提交。
- 服务问题：居民提交评价/反馈，管理员处理后查看答复。

演示数据是真实写入当前数据库的，不会在关闭页面后自动清空。金额展示与费用责任用于业务协调，当前系统不接入真实微信支付扣款。

## 11. 每天如何启动和关闭

完成首次安装后，不需要每天重装软件、重新建库或重导 data.sql。

每天启动：

1. 确认 MySQL 服务正在运行。
2. 在后端终端重新设置所需环境变量并执行 `mvn spring-boot:run`，或运行已保存的 IDEA 配置。
3. 在 admin-web 目录执行 `npm.cmd run dev`。
4. 打开管理后台并登录。
5. 若网络变化，同步局域网 IP。
6. 打开微信开发者工具，编译并扫码真机调试。

关闭时，在前端和后端各自的终端按 `Ctrl + C`，IDEA 使用停止按钮。若询问是否终止批处理，确认即可。关闭网页本身不会停止 Java 后端和 Vite。

MySQL 可以继续作为系统服务运行。不要通过删除数据库、删除 uploads 或重导 schema 来“关闭项目”。

## 12. 已有项目如何更新

### 12.1 更新代码

先查看本地是否有未保存到 Git 的修改：

```powershell
cd D:\serviceForTheAged
git status
git pull --ff-only
```

如果提示本地改动冲突或无法快进，先保留自己的改动并处理版本差异，不要直接强制覆盖。

### 12.2 更新数据库结构

更新前备份数据库，方法见第 15 节。**已有数据不要执行 schema.sql，也不要重新导入 data.sql。**

从已经包含 v2～v5 变更的版本升级到当前入驻/资质功能，执行：

```powershell
mysql -h 127.0.0.1 -P 3306 -u root -p --default-character-set=utf8mb4
```

在 mysql 中：

```sql
USE aged_care_db;
SOURCE D:/serviceForTheAged/sql/migration_v6.sql;
SOURCE D:/serviceForTheAged/sql/migration_v7.sql;
SHOW COLUMNS FROM staff_skill LIKE 'proof_urls_json';
SHOW TABLES LIKE 'staff_application';
exit;
```

v6 新增资质材料及审核字段，v7 新增入驻申请表；这两份脚本可重复执行。

如果你的版本更老或无法确定结构，先核对已有表字段与对应迁移内容。历史 v3～v5 包含非幂等 ALTER，有的还含演示坐标更新，**不要不加判断地全部重跑**。数据升级不会由 Spring Boot 自动完成。

### 12.3 重启并刷新

- 后端代码或配置变化：停止旧后端，再启动。
- 前端依赖锁文件变化：先停止 Vite，再在 admin-web 执行 npm ci，然后 npm run dev。
- 小程序变化：重新编译，再重新进入真机调试。
- 登录令牌失效：重新登录。
- 已上传图片保存在本地 uploads，更新代码时保留。

## 13. 常见报错与处理

### 13.1 命令不被识别

`java / mvn / mysql / node 不是内部或外部命令`：检查软件是否安装、bin 是否加入 Path，并重新打开终端。可用完整 exe/cmd 路径运行，不需要因此重装整个项目。

### 13.2 npm.ps1 被禁止运行

使用本文的 `npm.cmd ci`、`npm.cmd run dev`。无需为了运行项目降低整台电脑的脚本执行策略。

### 13.3 数据库 Access denied

检查 DB_USER、DB_PASSWORD 是否为本机 MySQL 账号。先用 `mysql -u root -p` 验证；网页密码 `123456` 不代表你的数据库密码也是它。

### 13.4 Communications link failure / 数据库连接被拒绝

检查 MySQL 服务是否运行以及端口是否为 3306。本地 dev 默认连接 localhost:3306/aged_care_db。

若使用其他端口，可在启动后端前指定完整 JDBC 地址，例如：

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3307/aged_care_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
```

3307 仅为示例，改为自己的实际端口。

### 13.5 Unknown database / Table doesn't exist / Unknown column

分别表示数据库缺失、表缺失或结构版本落后。首次安装检查导入结果；已有系统按第 12 节检查迁移。不要拿重建整个数据库作为第一步。

### 13.6 Port 8080 was already in use

通常是 IDEA 或另一个终端已启动了后端。优先找到原运行窗口并停止多余实例。

只查看占用信息：

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object LocalAddress, LocalPort, OwningProcess
```

根据返回的 PID 查看进程，例如将下面 12345 替换为实际值：

```powershell
Get-Process -Id 12345
```

确认进程归属后再决定停止，不要批量结束所有 Java 进程。如果确需改后端端口，同时更新 Vite 代理与小程序接口地址；最简单的本地方式是保留 8080 并避免重复启动。

### 13.7 前端页面能打开，登录却报网络错误

检查后端是否启动，先尝试第 5.3 节的接口检查。Vite 能启动只证明网页开发服务器正常，不证明后端或数据库正常。

后端在其他地址时，可创建 `admin-web/.env.local`，填入如下配置并重启 Vite：

```dotenv
VITE_PROXY_TARGET=http://localhost:8080
```

这里不加 `/api`，因为代理匹配路径已经包含它。不要在该文件中放微信密钥或数据库密码。

### 13.8 小程序 url not in domain list / errno 600002

这是微信请求域名校验阻止了请求。本地开发检查工具及真机调试的域名校验设置；体验版/正式版应配置真实合法 HTTPS 域名。

把 localhost 改成局域网 IP 能解决地址指向问题，但不自动解决域名校验。

### 13.9 ERR_CONNECTION_TIMED_OUT / errno 600001

域名校验放行后仍超时，重点查网络：

1. 手机地址是否仍写 localhost，或是旧的局域网 IP。
2. 电脑后端是否运行、电脑是否休眠。
3. 手机浏览器能否访问同一 IP 的后端。
4. 防火墙、校园网设备隔离、VPN 和代理。
5. 地址更新后是否重新编译并重新扫码。

### 13.10 微信登录失败、invalid appid / invalid code

检查小程序 AppID 与后端配置是否一致，AppSecret 是否属于该 AppID，登录微信是否有开发权限。修改后重启后端、重新编译并点击登录获取新 code，不要反复使用旧 code。

### 13.11 看不到“答辩演示”入口

同时确认：后端 active profile 为 dev、APP_DEV_MODE=true、手机为开发版、已经登录居民或工作人员账号，以及前后端均已更新重启。prod、体验版和正式版不提供该入口。

### 13.12 一直提示登录过期

可能是重启时换了 JWT 密钥、旧登录凭证已过期，或角色/账号状态发生变化。先退出并重新登录；需要稳定保留登录时使用固定私有 JWT 密钥。

### 13.13 订单一直“待人工处理”

打开后台派单详情查看排除原因，检查班次日期、技能审核、接单状态、服务范围、时间冲突和出行时间。不是所有订单都应强行自动派单；无法满足约束转人工属于预期行为。

### 13.14 资质、服务图片上传失败

使用真实 JPG/JPEG、PNG 或 WEBP 图片，不要只修改文件后缀。普通上传单文件上限 5MB，服务封面上限 2MB；资质申请要求 1～3 张证明。

没有 COS 配置时，新安装本地环境可以使用本地存储。资质和履约照片为私有文件，需要登录并有权限才能查看，不能拿匿名浏览器打不开来判断文件丢失。

如果是旧系统且已有 COS 历史照片，启动迁移可能需要原来的存储配置和访问权限；这种情况应按迁移日志处理，不要删除资料或跳过迁移掩盖错误。

### 13.15 WXML unexpected token / __route__ is not defined

先处理日志中**第一条 WXML 编译错误**。WXML 模板不支持直接调用任意 JS 方法，例如模板内 `.toFixed()`；应在页面 JS 里计算好显示值。后续渲染层错误可能由前面的编译失败引起。

### 13.16 SharedArrayBuffer Deprecation 警告

仅出现这种开发者工具兼容性警告，不代表后端启动失败。优先看 WXML 编译错误、请求失败或真实业务异常，不要为消除警告随意改接口和浏览器安全配置。

### 13.17 npm ci 下载失败 / EPERM

网络错误先检查网络和 npm 配置后重试。EPERM 常见于文件被 Vite、测试进程或 IDE 占用，先关闭相应进程，再重新安装。保留 package-lock.json，避免为解决下载问题随意升级全部依赖。

### 13.18 Maven 下载失败 / 编译错误

先查看第一条错误。依赖下载失败通常是网络或 Maven 仓库配置问题；`invalid target release: 17` 通常是实际使用了过旧的 JDK。

推荐先统一到 JDK 17/21。若必须使用 JDK 23 及以上，需要处理注解处理默认行为，例如 Maven 命令追加 `'-Dmaven.compiler.proc=full'`；该参数不能替代正确安装 JDK。

## 14. 测试与构建

**正常运行项目不要求先执行所有测试。** 下列检查用于修改代码后验证。不要直接运行旧的 verify-backend.ps1 并期待完全不使用 Docker；旧部分集成测试仍依赖 Testcontainers。

### 14.1 小程序逻辑测试

在根目录：

```powershell
cd D:\serviceForTheAged
node --test miniprogram/tests/*.test.cjs
```

这验证 JS 逻辑，不替代微信真实授权、定位、拍照或平板真机验收。

### 14.2 管理后台测试与构建

```powershell
cd D:\serviceForTheAged\admin-web
npm.cmd test
npm.cmd run build
```

构建产物位于 admin-web/dist。不要双击 dist/index.html 当作部署完成；正式部署还需要 Web 服务器、路由回退和 `/api` 代理。本指南的本地访问继续使用 npm run dev。

### 14.3 不使用 Docker 的后端单元测试

在根目录执行以下筛选命令：

```powershell
cd D:\serviceForTheAged
mvn -B -f backend/pom.xml test '-Dtest=*Test,!*IntegrationTest,!DispatchConcurrencyAndTransactionTest' '-Dmaven.compiler.proc=full'
```

该命令只运行选定的单元测试，不能声称已经执行所有数据库集成测试。完整 `mvn test` / `mvn verify` 会包含目前仍依赖容器的测试。

### 14.4 可选：本地 MySQL 入驻集成测试

需要本机 MySQL 和有创建/删除测试库权限的账号：

```powershell
cd D:\serviceForTheAged
$env:AGEDCARE_LOCAL_INTEGRATION = 'true'
$env:AGEDCARE_TEST_DB_USER = 'root'
$testDatabasePassword = Read-Host '请输入本机测试数据库管理员密码' -AsSecureString
$env:AGEDCARE_TEST_DB_PASSWORD = [Net.NetworkCredential]::new('', $testDatabasePassword).Password
mvn -B -f backend/pom.xml test '-Dtest=EnrollmentLocalIntegrationTest' '-Dmaven.compiler.proc=full'
Remove-Item Env:AGEDCARE_LOCAL_INTEGRATION
Remove-Item Env:AGEDCARE_TEST_DB_PASSWORD
Remove-Item Env:AGEDCARE_TEST_DB_USER
```

测试创建随机命名的 `agedcare_enrollment_test_<随机标识>` 数据库，验证注册、申请、审核与事务，结束后清理自己的测试库；不使用业务库测试。若进程被强制结束，可能遗留测试库，清理前必须核对完整库名。

### 14.5 最近一次功能验证记录

入驻/资质/演示功能交付时记录：后端 111 项单元测试及 1 项本地 MySQL 流程测试、小程序 195 项测试、后台 44 项测试通过；并完成后台构建、小程序原生编译和 110 组响应式布局检查。

这些是当时环境的验证记录，不保证任意新电脑、任意网络和微信账号都已验证，也不等于本次只修改文档时重新执行了全部测试。详见 docs/17 与 docs/18。

## 15. 备份和迁移到另一台电脑

### 15.1 备份数据库

在 PowerShell 中先创建备份目录，再执行 mysqldump。备份目录放在仓库外：

```powershell
New-Item -ItemType Directory -Force -Path D:\aged-care-backup
mysqldump -h 127.0.0.1 -P 3306 -u root -p --default-character-set=utf8mb4 --single-transaction --no-tablespaces --result-file=D:/aged-care-backup/aged_care_db.sql aged_care_db
```

每次备份建议修改输出文件名以免覆盖上一份。确认命令没有报错、文件存在且大小合理；备份包含业务资料，应妥善保管。

这里使用 `--result-file`，避免旧版 PowerShell 重定向输出时改变 SQL 文件编码。

### 15.2 图片和配置也要备份

数据库只保存图片引用，不包含本地图片文件。还应备份实际运行目录中的 uploads：按本文从 backend 启动时通常是 `backend/uploads/`。若以前从根目录启动，也应检查根目录 uploads；以实际配置和日志为准。

微信本地配置、自己保存的 JWT 密钥、IDEA 私有环境变量不会随 Git 自动传到新电脑，需要安全转移或重新配置。不要把这些文件和数据库备份上传到代码仓库。

### 15.3 在新电脑恢复

1. 按本文安装软件并下载相同版本代码。
2. 创建空的 `aged_care_db` 数据库，再在 mysql 客户端中恢复备份：

```sql
CREATE DATABASE IF NOT EXISTS aged_care_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE aged_care_db;
SOURCE D:/aged-care-backup/aged_care_db.sql;
```

3. 恢复对应 uploads 目录和本地私有配置。
4. 核对数据库密码、微信配置与图片路径。
5. 启动后端和前端，同步新电脑局域网 IP，重新编译小程序。

恢复会覆盖备份中同名表的现有内容，只在空目标库或已确认可以覆盖的目标上执行。恢复完成后不要再导入 schema.sql 或 data.sql。

## 16. 项目结构与设计说明

### 16.1 技术与业务范围

| 模块 | 主要实现 |
| --- | --- |
| backend | Spring Boot 3.3.4、MyBatis-Plus、MySQL、JWT、BCrypt、事务与调度任务 |
| admin-web | Vue 3、Vite、Element Plus、ECharts |
| miniprogram | 原生 WXML/WXSS/JavaScript、自定义导航、居民与工作人员页面 |
| sql | 18 张业务表、演示资料、增量迁移 |

业务覆盖老人档案、家属绑定、服务预约、派单、人工协调、履约、确认评价、反馈、工作人员入驻、技能资质审核、排班与操作日志。

当前身份模型：管理员 ADMIN、居民 RESIDENT、工作人员 STAFF。老人和家属共用居民系统角色，通过本人档案与审核后的绑定关系区分权限。

### 16.2 智能派单

先过滤账号、资质、区域、排班、冲突及出行时间等硬约束，再对可行人员进行评分：

```text
总分 = 0.35 × 到达便利度
     + 0.30 × 服务质量
     + 0.25 × 工作量余量
     + 0.10 × 熟悉程度
```

- 到达便利度基于估算出行时间，不是实时道路导航结果。
- 服务质量使用人员档案中的评分并归一化。
- 工作量计算包含服务与出行占用。
- 熟悉程度根据是否曾为该老人完成服务判断。
- 超时默认 120 秒、扫描间隔配置为 10 秒、最多尝试 3 次，无法分配时转人工处理。

这是约束筛选与加权评分调度，不是训练出的机器学习模型，也不保证所有规模下全局最优。论文应如实解释设计假设和仿真实验边界。

### 16.3 更多文档

| 文档 | 内容 |
| --- | --- |
| [项目实施方案](docs/01-项目实施方案.md) | 原始需求及实施规划 |
| [真机调试网络说明](docs/09-真机调试网络说明.md) | 手机网络与地址排查 |
| [算法实验报告](docs/11-派单算法实证实验与业务安全边界改进报告.md) | 多策略仿真、指标和边界 |
| [服务图片维护](docs/12-服务实景图片与后台维护.md) | 图片资源及维护 |
| [历史复审整改记录](docs/13-复审v2整改与验证.md) | 权限、事务与媒体整改记录 |
| [历史部署与隔离验收](docs/14-部署与隔离验收.md) | 包含容器方案；本地无 Docker 运行请以本 README 为准 |
| [手机与平板适配](docs/15-手机与平板布局适配.md) | 布局适配说明 |
| [服务评价与反馈](docs/16-服务评价与反馈改进.md) | 评价和反馈流程 |
| [资质审核](docs/17-资质证明提交与审核.md) | 私有证明图片及审核 |
| [单手机多角色演示](docs/18-单设备注册与多角色答辩演示.md) | 注册、身份切换、工作人员入驻 |

早期方案和数据库草案描述的是当时阶段，不全部代表当前实现。数据库最终结构以 sql/schema.sql 及适用迁移为准，业务以当前代码为准。

## 17. 运行成功检查表

完成下列检查才算基本跑通，不以“页面打开了”作为唯一标准：

- [ ] Java、Maven、MySQL、Node.js 版本命令正常。
- [ ] 数据库正确初始化或完成适用迁移，现有资料没有被意外覆盖。
- [ ] 后端以 dev 启动，初始化结束且没有随后退出。
- [ ] Web 管理员成功登录，服务项目和工作人员列表能读取。
- [ ] 小程序使用有权限的 AppID，前后端 AppID 一致，真实微信登录成功。
- [ ] 手机连接的是电脑局域网 IP，网络请求不超时。
- [ ] 当天/预约日期的排班、技能、区域和接单状态正确。
- [ ] 预约 → 派单 → 接单 → 履约 → 确认 → 评价流程走通。
- [ ] 新用户 → 入驻申请 → 审核 → 资质提交与审核流程走通。
- [ ] 普通字号、大字模式、主用手机和平板没有挡住关键按钮。
- [ ] 答辩前已备份数据库与图片，准备可重复演示的账号、班次和流程。

遇到问题时，优先记录：操作步骤、使用电脑还是手机、访问地址、后端第一条异常、开发者工具第一条请求/编译错误。分享日志前隐藏密码、AppSecret、token 和真实个人资料。
