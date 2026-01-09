package com.sama.notice.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.core4ct.api.system.SystemDubboService;
import com.core4ct.api.system.object.SystemTransformKey;
import com.core4ct.constants.OrgClassConstants;
import com.core4ct.utils.OrgCodeUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sama.api.business.AssetDubboService;
import com.sama.api.notice.constant.NoticeObject;
import com.sama.api.notice.object.DO.NoticeInfoDO;
import com.sama.api.notice.object.DO.NoticeLogDO;
import com.sama.api.notice.object.DTO.NoticeTemplateDTO;
import com.sama.notice.config.NoticeConfig;
import com.sama.notice.service.parseOriginalLog.*;
import com.sama.notice.utils.BeanToMapUtil;
import com.sama.notice.utils.KafkaClientUtil;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @Author HP
 * @Date 18/06/2024 15:57
 * @Version 1.0
 */
@Component
public class KafkaConsumerService {

    private final static Logger logger = LogManager.getLogger();

    @DubboReference
    AssetDubboService assetDubboService;

    @DubboReference
    SystemDubboService systemDubboService;

    @Resource
    NoticeLogService noticeLogService;

    @Resource
    NoticeInfoService noticeInfoService;

    @Resource
    NoticeConfig noticeConfig;

    @Resource(name = "asyncSaveDB")
    ThreadPoolTaskExecutor asyncSaveDB;

    @Resource
    KafkaClientUtil kafkaClientUtil;



    private final HashMap<String, String> orgNameMap = Maps.newHashMap();

    private final HashMap<Long, String> assetOrgCodeMap = Maps.newHashMap();

    private final HashMap<Long, Long> logTime = Maps.newHashMap();

    public void initLogTime() {
        long currentTimeMillis = System.currentTimeMillis();
        logTime.put(NoticeObject.WAF, currentTimeMillis);
        logTime.put(NoticeObject.FW, currentTimeMillis);
        logTime.put(NoticeObject.IPS, currentTimeMillis);
        logTime.put(NoticeObject.SCAN, currentTimeMillis);
        logTime.put(NoticeObject.NET_AUDIT, currentTimeMillis);
        logTime.put(NoticeObject.LOG_AUDIT, currentTimeMillis);
        logTime.put(NoticeObject.DATA_AUDIT, currentTimeMillis);
        logTime.put(NoticeObject.EDR, currentTimeMillis);
        logTime.put(NoticeObject.BASTION, currentTimeMillis);
        logTime.put(NoticeObject.ANTI_VIRUS, currentTimeMillis);
        logTime.put(NoticeObject.ANTI_TAMPER, currentTimeMillis);
    }

    public HashMap<String, String> getOrgNameMap() {
        return orgNameMap;
    }

    public HashMap<Long, String> getAssetOrgCodeMap() {
        return assetOrgCodeMap;
    }

