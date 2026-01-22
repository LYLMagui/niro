## 更新说明

### 20230927

第一版

## 前提条件

接入方需要准备以下信息:
- 注册C5GAME账号: 您需要登录[C5GAME官网](https://www.c5game.com)或者下载[C5GAME APP](https://sj.qq.com/appdetail/com.imbastar.c5game)进行注册账号. 后续如果迭代了购买功能, 您需要预充值账户余额, 才能完成购买饰品的操作, 需要您自行通过接口进行余额查询, 以及进行充值, 保证账户余额充足, 以免影响使用. 
- app-key: 所有接口都需要在 query parameter 中填写您的 app-key, app-key可以在[个人中心-api管理](https://www.c5game.com/user-center/user/open-api)申请开通.  使用示例: https://openapi.c5game.com?app-key=appkey-example

## 限流说明
默认限流次数为50qps,部分接口会有特殊标注限流次数,请在参阅具体接口的限流说明.

## 请求结构
调用开放平台的接口时,是指通过向我们的服务地址发送请求, 需要按照接口的地址, 请求方式, 请求参数构造请求, 否则不能调用成功. 一条查询余额的请求示例如下:

```shell
curl -X GET "http://openapi.c5game.com/merchant/account/v1/balance?app-key=app-key-example"
```

###  服务地址
C5GAME开放平台已有的服务接入地址如下

|服务地域|域名|备注|
|:----|:---|:-----|
|国内外|https://openapi.c5game.com|开放平台OpenAPI接入地址|

### 通信协议
提供的所有接口均通过 HTTPS 进行通信，提供高安全性的通信通道。

### 请求方式
根据各个接口的具体需求，选择 GET 或 POST 方式发起请求。

### 请求参数
在发起请求时，请求体中可能会包含两类参数：公共请求参数和接口特有的业务参数。
- 公共请求参数是每一个接口需要包含的，目前为query参数中的app-key。
- 接口特有的业务参数是各个接口特有的，参考各接口的参数描述。

### 字符编码
请求及返回结果使用 UTF-8 的字符集进行编码。


## 签名机制
目前无

## 返回结果
API请求返回以下结果
调用成功:

```json
{
  "success": true,
  "data": {
    "userId": 111,
    "balance": 100.21
  },
  "errorCode": 0,
  "errorMsg": "",
  "errorData": {},
  "errorCodeStr": ""
}
```
调用失败:
```json
{
  "errorCode": 400001,
  "errorMsg": "请输入正确的 app-Key",
  "success": false
}
```

|字段|类型|是否一定返回|说明|
|:----|:---|:---|:----|
|success|Boolean|是|本次请求是否成功,未true代表服务端流程走完,没有异常;如果为false,则需要去看errorCode字段的错误码,根据此错误码业务上进行处理|
|errorCode|Int32|是|错误码,当success为true时,errorCode必为0;当success为false时,errorCode必非0|
|errorMsg|String|是|错误消息,当success为true时,errorMsg为空字符串;当success为false时,errorMsg错误描述|
|errorData|Object|否|发生错误时的返回数据,此结构类型不定,根据各个接口返回不同的类型|
|errorCodeStr|String|否|错误的英文简要描述,只有当success为false时, 才可能有非空字符串返回|
|data|Object|否|请求成功时返回的业务数据,结构根据每个接口不同|

## 名词解释
|名字|解释|
|:---|:--|
|服务域名|我们提供服务的域名, 生产环境为http://openapi.c5game.com|
|app-key|我方用来鉴权的密钥,代表您在我们平台的凭证,请妥善保存,泄漏后别人也可以使用这个密钥进行关键操作.|
|appId|Steam 官方对于游戏的唯一标识，目前我们平台用到的，DOTA2 -> 570,CSGO -> 730,TF2 -> 440,PayDay2->218620,CSOL -> 2504460|
