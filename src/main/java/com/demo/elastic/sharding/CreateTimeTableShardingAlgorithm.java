package com.sama.notice.sharding;

import com.core4ct.utils.DateUtils;
import com.google.common.collect.Range;
import com.sama.api.notice.constant.ShardingConstans;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sama.notice.sharding.ShardingAlgorithmUtils.reshapeWithinOrGetDefaultRange;
import static com.sama.notice.sharding.ShardingAlgorithmUtils.shardingSuffix;

/**
 * @author: huxh
 * @description: 隐含一个重要逻辑：只有当availableTargetNames有变化时，才会重新从yml文件重新读取
 * @datetime: 2024/8/9 11:24
 */
public class CreateTimeTableShardingAlgorithm implements StandardShardingAlgorithm<Date> {

    private final static Logger logger = LogManager.getLogger();

    private Properties props;

    /**
     * 在初始化算法的时候被调用
     *
     * @param properties    配置文件中的props参数
     */
    @Override
    public void init(Properties properties) {
        this.props = properties;
    }

    @Override
    public String getType() {
        return "CLASS_BASED";
    }

    /**
     * 精确路由算法
     * 由于这个callback无法区分CRUD，所有DML都可能导致建表（*无法避免），所以所有CRUD的范围必须统一！
     *
     * @param actualTableNames      配置文件中的actual-data-nodes，被解析成列表递过来（必须赋值）
     * @param preciseShardingValue  精确值，对应配置文件中的shardingColumn
     * @return 路由后的唯一真实表
     */
    @Override
    public String doSharding(Collection<String> actualTableNames, PreciseShardingValue<Date> preciseShardingValue) {
        String logicTableName = preciseShardingValue.getLogicTableName();
        String targetTableName = preciseShardingValue.getLogicTableName() + shardingSuffix(preciseShardingValue.getValue());
        logger.info("对逻辑表:{}，目标表:{} 使用精确路由算法", logicTableName, targetTableName);

        // 只有ShardingTablesLoadRunner初始化会进入
        if (actualTableNames.size() == 1) {
            List<String> existedTableNames = ShardingAlgorithmUtils.getExistedTableNamesBySchema(logicTableName, true);
            actualTableNames.clear();
            actualTableNames.addAll(existedTableNames);
            // return targetTableName;
        }

        // 判断是否增量
        return getShardingTableOrCreate(logicTableName, targetTableName, actualTableNames);
    }

    /**
     * 范围路由算法（建议主动设置 between and 范围）
     *
     * @param actualTableNames      配置文件中的actual-data-nodes，被解析成列表递过来（必须赋值）
     * @param rangeShardingValue    值范围
     * @return 路由后的真实表
     */
    @Override
    public Collection<String> doSharding(Collection<String> actualTableNames, RangeShardingValue<Date> rangeShardingValue) {
        String logicTableName = rangeShardingValue.getLogicTableName();

        // 根据入参最小值（默认当前-1月）和最大值（默认当前+1天）生成目标表项
        Date now = new Date();
        Range<Date> valueRange = rangeShardingValue.getValueRange();
        Map<String, Date> defaultShardingRange = reshapeWithinOrGetDefaultRange(null, null);
        Date rangeLowerDate = valueRange.hasLowerBound() ? valueRange.lowerEndpoint() : defaultShardingRange.get(ShardingConstans.DEFAULT_LOWER_DATE_KEY);
        Date rangeUpperDate = valueRange.hasUpperBound() ? valueRange.upperEndpoint() : defaultShardingRange.get(ShardingConstans.DEFAULT_UPPER_DATE_KEY);
        List<String> targetTableNames = Stream.iterate(rangeLowerDate, date -> DateUtils.addDate(date, Calendar.DATE, 1))
                .limit(DateUtils.getDayBetween(rangeLowerDate, rangeUpperDate) + 1)
                .map(x -> logicTableName + shardingSuffix(x))
                // 相当于严格限定，因为 select/update/delete都不会在无表时触发， 而insert语句不能直接与where子句结合使用
                .filter(actualTableNames::contains)
                .collect(Collectors.toList());
        logger.info("对逻辑表：{}，多个实际表:{} 使用范围路由算法", logicTableName, targetTableNames);

        // 注意此处不同
        return targetTableNames;
    }

    /**
     * 检查存量表项中是否存在实际表，不存在则自动建表
     *
     * @param logicTableName    逻辑表
     * @param targetTableName   目标表
     * @param actualTableNames  （存量）实际表项
     * @return  唯一目标表
     */
    private String getShardingTableOrCreate(String logicTableName, String targetTableName, Collection<String> actualTableNames) {
        if (actualTableNames.contains(targetTableName)) {
            logger.info("当前存量表项为：{}，包含实际表{}", actualTableNames,targetTableName);
            return targetTableName;
        } else {
            boolean isSuccess = ShardingAlgorithmUtils.createShardingTable(logicTableName, targetTableName);
            if (isSuccess) {
                actualTableNames.add(targetTableName);
                logger.info("当前存量表为：{}，其中已成功创建实际表{}", actualTableNames, targetTableName);
                return targetTableName;
            } else {
                logger.warn("当前存量表为：{}，不包含实际表{}，创建失败，沿用逻辑表{}", actualTableNames, targetTableName, logicTableName);
                return logicTableName;
            }
        }
    }

    private Set<String> getShardingTablesAndCreate(String logicTableName, Collection<String> targetTableNames, Collection<String> actualTableNames) {
        return targetTableNames.stream()
                .map(single -> getShardingTableOrCreate(logicTableName, single, actualTableNames))
                .collect(Collectors.toSet());
    }

}
