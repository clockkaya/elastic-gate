package com.sama.notice.service;

import cn.hutool.core.convert.Convert;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.core4ct.exception.GenericException;
import com.core4ct.utils.DataUtils;
import com.core4ct.utils.DateUtils;
import com.core4ct.utils.TimeUtils;
import com.core4ct.utils.redis.RedisUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sama.api.notice.constant.NoticeObject;
import com.sama.api.notice.object.DO.NoticeDotStatisticsDO;
import com.sama.api.notice.object.DO.NoticeInfoDO;
import com.sama.api.notice.object.DO.SC.ElasticBoundedPrioritySet;
import com.sama.api.notice.object.DO.SC.NoticeInfoComparator;
import com.sama.api.notice.object.DO.SC.SCDotPivotIdx;
import com.sama.api.notice.object.DTO.SCTrendBO;
import com.sama.notice.config.NoticeConfig;
import com.sama.notice.utils.KafkaClientUtil;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author: huxh
 * @description: Safety Center 数据处理服务；4.1.4 之后不再从notice消费kafka给SC提供查询接口
 * @datetime: 2024/6/12 16:02
 */
// @RefreshScope
// @RefreshScope注解导致了服务的重新加载，调用两个线程上的run()方法，产生两个kafka client实例
@Deprecated
@Service
public class SCDataProcessService {

    private final static Logger logger = LogManager.getLogger(SCDataProcessService.class);

    @Value("${notice.scdata.capacity.lower:20}")
    private int lowerCapacity;

    @Value("${notice.scdata.capacity.upper:1000}")
    private int upperCapacity;

    @Value("${notice.scdata.retention:false}")
    private Boolean retenetinoFlag;

    @Resource
    KafkaClientUtil kafkaClientUtil;

    private final static String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    private final static String YYYY_MM_DD_HH_00_00 = "yyyy-MM-dd HH:00:00";

    private final static List<Long> selectedNoticeObjects = Arrays.asList(
            NoticeObject.WAF,
            NoticeObject.SCAN,
            NoticeObject.LOG_AUDIT,
            NoticeObject.DATA_AUDIT,
            NoticeObject.EDR);

    @Resource
    NoticeConfig noticeConfig;

    @Resource
    KafkaConsumerService kafkaConsumerService;

    private final static Map<String, ElasticBoundedPrioritySet<NoticeInfoDO>> tenantCacheMap =  new ConcurrentHashMap<>();

    /**
     * shift, near
     * dot：可能早于，也可能晚于真实的redis fields（例如near是26：58，redis field可能是26:01/25：00也可能是24:05/20：00，所以把25:00作为dot）
     */
    private final static Map<String, Date> timeRecords = new HashMap<>();

    // @Resource(name = "insertTenantCacheTask")
    // ThreadPoolTaskExecutor insertTenantCacheTask;
    //
    // @Resource(name = "dotWithinIntervalTask")
    // ThreadPoolTaskExecutor dotWithinIntervalTask;
    //
    // @Resource(name = "cleanTenantCacheCron")
    // ThreadPoolTaskExecutor cleanTenantCacheCron;

//    @Resource(name = "subCleanTenantCacheCron")
//    ThreadPoolTaskExecutor subCleanTenantCacheCron;

    @Resource(name = "keepRetentionTask")
    ThreadPoolTaskExecutor keepRetentionTask;

    @Resource
    RedisUtils redisUtils;

    @Resource
    NoticeDotStatisticsService noticeDotStatisticsService;

    /**
     * kafka input 入口点
     */
    public void entrypoint(){
        logger.error("【废弃】 SCDataProcessService Deprecated!!!");

        try {
            KafkaConsumer<String, String> consumer = loadSCKafkaClient();
            while (true){
                try {
                    List<NoticeInfoDO> kafkaInput = Lists.newArrayList();
                    pollAndParseFromKafka(kafkaInput, consumer);
                    if (DataUtils.isNotEmpty(kafkaInput)){
                        insertTenantCache(kafkaInput);
                        dotWithinInterval(kafkaInput);
                    }
                } catch (Exception e) {
                    logger.error("SCDataProcessService 写入过程中发生异常：" + e.getMessage(), e);
                }
            }
        } catch (Exception e){
            logger.error("SCDataProcessService 写入过程中发生异常：" + e.getMessage(), e);
        }

    }

