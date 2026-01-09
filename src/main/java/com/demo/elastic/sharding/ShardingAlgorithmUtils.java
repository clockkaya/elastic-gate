package com.sama.notice.sharding;

import cn.hutool.extra.spring.SpringUtil;
import com.core4ct.constants.HttpCode;
import com.core4ct.exception.GenericException;
import com.core4ct.utils.DataUtils;
import com.core4ct.utils.DateUtils;
import com.sama.api.notice.constant.ShardingConstans;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;

import java.io.File;
import java.sql.*;
import java.util.Date;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * @author: huxh
 * @description:
 * @datetime: 2024/8/19 16:39
 */
public class ShardingAlgorithmUtils {

    private final static Logger logger = LogManager.getLogger();

    /** 数据库配置 */
    private static final Environment ENV = SpringUtil.getApplicationContext().getEnvironment();
    private static final String DATASOURCE_URL = ENV.getProperty("spring.datasource.url");
    private static final String DATASOURCE_USERNAME = ENV.getProperty("init.mysqlUserName");
    private static final String DATASOURCE_PASSWORD = ENV.getProperty("init.mysqlPassword");

    /**
     * 获取所有匹配表名(可以改成dataSource.getConnection())
     *
     * @param   logicTableName  逻辑表
     * @return  List<String>    logicTableName_$->{20240805..20240813} 列表
     */
    public static List<String> getExistedTableNamesBySchema(String logicTableName, Boolean reshape) {
        if (StringUtils.isEmpty(DATASOURCE_URL) || StringUtils.isEmpty(DATASOURCE_USERNAME) || StringUtils.isEmpty(DATASOURCE_PASSWORD)) {
            logger.error("数据库连接配置有误，请稍后重试，URL:{}, username:{}", DATASOURCE_URL, DATASOURCE_USERNAME);
            throw new GenericException(HttpCode.INTERNAL_SERVER_ERROR, "数据库连接配置有误，请稍后重试");
        }

        // 表名前缀匹配
        List<String> actualTableNames = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DATASOURCE_URL, DATASOURCE_USERNAME, DATASOURCE_PASSWORD);
             Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("show TABLES like '" + logicTableName + ShardingConstans.TABLE_SPLIT_SYMBOL + "%'")) {
                while (rs.next()) {
                    String actualTableName = rs.getString(1);
                    // 匹配分表格式
                    if (actualTableName != null && actualTableName.matches(String.format("^(%s\\d{8})$", logicTableName + ShardingConstans.TABLE_SPLIT_SYMBOL))) {
                        actualTableNames.add(rs.getString(1));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("数据库连接失败，请稍后重试，原因：{}", e.getMessage(), e);
            throw new GenericException(HttpCode.INTERNAL_SERVER_ERROR, "数据库连接失败，请稍后重试");
        }

        // 不可以直接获取，因为分表规则可能不是连续的，比如：20240805, 20240813
        if (reshape){
            reshapeWithinOrGetDefaultRange(logicTableName, actualTableNames);
        }

        return actualTableNames;
    }


    /**
     * 获取默认的分表范围
     *
     * @param logicTableName    可以为null
     * @param actualTableNames  可以为null
     * @return
     */
    public static Map<String, Date> reshapeWithinOrGetDefaultRange(String logicTableName, List<String> actualTableNames){
        Map<String, Date> defaultShardingRange = new HashMap<>();
        Date now = new Date();
        Date defaultLowerDate = DateUtils.addDate(now, Calendar.MONTH, -1);
        Date defaultUpperDate = DateUtils.addDate(now, Calendar.DATE, 1);
        defaultShardingRange.put(ShardingConstans.DEFAULT_LOWER_DATE_KEY, defaultLowerDate);
        defaultShardingRange.put(ShardingConstans.DEFAULT_UPPER_DATE_KEY, defaultUpperDate);

        // actualTableNames 交集 遍历生成的defaultRangeTableNames
        if( DataUtils.isNotEmpty(logicTableName) && DataUtils.isNotEmpty(actualTableNames)){
            List<String> defaultRangeTableNames = Stream.iterate(defaultLowerDate, date -> DateUtils.addDate(date, Calendar.DATE, 1))
                    .limit(DateUtils.getDayBetween(defaultLowerDate, defaultUpperDate) + 1)
                    .map(x -> logicTableName + shardingSuffix(x))
                    .filter(actualTableNames::contains)
                    .collect(Collectors.toList());
            actualTableNames.clear();
            actualTableNames.addAll(defaultRangeTableNames);
        }

        return defaultShardingRange;
    }

    /**
     * 根据日期生成分片后缀
     *
     * @param shardingValue
     * @return
     */
    public static String shardingSuffix(Date shardingValue) {
        String dateSuffix = DateUtils.format(shardingValue, ShardingConstans.DATE_SUFFIX);
        return ShardingConstans.TABLE_SPLIT_SYMBOL + dateSuffix;
    }

    /**
     * 按照logicTableName表结构（前提必须存在），创建actuallyTableName分表
     *
     * @param logicTableName  逻辑表
     * @param actuallyTableName 真实表名，例：notice_info_retention_20240813
     * @return 创建结果（true创建成功，false未创建）
     */
    public static boolean createShardingTable(String logicTableName, String actuallyTableName) {
        synchronized (logicTableName.intern()) {
            executeSql(Collections.singletonList("CREATE TABLE IF NOT EXISTS `" + actuallyTableName + "` LIKE `" + logicTableName + "`;"));
        }
        return true;
    }

    /**
     * 执行SQL
     *
     * @param sqlList SQL集合
     */
    private static void executeSql(List<String> sqlList) {
        if (StringUtils.isEmpty(DATASOURCE_URL) || StringUtils.isEmpty(DATASOURCE_USERNAME) || StringUtils.isEmpty(DATASOURCE_PASSWORD)) {
            logger.error(">>>>>>>>>> 【ERROR】数据库连接配置有误，请稍后重试，URL:{}, username:{}, password:{}", DATASOURCE_URL, DATASOURCE_USERNAME, DATASOURCE_PASSWORD);
            throw new IllegalArgumentException("数据库连接配置有误，请稍后重试");
        }
        try (Connection conn = DriverManager.getConnection(DATASOURCE_URL, DATASOURCE_USERNAME, DATASOURCE_PASSWORD)) {
            try (Statement st = conn.createStatement()) {
                conn.setAutoCommit(false);
                for (String sql : sqlList) {
                    st.execute(sql);
                }
            } catch (Exception e) {
                conn.rollback();
                logger.error(">>>>>>>>>> 【ERROR】数据表创建执行失败，请稍后重试，原因：{}", e.getMessage(), e);
                throw new IllegalArgumentException("数据表创建执行失败，请稍后重试");
            }
        } catch (SQLException e) {
            logger.error(">>>>>>>>>> 【ERROR】数据库连接失败，请稍后重试，原因：{}", e.getMessage(), e);
            throw new IllegalArgumentException("数据库连接失败，请稍后重试");
        }
    }

    /**
     * 获取数据源配置
     */
    private static File getShardingYamlFile() {
        return new File(Objects.requireNonNull(
                ShardingAlgorithmUtils.class.getClassLoader().getResource(ShardingConstans.CONFIG_FILE), String.format("File `%s` is not existed.", ShardingConstans.CONFIG_FILE)).getFile());
    }

}
