package com.sama.notice.service.sharding;

import com.alibaba.fastjson2.JSON;
import com.core4ct.utils.DataUtils;
import com.core4ct.utils.DateUtils;
import com.sama.api.notice.object.DO.NoticeInfoRetentionDO;
import com.sama.notice.service.NoticeInfoRetentionService;
import com.sama.notice.service.SC.SCDataProcessServiceTest;
import jakarta.annotation.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Lazy;

import javax.sql.DataSource;
import java.util.Date;
import java.util.List;

/**
 * @author: huxh
 * @description:
 * @datetime: 2024/8/14 10:36
 */
@SpringBootTest
public class MockShardingTest {

    private final static Logger logger = LogManager.getLogger(SCDataProcessServiceTest.class);

    @Resource
    NoticeInfoRetentionService noticeInfoRetentionService;

    @Lazy
    @Autowired
    private DataSource shardingSphereDataSource;

    /**
     * 通过
     *
     * 发现问题1：只有初次刷新缓存才受到范围限制，其它时候并不受影响
     * 解决方案1：提示开发人员自行确认是否要严格限定
     * 发现问题2：全局id
     * 解决方案2：雪花算法（无需修改DDL和Long，去掉自增）
     */
    @Test
    public void createShardingTest(){
        NoticeInfoRetentionDO inputDO = getDOWithCreateTime(null);
        inputDO.setBusinessSystem("normal insert");
        logger.info("createShardingTest 入参 -----------> \n" + JSON.toJSONString(inputDO));

        NoticeInfoRetentionDO addDO = noticeInfoRetentionService.createSharding(inputDO);
        logger.info("createShardingTest 返回 -----------> \n" + JSON.toJSONString(addDO));
    }

    /**
     * 通过
     *
     * 说明：如果没有设置 Sharding Column / create_time，会插入当天的表（虽然不知道是怎么实现的）
     */
    @Test
    public void createShardingWithoutShardingColumnTest(){
        NoticeInfoRetentionDO inputDO = new NoticeInfoRetentionDO();
        inputDO.setBusinessSystem("no sharding column");
        logger.info("createShardingWithoutShardingColumnTest 入参 -----------> \n" + JSON.toJSONString(inputDO));

        NoticeInfoRetentionDO addDO = noticeInfoRetentionService.createSharding(inputDO);
        logger.info("createShardingWithoutShardingColumnTest 返回 -----------> \n" + JSON.toJSONString(addDO));
    }

    /**
     * 通过
     *
     * 发现问题：先 select id from 唯一目标表，再 select * from union all 表，虽然id设置了索引，但是还是浪费了效能
     * 解决方案：框架的问题 + BaseModel的问题，无需解决
     */
    @Test
    public void readShardingTest(){
        NoticeInfoRetentionDO query = getDOWithCreateTime("2024-08-11 00:00:18");
        logger.info("readShardingTest 入参 -----------> \n" + JSON.toJSONString(query));

        List<NoticeInfoRetentionDO> res = noticeInfoRetentionService.readSharding(query);
        // 92
        logger.info("readShardingTest 返回{}条 -----------> \n" + JSON.toJSONString(res), res.size());
    }

    /**
     * 通过
     *
     * 说明：如果没有设置 Sharding Column / create_time，会一个一个select id 遍历表查
     */
    @Test
    public void readShardingWithoutShardingColumnTest(){
        NoticeInfoRetentionDO query = new NoticeInfoRetentionDO();
        // query.setId(1033700284188065792L);
        query.setBusinessSystem("no sharding column");
        logger.info("readShardingWithoutShardingColumnTest 入参 -----------> \n" + JSON.toJSONString(query));

        List<NoticeInfoRetentionDO> res = noticeInfoRetentionService.readSharding(query);
        logger.info("readShardingWithoutShardingColumnTest 返回{}条 -----------> \n" + JSON.toJSONString(res), res.size());
    }