    private KafkaConsumer<String, String> loadSCKafkaClient(){
        String kafkaAddress = noticeConfig.getKafkaAddress4OriginalLog();
        String groupId = noticeConfig.getAsyncSaveGroupId();

        // 在getSCkafkaConfigMap中配置: a.如果消费者在5000ms内没有调用 poll 方法，Kafka将认为它已经失效; b.消费者将尝试拉取最多1000条消息
        Map<String, String> configHashMap = noticeConfig.getSCkafkaConfigMap();

        KafkaConsumer<String, String> consumer = kafkaClientUtil.getConsumer(kafkaAddress, groupId, configHashMap);
        consumer.subscribe(Lists.newArrayList(noticeConfig.getKafkaTopic4OriginalLog()));

        return consumer;
    }

    /**
     * 阻塞方法 (因为后续处理都是异步批量，所以这里不需要用队列优化)
     */
    public void pollAndParseFromKafka(List<NoticeInfoDO> noticeInfoDOList, KafkaConsumer<String, String> consumer){
        // c. 如果在10秒内没有新消息，poll 方法将返回
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        if (records.count() > 0) {
//            logger.info("SCDataProcessService 开始消费Kafka消息，本次数据量为：{}", records.count());

            records.forEach(record -> {
                String value = record.value();
                if (DataUtils.isNotEmpty(value)) {
                    try {
                        Map<String, Object> originalMap = JSON.parseObject(value, new TypeReference<Map<String, Object>>() {});
                        NoticeInfoDO noticeInfoDO = kafkaConsumerService.parseNoticeInfoDO(originalMap, value);
                        // 代替各方法的filter
                        if (noticeInfoDO.getNoticeObject() != null && selectedNoticeObjects.contains(noticeInfoDO.getNoticeObject())) {
                            noticeInfoDOList.add(noticeInfoDO);
                        }
                    } catch (Exception e){
                        String info = String.format("[Topic: %s][Partition:%d][Offset:%d][Key:%s][Message:%s]",
                                record.topic(), record.partition(), record.offset(), record.key(), record.value());
                        logger.error("parseNoticeInfoDO 解析Kafka消息时发生错误，原文：" + info, e);
                    }
                }
            });

            keepRetention(noticeInfoDOList);

        }

    }

    /**
     * 异步方法：插入缓存tenantCacheMap的处理
     * @param noticeInfoDOList 批量
     * @return Future<Object>
     */
    public Future<Object> insertTenantCache(List<NoticeInfoDO> noticeInfoDOList){
        Callable<Object> task = () ->{
            // 先区分租户
            Map<String, List<NoticeInfoDO>> groupedNotices = noticeInfoDOList.stream()
                    // .filter(notice -> selectedNoticeObjects.contains(notice.getNoticeObject()))
                    .collect(Collectors.groupingBy(notice ->
                            // 适配4.1.0
                            notice.getNoticeLogClass() == 0 ? "N" + notice.getOrgCode() : "T" + notice.getMissionOrgCode())
                    );

            // 再批量插入
            groupedNotices.forEach((unionOrgCode, data) -> {
                ElasticBoundedPrioritySet<NoticeInfoDO> set = tenantCacheMap.computeIfAbsent(unionOrgCode,
                        key -> new ElasticBoundedPrioritySet<>(new NoticeInfoComparator(), lowerCapacity, upperCapacity));
                set.addAll(data);
                tenantCacheMap.put(unionOrgCode, set);
            });

            return "DONE";
        };

        // Future<Object> res = insertTenantCacheTask.submit(task);
        Future<Object> res = new ThreadPoolTaskExecutor().submit(task);
        return res;
    }

    public synchronized void printCacheSizeAndExamples(){
        logger.info("当前 tenantCacheMap 键值共{}个，详情如下：", tenantCacheMap.size());
        tenantCacheMap.forEach((unionOrgCode, set) -> {
            logger.info("unionOrgCode:{}, size:{}, nearAndFar:{}", unionOrgCode, set.size(), set.nearAndFar());
        });
    }

    /**
     * 异步定时任务
     * 理论上来说，在此方法的父级线程池前已经groupby，所以execute不会高并发，但是可能由于其缓存队列设置的原因，可能存在并发问题。
     */
    public void cleanTenantCacheCron(){
        tenantCacheMap.forEach((unionOrgCode, set) -> {
            // cleanTenantCacheCron.execute(() -> {
            new ThreadPoolTaskExecutor().execute(() -> {
                try {
                    // 高频触发，用子线程池分担压力
                    set.truncateClean(new String[]{unionOrgCode});
                } catch (Exception e){
                    logger.error("捕获小异常一只，堆栈信息如下: ", e);
                    throw new GenericException(MessageFormat.format("cleanTenantCacheCron 清理{0}容器时发生错误！", unionOrgCode));
                }
            });
        });
    }

