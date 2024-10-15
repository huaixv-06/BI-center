package com.yupi.springbootinit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.springbootinit.model.entity.Chart;
import org.apache.commons.lang3.StringUtils;

/**
* 针对表【chart(图表信息表)】的数据库操作Service
*/
public interface ChartService extends IService<Chart> {

    /**
     * 构建用户输入
     * @param chart 图表对象
     * @return 用户输入字符串
     */
    String buildUserInput(Chart chart);

}