    /**
     * 通过
     *
     * 发现问题：read不存在的 Sharding Column / create_time，会走doSharding建表
     * 解决：提示开发注意
     */
    @Test
    public void readShardingNotExistedTest(){
        NoticeInfoRetentionDO notExistedDO = getDOWithCreateTime("2024-06-01 12:00:00");
        logger.info("readShardingNotExistedTest 入参 -----------> \n" + JSON.toJSONString(notExistedDO));

        List<NoticeInfoRetentionDO> res = noticeInfoRetentionService.readSharding(notExistedDO);
        logger.info("readShardingNotExistedTest 返回{}条 -----------> \n" + JSON.toJSONString(res), res.size());
    }

    /**
     * 通过
     *
     * 说明：对 Sharding Column / create_time 同时设置精确值和范围，会走精确路由！
     */
    @Test
    public void readRangeShardingTest(){
        // NoticeInfoRetentionDO query = getDOWithCreateTime(null);
        NoticeInfoRetentionDO query = new NoticeInfoRetentionDO();
        query.setId(1033422284339019776L);
        logger.info("readRangeShardingTest 入参 -----------> \n" + JSON.toJSONString(query));

        List<NoticeInfoRetentionDO> res = noticeInfoRetentionService.readRangeSharding(query);
        logger.info("readRangeShardingTest 返回{}条 -----------> \n" + JSON.toJSONString(res), res.size());
    }

    /**
     * 通过
     *
     * 说明：同readShardingWithoutShardingColumnTest逻辑
     */
    @Test
    public void readRangeShardingWithoutShardingColumnTest(){
        NoticeInfoRetentionDO query = new NoticeInfoRetentionDO();
        query.setBusinessSystem("时钟差");
        logger.info("readRangeShardingTest 入参 -----------> \n" + JSON.toJSONString(query));

        List<NoticeInfoRetentionDO> res = noticeInfoRetentionService.readRangeSharding(query);
        logger.info("readRangeShardingTest 返回{}条 -----------> \n" + JSON.toJSONString(res), res.size());
    }

    /**
     * 通过
     *
     * 发现问题：不可以 update Sharding Column / create_time
     * 解决方案：Assert.isNull
     */
    @Test
    public void updateShardingTest(){
        NoticeInfoRetentionDO input = new NoticeInfoRetentionDO();
        input.setId(1033699621148295168L);
        input.setBusinessSystem("no warn");
        logger.info("updateShardingTest 入参 -----------> \n" + JSON.toJSONString(input));

        NoticeInfoRetentionDO updatedDO = noticeInfoRetentionService.updateSharding(input);
        logger.info("updateShardingTest 返回 -----------> \n" + JSON.toJSONString(updatedDO));
    }

    /**
     * 通过
     *
     * 发现问题：直接用del()方法时，会 update Sharding Column / create_time
     * 解决：自己重写
     */
    @Test
    public void deleteShardingTest(){
        NoticeInfoRetentionDO deletedDO = noticeInfoRetentionService.deleteSharding(1033751173095489536L);
        logger.info("deleteShardingTest 返回 -----------> \n" + JSON.toJSONString(deletedDO));
    }

    /**
     * 通过
     *
     * 标志位只出现一次，说明doSharding的参数 var1, var2是全局唯一的
     */
    @Test
    public void isCacheTest(){
        for(int i = 0; i < 5; i++){
            logger.info("第{}次，检验缓存刷新逻辑", i);
            // readShardingTest();
            createShardingTest();
        }
    }

    @Test
    public void dataSourceTest(){
        System.out.println(shardingSphereDataSource);
    }

    /**
     *
     * @param dateStr   "2024-10-01 10:05:00"
     * @return  NoticeInfoRetentionDO
     */
    private NoticeInfoRetentionDO getDOWithCreateTime(String dateStr){
        NoticeInfoRetentionDO noticeInfoRetentionDO = new NoticeInfoRetentionDO();
        if (DataUtils.isNotEmpty(dateStr)){
            noticeInfoRetentionDO.setCreateTime(DateUtils.stringToDate(dateStr));
        } else {
            noticeInfoRetentionDO.setCreateTime(new Date());
        }
        return noticeInfoRetentionDO;
    }

}
