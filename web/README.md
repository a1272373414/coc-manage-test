前端启动方式
由于您的项目是零构建前端（纯 HTML/CSS/JS），有以下几种启动方式：

方式 1：VS Code Live Server（推荐）⭐
安装 VS Code 扩展 "Live Server"
右键点击 web/index.html → Open with Live Server
优点：热更新、自动刷新、模拟真实服务器环境
--------------------------------------------------------------------------------------------
方式 2：命令行启动 HTTP 服务器
使用 Python（已内置，无需安装）

# 进入 web 目录
cd d:\work\my\codebuddy_test\coc_manager_test01\web

# Python 3
python -m http.server 5500
访问地址：http://localhost:8080/index.html

使用 Node.js http-server（需先安装）

# 全局安装 http-server（只需一次）
npm install -g http-server

# 进入 web 目录并启动
cd d:\work\my\codebuddy_test\coc_manager_test01\web
http-server -p 8080 -c-1
参数说明：

-p 8080：端口
-c-1：禁用缓存（开发时建议使用）
访问地址：http://localhost:8080
--------------------------------------------------------------------------------------------
方式 3：直接双击文件
双击 web/index.html 即可在浏览器中打开

⚠️ 注意：这种方式可能有跨域限制，如果遇到 API 调用问题，请改用上述方式 1 或 2

方式 4：Spring Boot 集成静态资源（生产部署）
如果您想通过后端服务同时托管前端页面：


# 将 web/ 目录下的文件复制到 Spring Boot 的 static 资源目录
cp -r web/* src/main/resources/static/
然后启动后端即可访问：http://localhost:8080

