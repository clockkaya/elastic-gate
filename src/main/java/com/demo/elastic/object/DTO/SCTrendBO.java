package com.sama.api.notice.object.DTO;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: huxh
 * @description:
 * @datetime: 2024/4/24 14:24
 */
public class SCTrendBO implements Serializable {

    private static final long serialVersionUID = 3043845511678638452L;

    private Long timeId;

    // xKey
    private String time;

    // yKey
    private Long noticeObject;

    private String objectName;

    // yData
    private Integer cnt;

    // yDataList
    private List<Integer> value;

    /**
     * 注意这里不可以用Map数据结构，否则用流式处理数据会陷入不必要的麻烦
     * @return
     */
    public List<Object> prepareylist() {
        List<Object> result = new ArrayList<>();
        result.add(objectName);
        result.add(noticeObject);
        result.add(cnt);
        return result;
    }

    public Long getTimeId() {
        return timeId;
    }

    public void setTimeId(Long timeId) {
        this.timeId = timeId;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public Long getNoticeObject() {
        return noticeObject;
    }

    public void setNoticeObject(Long noticeObject) {
        this.noticeObject = noticeObject;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public Integer getCnt() {
        return cnt;
    }

    public void setCnt(Integer cnt) {
        this.cnt = cnt;
    }

    public List<Integer> getValue() {
        return value;
    }

    public void setValue(List<Integer> value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "SCTrendBO{" +
                "timeId=" + timeId +
                ", time='" + time + '\'' +
                ", noticeObject=" + noticeObject +
                ", objectName='" + objectName + '\'' +
                ", cnt=" + cnt +
                ", value=" + value +
                '}';
    }
}
