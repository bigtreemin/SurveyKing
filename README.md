# 卷王

简体中文 | [English](./README.en-us.md)

## 一键部署比问X星更强大的调查问卷考试系统

[点击](https://wj.surveyking.cn/s/start)卷王问卷考试系统-快速开始

需要您的 star ⭐️⭐️⭐️ 支持鼓励 🙏🙏🙏，**右上角点 Star (非强制)加QQ群(1074277968)获取最新的数据库脚本**。

## 友情推荐 [专注于中台化架构的低代码生成工具](https://gitee.com/orangeform/orange-admin)

## 🚀 1 分钟快速体验调查问卷系统(无需安装数据库)

1. 下载卷王快速体验安装包(加群)
2. 解压，双击运行 start.bat
3. 打开浏览器访问 [http://localhost:1991](http://localhost:1991)，输入账号密码： *admin*/*123456*

快速开始
安装
通过发行版安装
目前已适配 mysql 数据库，理论上支持所有的关系型数据库

点击下载 surveyking-mysql 版本到本地

使用源码编译安装
默认构建的是 h2 版本的安装包。

使用 gradle 构建：

# 下载源码
git clone https://gitee.com/surveyking/surveyking.git

# 设置 profile，修改 api/src/main/resources/application.yml
# 打开 active: ${activeProfile} # gradle 配置

# 开始构建
gradle clean :api:build -P pro -x test
# 生成的 jar 包位于 ./api/build/libs/surveyking-v0.x.x.jar
使用 maven 构建：

# 下载源码
git clone https://gitee.com/surveyking/surveyking.git

# 开始构建
mvn clean package -DskipTests -Ppro
# 生成的 jar 包位于 ./api/target/surveyking-v0.x.x.jar
使用 docker 快速启动
启动 SurveyKing 镜像时，你可以指定 SurveyKing 挂载参数，将日志文件和内置数据库保存到你本地。(docker 版本目前还有点问题，待解决）

docker run -p 1991:1991 surveyking/surveyking
# 挂载数据文件
docker run -d -p 1991:1991 -v /my/logs:/files -v /my/logs:/logs
使用
预安装 JRE 环境，由于本系统是 Java 构建的，需要依赖 Java 运行环境，可以通过 适用于所有操作系统的 Java 下载 来预装 java 环境。
配置数据库，按照下面的说明来配置不同的数据库，如果前端需要使用 nginx 部署，参考使用 nginx 部署前端。
运行，支持所有平台部署，windows 和 mac 支持双击运行，或者打开命令行窗口执行如下命令
java -jar surveyking-v0.x.x.jar
打开浏览器，访问 http://localhost:1991 即可，系统首次启动之后会自动创建 admin 用户，账号/密码（admin/123456），登录系统之后可以通过用户管理界面来修改密码。

mysql 启动方式
使用参数启动

首先创建 mysql 数据库，然后执行初始化脚本，点击 快速开始-获取最新数据库脚本)。
执行 java -jar surveyking-v0.x.x.jar --server.port=1991 --spring.datasource.url=jdbc:mysql://localhost:3306/surveyking --spring.datasource.username=root --spring.datasource.password=123456（只有首次启动系统需要添加后面的参数）
参数说明(按照实际需要自行修改)：

--server.port=1991 系统端口
--spring.datasource.url=jdbc:mysql://localhost:3306/surveyking 数据库连接的 url
--spring.datasource.username=root 数据库账号
--spring.datasource.password=123456 数据库密码
也可以尝试使用命令行的方式初始化数据库（会自动执行数据库初始脚本）

# 按照提示初始化数据库
java -jar surveyking-v0.x.x.jar i
# 初始化完成之后运行即可
java -jar surveyking-v0.x.x.jar 
使用 nginx 部署前端
下载 该目录下面的静态资源文件，直接部署到 nginx 即可。

然后配置 proxy 代理到后端 api 服务。



## 特性

- 🥇 支持 20 多种题型，如填空、选择、下拉、级联、矩阵、分页、签名、题组、上传等
- 🎉 多种创建问卷方式，Excel导入问卷、文本导入问卷、在线编辑器编辑问卷
- 💪 多种问卷设置，支持白名单答卷、公开查询、答卷限制等
- 🎇 数据，支持问卷数据新增、编辑、标记、导出、打印、预览和打包下载附件
- 🎨 报表，支持对问题实时统计分析并以图形（条形图、柱形图、扇形图）、表格的形式展示输出
- 🚀 安装部署简单（**最快 1 分钟部署**），支持一键windows部署、一键docker部署、前后端分离部署、单jar部署
- 🥊 响应式布局，所有页面在 PC 和手机端都有良好的操作体验，支持手机端编辑问卷
- 👬 支持多人协作管理问卷
- 🎁 后端支持多种数据库，可支持所有带有 jdbc 驱动的关系型数据库
- 🐯 安全、可靠、稳定、高性能的后端 API 服务
- 🙆 支持完善的 RBAC 权限控制
- 🦋 完善的自定义逻辑，分为**显示隐藏逻辑**、**值计算逻辑**、**文本替换逻辑**、**值校验逻辑**、**必填逻辑**、**选项自动勾选逻辑**、**选项显示隐藏逻辑**、**结束问卷逻辑**、**跳转逻辑**、**结束问卷自定义提示语逻辑**、**自定义跳转链接逻辑**
- ...

## 问卷产品对比

|                 | 问卷网 | 腾讯问卷 | 问卷星 | 金数据 | 卷王 |
| --------------- | ------ | -------- | ------ | ------ | ---- |
| 问卷调查        | ✔️   | ✔️     | ✔️   | ✔️   | ✔️ |
| 在线考试        | ✔️   | ❌       | ✔️   | ✔️   | ✔️ |
| 投票            | ✔️   | ✔️     | ✔️   | ✔️   | ✔️ |
| 支持题型        | 🥇     | 🥉       | 🥇     | 🥈     | 🥈   |
| 题型设置        | 🥇     | 🥉       | 🥇     | 🥇     | 🥇   |
| 自动计算        | ❌     | ❌       | 🥉     | 🥈     | 🥇   |
| 逻辑设置        | 🥈     | 🥈       | 🥈     | 🥈     | 🥇   |
| 自定义校验      | ❌     | ❌       | ❌     | ❌     | ✔️ |
| 自定义导出      | 🥈     | ❌       | ❌     | 🥉     | 🥇   |
| 手机端编辑      | ✔️   | ✔️     | ✔️   | ✔️   | ✔️ |
| 公开查询（快查) | ✔️   | ❌       | ✔️   | ❌     | ✔️ |
| 私有部署        | 💰💰💰 | 💰💰💰   | 💰💰💰 | 💰💰💰 | 🆓   |

注: 上表与卷王对比的全部是商业问卷产品，他们有很多地方值得卷王学习，仅列出部分主要功能供大家参考，如果对结果有疑问，可以点击对应产品的链接自行对比体验。

🥇强  🥈中 🥉弱

## 预览截图

* 考试系统预览

<table>
    <tr>
        <td><img src="docs/images/exam-editor.jpg"/></td>
        <td><img src="docs/images/exam-import.jpg"/></td>
    </tr>
     <tr>
        <td><img src="docs/images/exam-pc-prev.jpg"/></td>
        <td><img src="docs/images/exam-mb-preview.jpeg"/></td>
    </tr>
     <tr>
        <td><img src="docs/images/exam-repo-list.jpg"/></td>
        <td><img src="docs/images/exam-repo-pick.jpg"/></td>
    </tr>
     <tr>
        <td><img src="docs/images/exam-repo-qedit.jpg"/></td>
        <td><img src="docs/images/exam-repo.jpg"/></td>
    </tr>
</table>

* 调查问卷预览

<table>
    <tr>
        <td><img src="docs/images/survey-editor.jpg"/></td>
        <td><img src="docs/images/survey-editor-formula.jpg"/></td>
    </tr>
    <tr>
        <td><img src="docs/images/survey-editor-preview.jpg"/></td>
        <td><img src="docs/images/survey-imp.jpg"/></td>
    </tr>
    <tr>
        <td><img src="docs/images/survey-export.jpg"/></td>
        <td><img src="docs/images/survey-exp-preview.jpg"/></td>
    </tr>
    <tr>
        <td><img src="docs/images/survey-exp-formula.jpg"/></td>
        <td><img src="docs/images/survey-formula.jpg"/></td>
    </tr>
    <tr>
        <td><img src="docs/images/survey-editor-preview.jpg"/></td>
        <td><img src="docs/images/survey-prev-mbmi.jpeg"/></td>
    </tr>
    <tr>
        <td><img src="docs/images/survey-report.jpg"/></td>
        <td><img src="docs/images/survey-setting.jpg"/></td>
    </tr>
    <tr>
        <td><img src="docs/images/survey-sys.jpg"/></td>
        <td><img src="docs/images/survey-post.jpg"/></td>
    </tr>
</table>
