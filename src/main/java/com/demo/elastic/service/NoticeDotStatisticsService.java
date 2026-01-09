package com.sama.notice.service;

import com.core4ct.base.BaseService;
import com.sama.api.notice.object.DO.NoticeDotStatisticsDO;
import com.sama.api.notice.object.DO.NoticeInfoDO;
import com.sama.api.notice.object.DO.SC.SCDotPivotIdx;

import java.util.Date;
import java.util.List;

/**
 * @author: huxh
 * @description:
 * @datetime: 2024/6/20 15:51
 */
public interface NoticeDotStatisticsService extends BaseService<NoticeDotStatisticsDO> {

    List<NoticeDotStatisticsDO> queryByPivotIdx(List<SCDotPivotIdx> pivotIdx);

    List<NoticeDotStatisticsDO> queryByOrgCodeAndTimeRange(String orgCodePrefix, Date anchorTime);

    void cleanExpiredStatistics(Date anchorTime);

    void batchInsertRetention(List<NoticeInfoDO> noticeInfoDOList);

}
