package com.sama.notice.service.SC;

import com.alibaba.nacos.common.utils.UuidUtils;
import com.core4ct.utils.TimeUtils;
import com.sama.api.notice.object.DO.NoticeInfoDO;
import com.sama.notice.service.SCDataProcessService;
import com.sama.notice.utils.KafkaClientUtil;
import jakarta.annotation.Resource;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * @author: huxh
 * @description:
 * @datetime: 2024/6/13 16:06
 */
@SpringBootTest
public class SCDataProcessServiceTest {

    private final static Logger logger = LogManager.getLogger(SCDataProcessServiceTest.class);

    @Resource
    SCDataProcessService scDataProcessService;

    @Test
    public void allTenantOrgCodeCacheOperationsTest() throws InterruptedException {
        // 初始值
        scDataProcessService.insertTenantCache(samePrefixDiffOrgCode());
        scDataProcessService.printCacheSizeAndExamples();

        // 模拟每秒同时有写入/写出操作
        for (int i = 0; i < 3; i++){
            Thread.sleep(1000);
            LocalDateTime endTime = LocalDateTime.now();
            // 性能峰值大约在2w/s
            scDataProcessService.insertTenantCache(generateRandomNoticeList(30000, endTime.minusDays(3), endTime, 1,"200550", 11L));
            scDataProcessService.cleanTenantCacheCron();
            scDataProcessService.printCacheSizeAndExamples();
        }

        // 不间断的清理并查看容器情况
        for (int i = 0; i < 20; i++){
            Thread.sleep(1000);
            scDataProcessService.cleanTenantCacheCron();
            scDataProcessService.printCacheSizeAndExamples();
            scDataProcessService.getTenantCache("2005", 20, null);
        }
    }

    private List<NoticeInfoDO> samePrefixDiffOrgCode() {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = LocalDateTime.now().minusDays(1);
        List<NoticeInfoDO> listA = generateRandomNoticeList(10000, startTime, endTime,0, "200510011001", 11L);
        List<NoticeInfoDO> listB = generateRandomNoticeList(20000, startTime, endTime,0, "200520022002", 10L);
        List<NoticeInfoDO> listC = generateRandomNoticeList(30000, startTime, endTime,1, "20053003", 11L);
        List<NoticeInfoDO> listD = generateRandomNoticeList(40000, startTime, endTime,1, "20055005", -5L);
        List<NoticeInfoDO> combinedList = Stream.of(listA, listB, listC, listD).flatMap(List::stream).collect(Collectors.toList());
        return combinedList;
    }

    private List<NoticeInfoDO> generateRandomNoticeList(int size, LocalDateTime startDate, LocalDateTime endDate,
                                                        Integer noticeLogClass, String unionOrgCode, Long noticeObject) {
        List<NoticeInfoDO> noticeList = new ArrayList<>(size);
        long startSeconds = startDate.toEpochSecond(ZoneOffset.ofHours(8));
        long endSeconds = endDate.toEpochSecond(ZoneOffset.ofHours(8));
        for (int i = 0; i < size; i++) {
            NoticeInfoDO notice = new NoticeInfoDO();
            notice.setNoticeLogClass(noticeLogClass);
            if (noticeLogClass == 0) {
                notice.setOrgCode(unionOrgCode);
            } else {
                notice.setMissionOrgCode(unionOrgCode);
            }
            notice.setNoticeObject(noticeObject);
            long randomSeconds = ThreadLocalRandom.current().nextLong(startSeconds, endSeconds);
            LocalDateTime randomDateTime = LocalDateTime.ofEpochSecond(randomSeconds, 0, ZoneOffset.ofHours(8));
            Date noticeTime = TimeUtils.localDateTime2Date(randomDateTime);
            notice.setNoticeTime(noticeTime);
            notice.setCreateTime(new Date());
            notice.setNoticeId(UuidUtils.generateUuid());
            noticeList.add(notice);
        }
        return noticeList;
    }

    /**
     * 异步方法
     * @return
     */
    private Future<Object> redisCount(){
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        // 排列组合
        List<NoticeInfoDO> listA = generateRandomNoticeList(1000, startTime, endTime,0, "200510011001", 11L);
        List<NoticeInfoDO> listB = generateRandomNoticeList(1000, startTime, endTime,0, "200520022002", 10L);
        List<NoticeInfoDO> listC = generateRandomNoticeList(1000, startTime, endTime,1, "20053003", 11L);
        List<NoticeInfoDO> listD = generateRandomNoticeList(1000, startTime, endTime,1, "20101001", -5L);
        List<NoticeInfoDO> listE = generateRandomNoticeList(1000, startTime, endTime,0, "2010100011001", 1L);
        List<NoticeInfoDO> combinedList = Stream.of(listA, listB, listC, listD, listE).flatMap(List::stream).collect(Collectors.toList());
        return scDataProcessService.dotWithinInterval(combinedList);
    }

    @Test
    public void allDotStatisticsTest() throws InterruptedException {
        // 实际是每5分钟一次
        for (int i = 0; i < 5; i++){
            logger.info("allDotStatisticsTest：第{}/5次......", i+1);
            redisCount(); // 异步
            Thread.sleep( 60 * 1000L);
            scDataProcessService.persistentDotStatisticsCron();
            scDataProcessService.getDotStatistic("2005");
        }
    }

    @Test
    public void duration() throws InterruptedException {
        long startTime = System.nanoTime();
        try {
            Future<Object> res = redisCount();
            while (!"DONE".equals(res.get())){
                Thread.sleep(1000L);
            }
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        // 将纳秒转换为更易读的单位，比如毫秒
        double seconds = duration / 1_000_000_000.0;
        System.out.printf("方法执行总耗时: %.3f 秒%n", seconds);
    }

    @Resource
    KafkaClientUtil kafkaClientUtil;

    @Test
    public void localhostKafkaTunnelTest() throws InterruptedException {
        Properties props = new Properties();
        // 本地54跳板机隧道
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "172.31.10.214:29091,172.31.10.214:29092,172.31.10.214:29093");
        props.put("sasl.mechanism", "PLAIN");
        props.put("security.protocol", "SASL_PLAINTEXT");
        props.put("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"operator\" password=\"2Qame1Que1k#pF@x\";");
        List<NoticeInfoDO> input = Lists.newArrayList();
        scDataProcessService.pollAndParseFromKafka(input, kafkaClientUtil.createSCConsumer(props));
        Thread.sleep(5 * 60 * 1000L);
    }

    @Test
    public void entrypointTest() throws InterruptedException {
        // scDataProcessService.entrypoint();
        Thread.sleep(10 * 60 * 1000L);
    }

    @Test
    public void keepRetentionTest() throws InterruptedException {
        scDataProcessService.keepRetention(generateRandomNoticeList(50000, LocalDateTime.now().minusDays(1), LocalDateTime.now(), 0, "200510011001", 11L));
        Thread.sleep(1 * 60 * 1000L);
    }

}
