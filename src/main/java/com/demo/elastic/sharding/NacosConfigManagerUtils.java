package com.sama.notice.sharding;

import com.alibaba.cloud.nacos.NacosConfigManager;

import java.util.Map;

/**
 * @author: huxh
 * @description: 定义一个静态的NacosConfigManager，在Spring容器创建完成后进行赋值，在加载驱动时获取该实例，然后调用获取配置（理解为快照）
 * 原因：ShardingSphere驱动在项目启动时并不会加载，在第一次访问数据时才会加载，使用SPI机制加载ShardingSphereDriverURLProvider实例，然后获取配置，加载数据源、分片规则，所以这里无法从容器中直接获取NacosConfigManager
 * @datetime: 2024/8/16 13:28
 */
public class NacosConfigManagerUtils {

    private NacosConfigManagerUtils() {
    }

    private static NacosConfigManager nacosConfigManager;

    private static Map<String, String> nacosProps;

    public static void init(NacosConfigManager manager) {
        nacosConfigManager = manager;
    }

    public static void initWithProps(NacosConfigManager manager, Map<String, String> props) {
        nacosConfigManager = manager;
        nacosProps = props;
    }

    public static NacosConfigManager getNacosConfigManager() {
        return nacosConfigManager;
    }

    public static Map<String, String> getNacosProps() {
        return nacosProps;
    }

}