    /**
     * 同getSCRealTimeBroadcast方法
     * @param orgCodePrefix 租户前缀
     * @param size 取数
     * @param lastTime 偏移量（默认7天取值范围）
     * @return List<NoticeInfoDO>
     */
    public List<NoticeInfoDO> getTenantCache(String orgCodePrefix, Integer size, String lastTime){
        if (size > lowerCapacity){
            logger.warn("当前需要数已超出缓存的默认最小长度({})，可能无法满足取数要求！", lowerCapacity);
        }

        NoticeInfoDO reference = new NoticeInfoDO();
        Date initNoticeTime;
        if (DataUtils.isEmpty(lastTime)){
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            initNoticeTime = TimeUtils.localDateTime2Date(sevenDaysAgo);
        } else {
            initNoticeTime = DateUtils.stringToDate(lastTime);
        }
        reference.setNoticeTime(initNoticeTime);

        // 适配4.1.0
        List<NoticeInfoDO> parentRes = new CopyOnWriteArrayList<>();
        String regex = "^(T" + Pattern.quote(orgCodePrefix) + "|N" + Pattern.quote(orgCodePrefix) + "[A-Za-z\\d]*)$";
        Pattern pattern = Pattern.compile(regex);
        tenantCacheMap.forEach((unionOrgCode, set) -> {
            if (pattern.matcher(unionOrgCode).matches()) {
                // SCDataProcessTask.execute(() -> {
                    // 先收集所有
                    parentRes.addAll(set.fetchAccordingCondition(size, reference));
                // });
            }
        });
        // 再排序
        List<NoticeInfoDO> parentResResize = parentRes.stream().sorted(new NoticeInfoComparator()).limit(size).collect(Collectors.toList());
        logger.info("getTenantCache-成功处理租户缓存并返回{}条", parentResResize.size());

        return parentResResize;
    }


    /**
     * 异步方法：透视表（租户，原子能力，时间），利用Redis(INCRBY)完成Interval内的计数器功能
     * 这里是没有时间窗口的，随persistentDotStatisticsCron方法清空
     * @param noticeInfoDOList 批量
     * @return Future<Object>
     */
    public Future<Object> dotWithinInterval(List<NoticeInfoDO> noticeInfoDOList){
        Callable<Object> task = () -> {
            // 先归类计数
            Map<SCDotPivotIdx, Map<String, Long>> groupedNotices =  noticeInfoDOList.stream()
                    // .filter(notice -> selectedNoticeObjects.contains(notice.getNoticeObject()))
                    .collect(
                    // 第一层分类器：基于unionOrgCode和noticeObject
                    Collectors.groupingBy(outerClassifier->{
                                String unionOrgCode = outerClassifier.getNoticeLogClass() == 0 ? "N" + outerClassifier.getOrgCode() : "T" + outerClassifier.getMissionOrgCode();
                                return new SCDotPivotIdx(unionOrgCode, outerClassifier.getNoticeObject());
                                // 第二层分类器：基于dotTime
                            },Collectors.groupingBy(innerClassifier->{
                                // 精确到整5分钟
                                Date roundDotTime = forwardFiveMins(innerClassifier.getNoticeTime());
                                String dotTime = DateUtils.format(roundDotTime, YYYY_MM_DD_HH_MM_SS);
                                // 精确到小时
                                // String dotTime = DateUtils.format(innerClassifier.getNoticeTime(), YYYY_MM_DD_HH_00_00);
                                return dotTime;
                            }, Collectors.counting())
                    ));

            // 再插入Redis
            for (Map.Entry<SCDotPivotIdx, Map<String, Long>> entry : groupedNotices.entrySet()){
                String redisKey = String.join(":", "SC_DOT", entry.getKey().getUnionOrgCode(), entry.getKey().getNoticeObject().toString());
                // 可保证线程安全（用增减代替加锁）
                entry.getValue().forEach((redisField, count) -> {
                    redisUtils.hincr(redisKey, redisField, count);
                });
            }

            return "DONE";
        };

        // Future<Object> res = dotWithinIntervalTask.submit(task);
        Future<Object> res = new ThreadPoolTaskExecutor().submit(task);
        return res;
    }

