package com.yupi.springbootinit.job;


import com.yupi.springbootinit.constant.CommonConstant;
import com.yupi.springbootinit.manager.AiManager;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.service.ChartService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.*;

/**
 *  队满补偿任务
 */
@Component
@Slf4j
public class DeadRequestHandleJob {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ChartService chartService;

    @Resource
    private AiManager aiManager;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private RetryQueueService retryQueueService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Scheduled(cron = "0 0/5 * * * *")
    public void doCatchRecommendUser(){
        RLock lock = redissonClient.getLock("biCenter:deadRequestHandleJob:chartHandle:lock");
        try {
            if (lock.tryLock(0,-1,TimeUnit.MILLISECONDS)){
                Long pollId = retryQueueService.poll();
                if (pollId == null) {
                    return;
                }
                Chart chart = chartService.getById(pollId);
                if (chart == null) {
                    return;
                }
                String userInput = chartService.buildUserInput(chart);

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    // 先修改图标执行任务状态为“执行中”
                    Chart updateChart = new Chart();
                    updateChart.setId(chart.getId());
                    // 设置任务状态为执行中
                    updateChart.setStatus("running");
                    boolean b = chartService.updateById(updateChart);
                    if (!b) {
                        handleChartUpdateError(chart.getId(), "修改图表状态失败");
                        return;
                    }

                    // 调用 AI
                    String result = aiManager.doChat(CommonConstant.BI_MODAL_ID, userInput);
                    String[] splits = result.split("【【【【【");
                    if (splits.length < 3) {
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
                        handleChartUpdateError(chart.getId(), "修改图表状态失败");
                    }
                }, threadPoolExecutor);

                // 记录超时任务的Future以便我们可以取消它
                ScheduledFuture<?> scheduledFuture = scheduler.schedule(() -> {
                    // 超时后的处理逻辑
                    // 修改图表状态为失败并抛出超时异常
                    Chart timeoutChart = new Chart();
                    timeoutChart.setId(chart.getId());
                    if (timeoutChart.getStatus().equals("success")){
                        return;
                    }
                    timeoutChart.setStatus("failed");
                    chartService.updateById(timeoutChart); // 假设这个方法更新图标状态
                    log.error("任务执行超时，已终止");
                    // 终止异步任务
                    future.cancel(true);
                }, 10, TimeUnit.MINUTES); // 设置10分钟的超时时间

                // 处理异步任务的异常情况
                future.exceptionally(ex -> {
                    if (ex instanceof CancellationException && !scheduledFuture.isCancelled()) {
                        // 如果异步任务被超时任务取消，这里不处理
                        return null;
                    } else if (ex.getCause() instanceof RejectedExecutionException) {
                        log.error("任务队列满，无法执行任务: {}", ex.getCause().getMessage());
                        Long id = chart.getId();
                        retryQueueService.offer(id); // 将 ID 放入重试队列
                    } else {
                        // 处理其他异常情况
                        log.error("异步任务发生异常", ex);
                    }
                    return null;
                });
            }
        } catch (InterruptedException e) {
            log.error("get lock error",e);
        } finally {
            // 只能释放自己的锁
            if (lock.isHeldByCurrentThread()){
                lock.unlock();
            }
        }
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
