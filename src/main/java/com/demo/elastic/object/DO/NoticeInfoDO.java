package com.sama.api.notice.object.DO;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.core4ct.base.BaseModel;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @Author HP
 * @Date 18/4/2024 08:48
 * @Version 1.0
 */
@TableName("notice_original_info")
public class NoticeInfoDO extends BaseModel implements Serializable {

    private static final long serialVersionUID = -5915305496488093188L;

    @Schema(description = "通知时间")
    @TableField("notice_time")
    @JSONField(name = "notice_time")
    private Date noticeTime;

    @Schema(description = "被保护资产或资源的IP")
    @TableField("asset_ip")
    @JSONField(name = "asset_ip")
    private String assetIp;

    @Schema(description = "资产的业务系统")
    @TableField("business_system")
    @JSONField(name = "business_system")
    private String businessSystem;

    @Schema(description = "通知级别，事件等级，用于模板匹配，漏洞大类：漏洞等级是5档，分别为信息，低危，中危，高危，危急 对应 4，3，2，1，0；事件大类：原子能力等级为低危，中危，高危 对应 3，2，1 ；运维巡检告警：(3：低，2：中，1：高，0:严重)")
    @TableField("notice_level")
    @JSONField(name = "notice_level")
    private Integer noticeLevel;

    @Schema(description = "通知名称，事件名称")
    @TableField("notice_name")
    @JSONField(name = "notice_name")
    private String noticeName;

    @Schema(description = "通知类型，事件类型")
    @TableField("notice_class")
    @JSONField(name = "notice_class")
    private String noticeClass;

    @Schema(description = "事件源IP")
    @TableField("source_ip")
    @JSONField(name = "source_ip")
    private String sourceIp;

    @Schema(description = "通知大类，用于模板匹配，1-资源池主机虚机告警；2-原子能力事件告警；3-漏洞扫描告警")
    @TableField("notice_type")
    @JSONField(name = "notice_type")
    private Integer noticeType;

    @Schema(description = "通知对象，用于模板匹配，即原子能力ID；若为通知大类为资源池告警，主机告警为 -1，虚机告警为 -2；若通知大类为漏洞，漏扫为 -3")
    @TableField("notice_object")
    @JSONField(name = "notice_object")
    private Long noticeObject;

    @Schema(description = "原子能力子表序号")
    @TableField("product_sub_table_num")
    @JSONField(name = "product_sub_table_num")
    private Integer productSubTableNum;

    @Schema(description = "通知ID（事件ID，一般是uuid）")
    @TableField("notice_id")
    @JSONField(name = "notice_id")
    private String noticeId;

    @Schema(description = "资源池 OrgCode")
    @TableField("pool_org_code")
    @JSONField(name = "pool_org_code")
    private String poolOrgCode;

    @Schema(description = "资产ID")
    @TableField("asset_id")
    @JSONField(name = "asset_id")
    private Long assetId;

    @Schema(description = "资产所在租户code")
    @TableField("org_code")
    @JSONField(name = "org_code")
    private String orgCode;

    @Schema(description = "发起任务的租户code")
    @TableField("mission_org_code")
    @JSONField(name = "mission_org_code")
    private String missionOrgCode;

    @Schema(description = "日志类型，0-非任务型（默认值），1-任务型")
    @TableField("notice_log_class")
    @JSONField(name = "notice_log_class")
    private Integer noticeLogClass;

    @Schema(description = "通知状态：1-告警；2-取消告警")
    @TableField("notice_status")
    @JSONField(name = "notice_status")
    private Integer noticeStatus;

    @Schema(description = "描述")
    @TableField("description")
    @JSONField(name = "description")
    private String description;

    @Schema(description = "其他参数，由继承本 BaseDO 的子类的特有参数转成 Map 格式的 json string")
    @TableField("other_params")
    @JSONField(name = "other_params")
    private String otherParams;


    @TableField(exist = false)
    @JSONField(name = "description_map_list")
    private List<Map<String, String>> descriptionMapList;

    @TableField(exist = false)
    @JSONField(name = "other_params_map")
    private Map<String, Object> otherParamsMap;




    public Date getNoticeTime() {
        return noticeTime;
    }

    public void setNoticeTime(Date noticeTime) {
        this.noticeTime = noticeTime;
    }

    public String getAssetIp() {
        return assetIp;
    }

    public void setAssetIp(String assetIp) {
        this.assetIp = assetIp;
    }

    public String getBusinessSystem() {
        return businessSystem;
    }

