## 项目介绍

基于 Spring Boot + MQ + AIGC 的智能数据分析平台。

区别于传统 BI，用户只需要导入原始数据集、并输入分析诉求，就能自动生成可视化图表及分析结论，实现数据分析的降本增效。

AIGC：AI 生成内容

## 基础流程

客户端输入分析诉求和原始数据，向业务后端发送请求。业务后端将请求事件放入消息队列，并为客户端生成取餐号，让要生成图表的客户端去排队，消息队列根据 AI 服务负载情况，定期检査进度，如果 AI 服务还能处理更多的图表生成请求，就向任务处理模块发送消息。

任务处理模块调用 AI 服务处理客户端数据，AI 服务异步生成结果返回给后端并保存到数据库，当后端的 AI 服务生成完毕后，可以通过向前端发送通知的方式，或者通过业务后端监控数据库中图表生成服务的状态，来确定生成结果是否可用。若生成结果可用，前端即可获取并处理相应的数据，最终将结果返回给客户端展示。(在此期间，用户可以去做自己的事情)

![image](https://github.com/user-attachments/assets/dd230329-7005-4895-bef0-25d7e253cee8)

## 技术选型

### 后端

- Java Spring Boot
- MySQL 数据库
- MyBatis-Plus 及 MyBatis X 自动生成
- Redis + Redisson 限流
- RabbitMQ 消息队列
- 讯飞星火 AI SDK（AI 能力）
- JDK 线程池及异步化
- Swagger + Knife4j 接口文档生成
- Hutool、Apache Common Utils 等工具库

项目在本地启动后可访问 http://localhost:端口号/api/doc.html#/home 打开 Swagger + Knife4j 自动生成的接口文档
