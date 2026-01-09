package com.sama.api.notice.object.DTO;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * @author: huxh
 * @description:
 * @datetime: 2024/4/24 14:22
 */
@Schema(description = "安全中心拦截趋势图")
public class SCTrendChartDTO implements Serializable {

    private static final long serialVersionUID = 8077734770951946884L;

    // endTime
    @Schema(description = "横轴：时间")
    List<String> xAxis;

    @Schema(description = "纵轴：各原子能力统计值")
    List<SCTrendBO> dataList;

    public List<String> getxAxis() {
        return xAxis;
    }

    public void setxAxis(List<String> xAxis) {
        this.xAxis = xAxis;
    }

    public List<SCTrendBO> getDataList() {
        return dataList;
    }

    public void setDataList(List<SCTrendBO> dataList) {
        this.dataList = dataList;
    }

    @Override
    public String toString() {
        return "SCTrendChartDTO{" +
                "xAxis=" + xAxis +
                ", dataList=" + dataList +
                '}';
    }
}