    public void setBusinessSystem(String businessSystem) {
        this.businessSystem = businessSystem;
    }

    public Integer getNoticeLevel() {
        return noticeLevel;
    }

    public void setNoticeLevel(Integer noticeLevel) {
        this.noticeLevel = noticeLevel;
    }

    public String getNoticeName() {
        return noticeName;
    }

    public void setNoticeName(String noticeName) {
        this.noticeName = noticeName;
    }

    public String getNoticeClass() {
        return noticeClass;
    }

    public void setNoticeClass(String noticeClass) {
        this.noticeClass = noticeClass;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public Integer getNoticeType() {
        return noticeType;
    }

    public void setNoticeType(Integer noticeType) {
        this.noticeType = noticeType;
    }

    public Long getNoticeObject() {
        return noticeObject;
    }

    public void setNoticeObject(Long noticeObject) {
        this.noticeObject = noticeObject;
    }

    public Integer getProductSubTableNum() {
        return productSubTableNum;
    }

    public void setProductSubTableNum(Integer productSubTableNum) {
        this.productSubTableNum = productSubTableNum;
    }

    public String getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(String noticeId) {
        this.noticeId = noticeId;
    }

    public String getPoolOrgCode() {
        return poolOrgCode;
    }

    public void setPoolOrgCode(String poolOrgCode) {
        this.poolOrgCode = poolOrgCode;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getOrgCode() {
        return orgCode;
    }

    public void setOrgCode(String orgCode) {
        this.orgCode = orgCode;
    }

    public String getMissionOrgCode() {
        return missionOrgCode;
    }

    public void setMissionOrgCode(String missionOrgCode) {
        this.missionOrgCode = missionOrgCode;
    }

    public Integer getNoticeLogClass() {
        return noticeLogClass;
    }

    public void setNoticeLogClass(Integer noticeLogClass) {
        this.noticeLogClass = noticeLogClass;
    }

    public Integer getNoticeStatus() {
        return noticeStatus;
    }

    public void setNoticeStatus(Integer noticeStatus) {
        this.noticeStatus = noticeStatus;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOtherParams() {
        return otherParams;
    }

    public void setOtherParams(String otherParams) {
        this.otherParams = otherParams;
    }

    public List<Map<String, String>> getDescriptionMapList() {
        List<Map<String, String>> mapList = JSON.parseObject(getDescription(), new TypeReference<List<Map<String, String>>>() {});
        descriptionMapList = mapList == null ? Lists.newArrayList() : mapList;
        return descriptionMapList;
    }

    public void setDescriptionMapList(List<Map<String, String>> descriptionMapList) {
        this.descriptionMapList = descriptionMapList;
        setDescription(descriptionMapList == null ? JSON.toJSONString(Lists.newArrayList()) : JSON.toJSONString(descriptionMapList));
    }

    public Map<String, Object> getOtherParamsMap() {
        Map<String, Object> map = JSON.parseObject(getOtherParams(), new TypeReference<Map<String, Object>>() {});
        otherParamsMap = map == null ? Maps.newHashMap(): map;
        return otherParamsMap;
    }

    public void setOtherParamsMap(Map<String, Object> otherParamsMap) {
        this.otherParamsMap = otherParamsMap;
        setOtherParams(otherParamsMap == null ? JSON.toJSONString(Maps.newHashMap()) : JSON.toJSONString(otherParamsMap));
    }

    @Override
    public String toString() {
        return "NoticeInfoDO{" +
                "noticeTime=" + noticeTime +
                ", assetIp='" + assetIp + '\'' +
                ", businessSystem='" + businessSystem + '\'' +
                ", noticeLevel=" + noticeLevel +
                ", noticeName='" + noticeName + '\'' +
                ", noticeClass='" + noticeClass + '\'' +
                ", sourceIp='" + sourceIp + '\'' +
                ", noticeType=" + noticeType +
                ", noticeObject=" + noticeObject +
                ", productSubTableNum=" + productSubTableNum +
                ", noticeId='" + noticeId + '\'' +
                ", poolOrgCode='" + poolOrgCode + '\'' +
                ", assetId=" + assetId +
                ", orgCode='" + orgCode + '\'' +
                ", missionOrgCode='" + missionOrgCode + '\'' +
                ", noticeLogClass=" + noticeLogClass +
                ", noticeStatus=" + noticeStatus +
                ", description='" + description + '\'' +
                ", otherParams='" + otherParams + '\'' +
                ", descriptionMapList=" + descriptionMapList +
                ", otherParamsMap=" + otherParamsMap +
                "} " + super.toString();
    }
}