    /**
     * 定时任务
     */
    // @Transactional(rollbackFor = Exception.class)
    public void persistentDotStatisticsCron(){
        // 对于Redis key：SC_DOT:unionOrgCode:noticeObject,个数是有上限的
        Collection<String> pivotKeys = redisUtils.keys("SC_DOT:*:*");

        // 更新统计时间
        Date standingTime = refreshTimeRecords();
        // String placeholderDotTime = DateUtils.format(timeRecords.get("dot"), YYYY_MM_DD_HH_MM_SS);

        // 构建需要更新的数据/查询索引
        Map<SCDotPivotIdx, Long> toAdd = Maps.newHashMap();
        pivotKeys.forEach(redisKey->{
            String[] parts = redisKey.split(":");
            String unionOrgCode = parts[1];
            Long noticeObject = Long.valueOf(parts[2]);
            Map<Object, Object> redisFields = redisUtils.hmget(redisKey);
            // fields.putIfAbsent(placeholderDotTime, 0);
            redisFields.forEach((redisField, count)->{
                if (0 == Convert.toInt(count)){
                    // 控制冗余，直接删除（无锁策略：从上次persistentDotStatisticsCron以来无delta，极小概率会在此刻发生）
                    // logger.info("Woops!Redis Key:{}, Redis Field:{}有冗余！", redisKey, redisField);
                    redisUtils.hdel(redisKey, redisField);
                } else {
                    SCDotPivotIdx idx = new SCDotPivotIdx(unionOrgCode, noticeObject, DateUtils.stringToDate((String) redisField));
                    toAdd.put(idx, Convert.toLong(count));
                    // 用增减代替加锁
                    redisUtils.hdecr(redisKey, (String) redisField, Convert.toDouble(count));
                }
                // 重置redis key过期时间为一天（否则SET和HINCRBY操作都会使hash expire清零，变为永不过期）
                // redisUtils.getRedisTemplate().expire(redisKey, Duration.ofSeconds( 24 * 60 * 60));
            });
        });

        if (DataUtils.isEmpty(toAdd)){
            logger.info("统计时间段内({}, {}),persistentDotStatisticsCron无需要更新数据！",
                    DateUtils.format(timeRecords.get("shift"), YYYY_MM_DD_HH_MM_SS), DateUtils.format(standingTime, YYYY_MM_DD_HH_MM_SS));

            noticeDotStatisticsService.cleanExpiredStatistics(standingTime);
            return;
        }

        // DB一次查(联合索引)，内存多次改
        List<NoticeDotStatisticsDO> existed = noticeDotStatisticsService.queryByPivotIdx(Lists.newArrayList(toAdd.keySet()));
        List<NoticeDotStatisticsDO> renew = Lists.newArrayList();
        // toAdd > existed
        toAdd.forEach((toAddItem, statistics)->{
            NoticeDotStatisticsDO renewItem = new NoticeDotStatisticsDO();
            List<NoticeDotStatisticsDO> matched = existed.stream().filter(
                    x->x.getUnionOrgCode().equals(toAddItem.getUnionOrgCode())
                    && x.getNoticeObject().equals(toAddItem.getNoticeObject())
                    && x.getDotTime().equals(toAddItem.getDotTime())).collect(Collectors.toList());
            if (matched.size() == 0){
                BeanUtils.copyProperties(toAddItem, renewItem);
                renewItem.setLogCount(statistics);
                renewItem.setCreateTime(standingTime);
            } else if (matched.size() == 1){
                NoticeDotStatisticsDO matchedItem = matched.get(0);
                renewItem.setId(matchedItem.getId());
                renewItem.setLogCount(matchedItem.getLogCount() + statistics);
            } else {
                logger.error(MessageFormat.format("notice_dot_statistics表联合主键查询非唯一值:{},请排查!",
                        JSON.toJSONString(matched)));
                throw new GenericException("persistentDotStatisticsCron前序处理错误！");
            }
            renewItem.setUpdateTime(standingTime);
            renew.add(renewItem);
        });

        // DB一次改
        noticeDotStatisticsService.updateBatch(renew);
        logger.info("统计时间段内({}, {}),persistentDotStatisticsCron完成更新数据{}条",
                DateUtils.format(timeRecords.get("shift"), YYYY_MM_DD_HH_MM_SS), DateUtils.format(standingTime, YYYY_MM_DD_HH_MM_SS),
                renew.size());

        // 物理删除DB过期数据
        noticeDotStatisticsService.cleanExpiredStatistics(standingTime);

    }

