package com.sama.api.notice.object.DO.SC;

import com.google.common.base.Objects;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: huxh
 * @description:
 * @datetime: 2024/6/18 17:12
 */
public class SCDotPivotIdx implements Serializable {

    private static final long serialVersionUID = -8944639573059098028L;

    private String unionOrgCode;

    private Long noticeObject;

    private Date dotTime;

    private Date endTime;

    public SCDotPivotIdx(String unionOrgCode, Long noticeObject, Date dotTime) {
        this.unionOrgCode = unionOrgCode;
        this.noticeObject = noticeObject;
        this.dotTime = dotTime;
    }

    public SCDotPivotIdx(String unionOrgCode, Long noticeObject) {
        this.unionOrgCode = unionOrgCode;
        this.noticeObject = noticeObject;
    }

    public SCDotPivotIdx(Long noticeObject, Date dotTime, Date endTime) {
        this.noticeObject = noticeObject;
        this.dotTime = dotTime;
        this.endTime = endTime;
    }

    public String getUnionOrgCode() {
        return unionOrgCode;
    }

    public void setUnionOrgCode(String unionOrgCode) {
        this.unionOrgCode = unionOrgCode;
    }

    public Long getNoticeObject() {
        return noticeObject;
    }

    public void setNoticeObject(Long noticeObject) {
        this.noticeObject = noticeObject;
    }

    public Date getDotTime() {
        return dotTime;
    }

    public void setDotTime(Date dotTime) {
        this.dotTime = dotTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SCDotPivotIdx that = (SCDotPivotIdx) o;
        return Objects.equal(unionOrgCode, that.unionOrgCode) && Objects.equal(noticeObject, that.noticeObject) && Objects.equal(dotTime, that.dotTime);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(unionOrgCode, noticeObject, dotTime);
    }

    @Override
    public String toString() {
        return "SCDotPivotIdx{" +
                "unionOrgCode='" + unionOrgCode + '\'' +
                ", noticeObject=" + noticeObject +
                ", dotTime='" + dotTime + '\'' +
                '}';
    }

}
