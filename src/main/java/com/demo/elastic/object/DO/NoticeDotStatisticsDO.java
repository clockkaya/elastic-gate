package com.sama.api.notice.object.DO;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.core4ct.base.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: huxh
 * @description: SCTrendPivotIdx/idx_pivot: union_org_code, notice_object, dot_time
 * @datetime: 2024/6/20 9:58
 */
@Schema(description = "接收日志在统计时间内的计数")
@TableName("notice_dot_statistics")
public class NoticeDotStatisticsDO extends BaseModel implements Serializable {

    private static final long serialVersionUID = 1464329108831440878L;

    @Schema(description = "租户code（以C结尾的为非任务型，以P结尾的为任务型）")
    @TableField("union_org_code")
    private String unionOrgCode;

    @Schema(description = "通知对象，用于模板匹配，即原子能力ID")
    @TableField("notice_object")
    private Long noticeObject;

    @Schema(description = "统计时间段的起始值")
    @TableField("dot_time")
    private Date dotTime;

    @Schema(description = "统计时间内的计数")
    @TableField("log_count")
    private Long logCount;

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

    public Long getLogCount() {
        return logCount;
    }

    public void setLogCount(Long logCount) {
        this.logCount = logCount;
    }

    @Override
    public String toString() {
        return "NoticeDotStatisticsDO{" +
                "unionOrgCode='" + unionOrgCode + '\'' +
                ", noticeObject=" + noticeObject +
                ", dotTime=" + dotTime +
                ", logCount=" + logCount +
                "} " + super.toString();
    }
}
