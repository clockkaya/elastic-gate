package com.sama.api.notice.object.DO.SC;

import com.sama.api.notice.object.DO.NoticeInfoDO;

import java.util.Comparator;

/**
 * @author: huxh
 * @description:
 * @datetime: 2024/6/12 15:13
 */
public class NoticeInfoComparator implements Comparator<NoticeInfoDO> {

    @Override
    public int compare(NoticeInfoDO o1, NoticeInfoDO o2) {
        // 逆序
        int result = o2.getNoticeTime().compareTo(o1.getNoticeTime());
        if (result != 0) {
            return result;
        }
        result = o2.getCreateTime().compareTo(o1.getCreateTime());
        if (result != 0) {
            return result;
        }
        // noticeId作为唯一标识
        return o2.getNoticeId().compareTo(o1.getNoticeId());
    }
}
