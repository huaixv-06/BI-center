package com.yupi.springbootinit.bizmq;

import com.rabbitmq.client.Channel;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.constant.BiMqConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.manager.AiManager;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.service.ChartService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class BiMessageConsumer {

    @Resource
    private ChartService chartService;

    @Resource
    private AiManager aiManager;

    /**
     * 接收消息的方法
     *
     * @param message      接收到的消息内容，是一个字符串类型
     * @param channel      消息所在的通道，可以通过该通道与 RabbitMQ 进行交互，例如手动确认消息、拒绝消息等
     * @param deliveryTag  消息的投递标签，用于唯一标识一条消息
     */
    // 使用@SneakyThrows注解简化异常处理
    @SneakyThrows
    @RabbitListener(queues = {BiMqConstant.BI_QUEUE}, ackMode = "MANUAL")
    // @Header(AmqpHeaders.DELIVERY_TAG) 用于从消息头中获取投递标签(deliveryTag),
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        if (StringUtils.isBlank(message)) {
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "消息内容为空");
        }
        long chartId = Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        if (chart == null) {
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图表为空");
        }
        // 先修改图标执行任务状态为“执行中”
        Chart updateChart = new Chart();
        updateChart.setId(chart.getId());
        // 设置任务状态为执行中
        updateChart.setStatus("running");
        boolean Result = chartService.updateById(updateChart);
        if (!Result) {
            channel.basicNack(deliveryTag, false, false);
            handleChartUpdateError(chart.getId(), "修改图表状态失败");
            return;
        }
        // 调用 AI
        // String result = aiManager.doChat(CommonConstant.BI_MODAL_ID, chartService.buildUserInput(chart));
        String userInput = chartService.buildUserInput(chart);
        String result = aiManager.sendMsgToXingHuo(true, userInput);
        String[] splits = result.split("'【【【【【'");
        if (splits.length < 3) {
            channel.basicNack(deliveryTag, false, false);
            handleChartUpdateError(chart.getId(), "图表生成失败");
            return;
        }
        String genChart = splits[1].trim();
        String genResult = splits[2].trim();
        // 调用AI得到结果之后，再更新一次
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chart.getId());
        updateChartResult.setGenChart(genChart);
        updateChartResult.setGenResult(genResult);
        updateChartResult.setStatus("success");
        boolean updateByIdResult = chartService.updateById(updateChartResult);
        if (!updateByIdResult) {
            channel.basicNack(deliveryTag, false, false);
            handleChartUpdateError(chart.getId(), "修改图表状态失败");
        }
        // 使用日志记录器打印接收到的消息内容
        log.info("receiveMessage message = {}", message);
        // 手动确认消息，表示消息已经被成功处理
        channel.basicAck(deliveryTag, false);
    }

    @SneakyThrows
    @RabbitListener(queues = {BiMqConstant.BI_DEAD_QUEUE}, ackMode = "MANUAL")
    public void receiveDeadLetterMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        if (StringUtils.isBlank(message)) {
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "死信消息内容为空");
        }

        long chartId = Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        if (chart == null) {
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图表未找到");
        }

        // 处理死信
        Chart updateChart = new Chart();
        updateChart.setId(chart.getId());
        // 设置任务状态为执行中
        updateChart.setStatus("failed");
        boolean result = chartService.updateById(updateChart);
        if (!result) {
            channel.basicNack(deliveryTag, false, false);
            handleChartUpdateError(chart.getId(), "修改图表状态失败");
            return;
        }

        // 使用日志记录器打印接收到的死信消息内容
        log.info("receiveDeadLetterMessage message = {}", message);

        // 手动确认消息，表示消息已经被成功处理
        channel.basicAck(deliveryTag, false);
    }

    private void handleChartUpdateError(Long chartId, String execMessage) {
        Chart updateChart = new Chart();
        updateChart.setId(chartId);
        updateChart.setStatus("failed");
        updateChart.setExecMessage(execMessage);
        boolean updateByIdResult = chartService.updateById(updateChart);
        if (!updateByIdResult) {
            log.error("修改图表状态失败" + chartId + "," + execMessage);
        }
    }

}
