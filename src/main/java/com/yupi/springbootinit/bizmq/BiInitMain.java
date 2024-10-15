package com.yupi.springbootinit.bizmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.yupi.springbootinit.constant.BiMqConstant;

import java.util.HashMap;
import java.util.Map;

/**
 * 用于创建测试程序用到的交换机和队列（只用在程序启动前执行一次）
 */
public class BiInitMain {

    public static void main(String[] args) {
        try {
            // 创建连接工厂
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            // 创建连接
            Connection connection = factory.newConnection();
            // 创建通道
            Channel channel = connection.createChannel();

            // 定义交换机的名称为"bi_exchange"
            String exchangeName = BiMqConstant.BI_EXCHANGE;
            // 创建队列
            String queueName = BiMqConstant.BI_QUEUE;
            // 设置路由键为 "bi_routing_Key"
            String routingKey = BiMqConstant.BI_ROUTING_KEY;
            // 声明交换机，指定交换机类型为 direct
            channel.exchangeDeclare(exchangeName, "direct");

            // 设置死信交换机的名称
            String deadLetterExchange = BiMqConstant.BI_DEAD_EXCHANGE;
            String deadLetterQueue = BiMqConstant.BI_DEAD_QUEUE;
            String deadLetterRoutingKey = BiMqConstant.BI_DEAD_ROUTING_KEY;

            // 创建主队列的属性
            Map<String, Object> arg = new HashMap<>();
            arg.put("x-dead-letter-exchange", deadLetterExchange); // 设置死信交换机
            arg.put("x-dead-letter-routing-key", deadLetterRoutingKey); // 设置路由键
            arg.put("x-message-ttl", 300000); // 设置主队列消息的生存时间（毫秒）
            arg.put("x-max-length", 100); //设置主队列的最大消息数

            // 声明队列，设置队列持久化、非独占、非自动删除，并传入额外的参数 arg
            channel.queueDeclare(queueName, true, false, false, arg);

            // 声明死信交换机
            channel.exchangeDeclare(deadLetterExchange, "direct");
            // 声明死信队列
            channel.queueDeclare(deadLetterQueue, true, false, false, null);

            // 将死信队列绑定到死信交换机
            channel.queueBind(deadLetterQueue, deadLetterExchange, deadLetterRoutingKey);
            // 将主队列绑定到主交换机，指定路由键为 "bi_routing_Key"
            channel.queueBind(queueName, exchangeName, routingKey);

            System.out.println("交换机和队列创建成功");
        } catch (Exception e) {
            // 异常处理
        }
    }
}