    /**
     * 获取点统计值，不提供准实时统计
     * @param orgCodePrefix 租户前缀
     * @return List<SCTrendBO>
     */
    public List<SCTrendBO> getDotStatistic(String orgCodePrefix){
        // 根据 selectedNoticeObjects 和 timeIntervals 补全默认透视图(join on)
        Date nearStatisticsTime = timeRecords.getOrDefault("near", new Date());
        // by hour 时间序列 * 2
        Date anchorDate = forwardFiveMins(nearStatisticsTime); // 保持步长，避免歧义
        List<Date> startTimeIntervals = generateTimeIntervals(anchorDate);
        List<Date> endTimeIntervals = new ArrayList<>(startTimeIntervals);
        endTimeIntervals.add(anchorDate);
        endTimeIntervals.remove(0);
        List<SCDotPivotIdx> defaultPivotTable = Lists.newArrayList();
        for (int i = 0; i < startTimeIntervals.size(); i++ ){
            for (Long noticeObject: selectedNoticeObjects){
                SCDotPivotIdx idx = new SCDotPivotIdx(noticeObject, startTimeIntervals.get(i), endTimeIntervals.get(i));
                defaultPivotTable.add(idx);
            }
        }

        // DB聚类查询，上限为 selectedNoticeObjects.size * (7*24*60/5)
        List<NoticeDotStatisticsDO> existed = noticeDotStatisticsService.queryByOrgCodeAndTimeRange(orgCodePrefix, nearStatisticsTime);

        List<SCTrendBO> trendBOList = Lists.newArrayList();
        if (DataUtils.isEmpty(existed)){
            logger.info("当前租户({})无任一原子能力({})计数！", orgCodePrefix, JSON.toJSONString(selectedNoticeObjects));
            return trendBOList;
        }

        // 匹配补全(left join)
        defaultPivotTable.forEach(defaultPivotIdx->{
            List<NoticeDotStatisticsDO> matched = existed.stream().filter(
                    x-> x.getNoticeObject().equals(defaultPivotIdx.getNoticeObject())
                    //        [dotTime/startTime, endTime)
                    && x.getDotTime().getTime() >= defaultPivotIdx.getDotTime().getTime()
                    && x.getDotTime().getTime() < defaultPivotIdx.getEndTime().getTime())
                    .collect(Collectors.toList());
            Long sumLogCount = 0L;
            if (!DataUtils.isEmpty(matched)){
                sumLogCount = matched.stream().mapToLong(NoticeDotStatisticsDO::getLogCount).sum();
            }
            SCTrendBO trendBO = new SCTrendBO();
            trendBO.setNoticeObject(defaultPivotIdx.getNoticeObject());
            // 与406之前保持一致（endTime作为xAxis）
            trendBO.setTime(DateUtils.format(defaultPivotIdx.getEndTime(), YYYY_MM_DD_HH_MM_SS));
            trendBO.setCnt(sumLogCount.intValue());
            trendBOList.add(trendBO);
        });

        logger.info("getDotStatistic-成功处理并返回当前租户（{}）数据{}/{}条 -----------> \n {}",
                orgCodePrefix, trendBOList.size(), selectedNoticeObjects.size()*7*24, JSON.toJSONString(trendBOList));

        return trendBOList;
    }

    /**
     * 异步方法，对消息留样入库
     */
    public void keepRetention(List<NoticeInfoDO> noticeInfoDOList){
        if (retenetinoFlag){
            keepRetentionTask.execute(()->{
                noticeDotStatisticsService.batchInsertRetention(noticeInfoDOList);
            });
        }
    }

    private Date refreshTimeRecords(){
        Date standingTime = new Date();
        timeRecords.put("shift", timeRecords.getOrDefault("near", new Date(0L)));
        timeRecords.put("near", standingTime);
        timeRecords.put("dot", forwardFiveMins(standingTime));

        return standingTime;
    }

    /**
     * @param date special example: TimeUtils.localDateTime2Date(LocalDateTime.of(2024, 6, 25, 14, 0, 0)
     * @return special example: Tue Jun 25 14:00:00 CST 2024
     */
    private Date forwardFiveMins(Date date){
        Calendar calendar = DateUtils.getCalendar(date);

        // 计算距离当前分钟最近的5分钟间隔的分钟数
        int minutes = calendar.get(Calendar.MINUTE);
        int fiveMinuteInterval = (minutes / 5) * 5;

        // 设置时间为该5分钟间隔的开始时间
        calendar.set(Calendar.MINUTE, fiveMinuteInterval);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTime();
    }

    private List<Date> generateTimeIntervals(Date anchorTime){
        // [anchorTime-7*24h, anchorTime)
        LocalDateTime endDateTime = anchorTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime startDateTime = endDateTime.minusDays(7);

        List<Date> timeSeries = new ArrayList<>();
        LocalDateTime currentDateTime = startDateTime;
        for (int i = 0; i < 7 * 24; i++) {
            timeSeries.add(TimeUtils.localDateTime2Date(currentDateTime));
            currentDateTime = currentDateTime.plusHours(1);
        }

        return timeSeries;
    }

}
