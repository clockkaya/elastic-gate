package com.sama.api.notice.object.DTO;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * @author: huxh
 * @description:
 * @datetime: 2024/4/24 9:35
 */
@Schema(description = "安全中心实时事件播报")
public class SCRealTimeBroadcastDTO implements Serializable {

    private static final long serialVersionUID = 3610703993447248828L;

    @Schema(description = "id")
    private Long id;

    @Schema(description = "时间")
    private String time;

    @Schema(description = "来源能力")
    private String sourceAbility;

    @Schema(description = "业务系统")
    private String businessSystem;

    @Schema(description = "资产IP")
    private String assetIp;

    @Schema(description = "事件等级")
    private String eventLevel;

    @Schema(description = "事件名称")
    private String eventName;

    @Schema(description = "事件类型")
    private String eventType;

    @Schema(description = "事件源IP")
    private String srcIp;

    @Schema(description = "详情")
    private String detail;

    // 入参
    private Long noticeObject;

    // 被转义
    private String noticeClass;

    // 资产所在租户code
    private String orgCode;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getSourceAbility() {
        return sourceAbility;
    }

    public void setSourceAbility(String sourceAbility) {
        this.sourceAbility = sourceAbility;
    }

    public String getBusinessSystem() {
        return businessSystem;
    }

    public void setBusinessSystem(String businessSystem) {
        this.businessSystem = businessSystem;
    }

    public String getAssetIp() {
        return assetIp;
    }

    public void setAssetIp(String assetIp) {
        this.assetIp = assetIp;
    }

    public String getEventLevel() {
        return eventLevel;
    }

    public void setEventLevel(String eventLevel) {
        this.eventLevel = eventLevel;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSrcIp() {
        return srcIp;
    }

    public void setSrcIp(String srcIp) {
        this.srcIp = srcIp;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Long getNoticeObject() {
        return noticeObject;
    }

    public void setNoticeObject(Long noticeObject) {
        this.noticeObject = noticeObject;
    }

    public String getNoticeClass() {
        return noticeClass;
    }

    public void setNoticeClass(String noticeClass) {
        this.noticeClass = noticeClass;
    }

    public String getOrgCode() {
        return orgCode;
    }

    public void setOrgCode(String orgCode) {
        this.orgCode = orgCode;
    }

    @Override
    public String toString() {
        return "SCRealTimeBroadcastDTO{" +
                "id=" + id +
                ", time='" + time + '\'' +
                ", sourceAbility='" + sourceAbility + '\'' +
                ", businessSystem='" + businessSystem + '\'' +
                ", assetIp='" + assetIp + '\'' +
                ", eventLevel='" + eventLevel + '\'' +
                ", eventName='" + eventName + '\'' +
                ", eventType='" + eventType + '\'' +
                ", srcIp='" + srcIp + '\'' +
                ", detail='" + detail + '\'' +
                ", noticeObject=" + noticeObject +
                ", noticeClass='" + noticeClass + '\'' +
                ", orgCode='" + orgCode + '\'' +
                '}';
    }
}
