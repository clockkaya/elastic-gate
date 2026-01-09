package com.sama.notice.sharding;

import com.sama.api.notice.object.DO.NoticeInfoRetentionDO;
import com.sama.api.notice.object.DO.NoticeLogDO;
import com.sama.notice.service.NoticeInfoRetentionService;
import com.sama.notice.service.NoticeLogService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Date;

/**
 * @author: huxh
 * @description:
 * @datetime: 2024/8/21 9:02
 */
// @Order(value = -1)
@Component
public class ShardingTablesLoadRunner implements ApplicationRunner {

    private final static Logger logger = LogManager.getLogger();

    @Resource
    NoticeInfoRetentionService noticeInfoRetentionService;

    @Resource
    NoticeLogService noticeLogService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        NoticeInfoRetentionDO initQuery = new NoticeInfoRetentionDO();
        initQuery.setCreateTime(new Date());
        noticeInfoRetentionService.selectOne(initQuery);

        NoticeLogDO initQuery2 = new NoticeLogDO();
        initQuery2.setCreateTime(new Date());
        noticeLogService.selectOne(initQuery2);

        logger.info(">>>>>>>>>> 【ShardingTablesLoadRunner】缓存已有分表成功 <<<<<<<<<<");
    }
}