    public void setInfoByAssetId(NoticeInfoDO noticeInfoDO) {
        //EDR 漏扫 任务型，需要根据 assetId 获取 orgCode
        if (noticeInfoDO.getNoticeLogClass() != null
                && noticeInfoDO.getNoticeLogClass().equals(1)
                && noticeInfoDO.getAssetId() != null
                && !noticeInfoDO.getAssetId().equals(0L)
        ) {
            String orgCode = assetOrgCodeMap.get(noticeInfoDO.getAssetId());
            if (orgCode == null) {
                try {
                    Map<Long, String> transformOrgCodeMap = assetDubboService.transform(Lists.newArrayList(noticeInfoDO.getAssetId()));
                    orgCode = transformOrgCodeMap.get(noticeInfoDO.getAssetId());
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
                if (orgCode == null) {
                    orgCode = "";
                }
                assetOrgCodeMap.put(noticeInfoDO.getAssetId(), orgCode);
            }
            noticeInfoDO.setOrgCode(orgCode);
        }

        //存在 orgCode，才能获取业务系统名称
        if (noticeInfoDO.getOrgCode() != null && !"".equals(noticeInfoDO.getOrgCode())) {
            //判断是否是业务系统
            char flag = 0;
            try {
                flag = OrgCodeUtils.getClass(noticeInfoDO.getOrgCode());
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
            if (flag == OrgClassConstants.BIZ_SYSTEM) {
                String businessSystem = orgNameMap.get(noticeInfoDO.getOrgCode());
                if (businessSystem == null) {
                    try {
                        Map<String, String> transformMap = systemDubboService.transformByStr(SystemTransformKey.ORG_NAME, Lists.newArrayList(noticeInfoDO.getOrgCode()));
                        businessSystem = transformMap.get(noticeInfoDO.getOrgCode());
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                    if (businessSystem == null) {
                        businessSystem = "";
                    }
                    orgNameMap.put(noticeInfoDO.getOrgCode(), businessSystem);
                }
                noticeInfoDO.setBusinessSystem(businessSystem);
            }
        }

    }


    public void syncPush() {
        try {
            String kafkaAddress = noticeConfig.getKafkaAddress4OriginalLog();
            String groupId = noticeConfig.getSyncPushGroupId();
            Map<String, String> configHashMap = noticeConfig.getKafkaConfigMap();
            KafkaConsumer<String, String> consumer = kafkaClientUtil.getConsumer(kafkaAddress, groupId, configHashMap);
            consumer.subscribe(Lists.newArrayList(noticeConfig.getKafkaTopic4OriginalLog()));

            LinkedBlockingQueue<NoticeLogDO> cacheNoticeLogQueue = new LinkedBlockingQueue<>(5000);

            while (true) {
                try {
                    // 消费消息
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(300)); // 轮询等待时间 300ms
                    for (ConsumerRecord<String, String> record : records) {
                        // 模板匹配并推送告警日志
                        List<NoticeLogDO> noticeLogDOList = publishNoticeLog(record.value());
                        cacheNoticeLogQueue.addAll(noticeLogDOList);

                        //入库保存超出缓存队列的log，等待空闲异步发送
                        //每1000条日志写一次数据库
                        if (cacheNoticeLogQueue.size() == 1000 || cacheNoticeLogQueue.size() > 2000) {
                            // 异步写入数据库
                            asyncSaveDB.execute(()->{
                                List<NoticeLogDO> batchNoticeLogDOList = Lists.newArrayList();
                                int num = cacheNoticeLogQueue.drainTo(batchNoticeLogDOList);
                                if (num > 0) {
                                    noticeLogService.batchInsertNoticeLog(batchNoticeLogDOList);
                                    logger.info("待发送 kafka/http 消息日志条数：{}", num);
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private List<NoticeLogDO> publishNoticeLog(String value) {

        NoticeInfoDO noticeInfoDO;
        Map<String, Object> originalMap;
        try {
            originalMap = JSON.parseObject(value, new TypeReference<Map<String, Object>>() {});
            noticeInfoDO = parseNoticeInfoDO(originalMap, value);
        } catch (Exception e) {
            logger.error("非法 kafka 消息：{}, {}", value, e.getMessage(), e);
            return Lists.newArrayList();
        }

        return noticeLogService.mapTmpAndGenNoticeLog(noticeInfoDO, originalMap);
    }


    public NoticeInfoDO parseNoticeInfoDO(Map<String, Object> originalMap, String value) {
        NoticeInfoDO noticeInfoDO = new NoticeInfoDO();

        noticeInfoDO.setNoticeObject(Long.parseLong(originalMap.get("notice_object").toString()));
        noticeInfoDO.setProductSubTableNum(noticeLogService.getProductSubTableNum(originalMap.get("product_sub_table_num")));

        boolean isPrintLog = false;
        switch (noticeInfoDO.getNoticeObject().intValue()) {
            case (int) NoticeObject.WAF:
                isPrintLog = checkLogTime(NoticeObject.WAF);
                ParseGateway.parseGateway(noticeInfoDO, originalMap);
                break;
            case (int) NoticeObject.FW:
                isPrintLog = checkLogTime(NoticeObject.FW);
                ParseGateway.parseGateway(noticeInfoDO, originalMap);
                break;
            case (int) NoticeObject.IPS:
                isPrintLog = checkLogTime(NoticeObject.IPS);
                ParseGateway.parseGateway(noticeInfoDO, originalMap);
                break;
            case (int) NoticeObject.SCAN:
                isPrintLog = checkLogTime(NoticeObject.SCAN);
                ParseScan.parseScan(noticeInfoDO, originalMap);
                break;
            case (int) NoticeObject.NET_AUDIT:
                isPrintLog = checkLogTime(NoticeObject.NET_AUDIT);
                ParseNetAudit.parseNetAudit(noticeInfoDO, originalMap);
                break;
            case (int) NoticeObject.LOG_AUDIT:
                isPrintLog = checkLogTime(NoticeObject.LOG_AUDIT);
                ParseLogAudit.parseLogAudit(noticeInfoDO, originalMap);
                break;
            case (int) NoticeObject.DATA_AUDIT:
                isPrintLog = checkLogTime(NoticeObject.DATA_AUDIT);
                ParseDataAudit.parseDataAudit(noticeInfoDO, originalMap);
                break;
            case (int) NoticeObject.EDR:
                isPrintLog = checkLogTime(NoticeObject.EDR);
                switch (noticeInfoDO.getProductSubTableNum()) {
                    case 1:
                        ParseEdr.parseThreatDetection(noticeInfoDO, originalMap);
                        break;
                    case 2:
                        ParseEdr.parseFileProtection(noticeInfoDO, originalMap);
                        break;
                    case 3:
                        ParseEdr.parseTerminalEvent(noticeInfoDO, originalMap);
                        break;
                    case 4:
                        ParseEdr.parseBaselineEvent(noticeInfoDO, originalMap);
                        break;
                    case 5:
                        ParseEdr.parseWebShellEvent(noticeInfoDO, originalMap);
                        break;
                    case 6:
                        ParseEdr.parseWeakPasswordEvent(noticeInfoDO, originalMap);
                        break;
                    case 7:
                        ParseEdr.parseHostVul(noticeInfoDO, originalMap);
                        break;
                }
                break;
            case (int) NoticeObject.BASTION:
                isPrintLog = checkLogTime(NoticeObject.BASTION);
                ParseBastion.parseBastion(noticeInfoDO, originalMap);
                break;
            case (int) NoticeObject.ANTI_VIRUS:
                isPrintLog = checkLogTime(NoticeObject.ANTI_VIRUS);
                ParseAntiVirus.parseAntiVirus(noticeInfoDO, originalMap);
                break;
            case (int) NoticeObject.ANTI_TAMPER:
                isPrintLog = checkLogTime(NoticeObject.ANTI_TAMPER);
                ParseAntiTamper.parseAntiTamper(noticeInfoDO, originalMap);
                break;
            default:
                noticeInfoDO = JSON.parseObject(value, NoticeInfoDO.class);
        }

        setInfoByAssetId(noticeInfoDO);
        noticeInfoDO.setCreateTime(new Date());

        Map<String, Object> noticeInputMap = BeanToMapUtil.convertBeanToMap(noticeInfoDO);
        noticeInputMap.remove("serial_version_u_i_d");
        noticeInputMap.remove("description");
        noticeInputMap.remove("otherParamsMap");
        noticeInputMap.remove("otherParams");
        originalMap.putAll(noticeInputMap);
        noticeInfoDO.setOtherParamsMap(originalMap);

        if (isPrintLog) {
            logger.info("noticeObject：{}，原始日志：{}", noticeInfoDO.getNoticeObject(), value);
            NoticeInfoDO finalNoticeInfoDO = noticeInfoDO;
            asyncSaveDB.execute(()->{
                noticeInfoService.add(finalNoticeInfoDO);
            });

        }
        return noticeInfoDO;
    }

    private boolean checkLogTime(Long noticeObjectId) {
        long currentTimeMillis = System.currentTimeMillis();
        boolean isPrintLog = ( currentTimeMillis - logTime.get(noticeObjectId) ) > 300000;
        if (isPrintLog) {
            logTime.put(noticeObjectId, currentTimeMillis);
        }
        return isPrintLog;
    }




    public void syncPassThrough() {
        try {
            String kafkaAddress = noticeConfig.getKafkaAddress4OriginalLog();
            String groupId = noticeConfig.getPassThroughGroupId();
            Map<String, String> configHashMap = noticeConfig.getKafkaConfigMap();
            KafkaConsumer<String, String> consumer = kafkaClientUtil.getConsumer(kafkaAddress, groupId, configHashMap);
            consumer.subscribe(Lists.newArrayList(noticeConfig.getKafkaTopic4PassThrough()));

            LinkedBlockingQueue<NoticeLogDO> cacheNoticeLogQueue = new LinkedBlockingQueue<>(5000);

            while (true) {
                try {
                    // 消费消息
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(300)); // 轮询等待时间 300ms
                    for (ConsumerRecord<String, String> record : records) {
                        // 模板匹配并推送告警日志
                        List<NoticeLogDO> noticeLogDOList = parsePassThroughLog(record.value());
                        cacheNoticeLogQueue.addAll(noticeLogDOList);

                        //入库保存超出缓存队列的log，等待空闲异步发送
                        //每1000条日志写一次数据库
                        if (cacheNoticeLogQueue.size() == 1000 || cacheNoticeLogQueue.size() > 2000) {
                            // 异步写入数据库
                            asyncSaveDB.execute(()->{
                                List<NoticeLogDO> batchNoticeLogDOList = Lists.newArrayList();
                                int num = cacheNoticeLogQueue.drainTo(batchNoticeLogDOList);
                                if (num > 0) {
                                    noticeLogService.batchInsertNoticeLog(batchNoticeLogDOList);
                                    logger.info("透传，待发送 kafka/http 消息日志条数：{}", num);
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }


    private List<NoticeLogDO> parsePassThroughLog(String value) {

        NoticeInfoDO noticeInfoDO = new NoticeInfoDO();
        Map<String, Object> originalMap;
        try {
            originalMap = JSON.parseObject(value, new TypeReference<Map<String, Object>>() {});
            noticeInfoDO.setNoticeType(Integer.parseInt(originalMap.get("notice_type").toString()));
            noticeInfoDO.setPoolOrgCode(originalMap.get("pool_org_code").toString());
            noticeInfoDO.setOrgCode(originalMap.get("tenant_org_code").toString());
            noticeInfoDO.setNoticeId(originalMap.get("notice_id").toString());

            //移除不需要 推送的字段，该字段只用于模板匹配
            originalMap.remove("notice_type");
            originalMap.remove("notice_id");
        } catch (Exception e) {
            logger.error("透传，非法 kafka 消息：{}, {}", value, e.getMessage(), e);
            return Lists.newArrayList();
        }

        List<NoticeTemplateDTO> noticeTemplateList = noticeLogService.matchPassThroughTemplate(noticeInfoDO, originalMap);
        return noticeLogService.cachePassThroughNoticeLog(noticeInfoDO, originalMap, noticeTemplateList);
    }
}
