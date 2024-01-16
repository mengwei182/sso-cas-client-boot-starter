### 接入说明
该客户端以cas协议为基础，修改并支持了前后端分离架构项目的验证和流转。
Spring项目请配置扫描包路径org.example，加载客户端功能。
```
<!--单点登录cas客户端-->
<dependency>
    <groupId>org.example</groupId>
    <artifactId>sso-cas-client-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```
### 接入流程
#### 引入jar包
直接添加sso-cas-client-boot-starter.jar包到项目或者安装到maven仓库后再引用
#### 配置参数
```
sso:
  cas:
    enable: true
    server:
      loginUrl: http://localhost/web/login.html
      logoutUrl: http://localhost/sso/logout
      urlPrefix: http://localhost/sso/
      serverName: http://localhost/cas/
      redirectUrl: http://localhost/web/cas.html
      ignoreUrls: /test/url1,/test/url2
```
#### 参数说明

- **sso.cas.enable**

开启或关闭cas客户端功能，缺省默认开启

- **sso.cas.server.loginUrl**

sso server提供的登录地址全路径。参数值不能为空

- **sso.cas.server.logoutUrl**

sso server提供的登出地址全路径，配置该参数后在客户端登出功能中需要做以下任务：

1. 注销客户端项目中已生成的用户token（如有）
2. 使用该参数把登出功能转到sso server，sso server会删除已持有的用户登录信息，并把登出功能转到客户端，客户端自动删除用户session
- **sso.cas.server.urlPrefix**

sso server的路径前缀，用作校验ticket，如sso server提供了验证ticket的路径为/p3/service/Validate，则前缀需要配置为：http://localhost:8080/sso/cas/。参数值不能为空

- **sso.cas.server.serverName**

cas客户端服务地址前缀，用作sso server回调cas客户端服务使用。参数值不能为空l

- **sso.cas.server.redirectUrl**

cas客户端前端页面地址，用作sso server回调前后端分离架构的项目使用， sso server验证登录成功后会跳转到该参数值所在的页面。配置后会覆盖sso.cas.server.serverName参数l

- **sso.cas.server.ignoreUrls**

白名单，多路径以英文逗号分隔
注：

1. 以上参数已封装到sso-cas-client-boot-starter.jar包中的org.example.properties.CasProperties类
2. 由于ajax请求无法自动重定向跳转页面，所以客户端会直接返回401状态并把跳转到sso server的地址写入response返回值，返回值格式如下：
```
{
    "data": "http://xxx",
    "message": "",
    "code": 401
}
```
code为固定值，401表示需要重定向 ，此时需要前端自行处理该状态和返回值并手动跳转到sso server，比如前端使用windows.location.href = xhr.responseText进行跳转