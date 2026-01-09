package com.sama.notice.sharding;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.core4ct.utils.DataUtils;
import com.sama.api.notice.constant.ShardingNacosPropsEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author: huxh
 * @description: 定义一个应用监听器ApplicationListener，监听容器刷新完成事件，从容器中获取NacosConfigManager并赋值给工具类。
 * 这样可以确保在应用启动完成前提前加载，确保ShardingSphere在任何时间点都能获取配置，并随时把 @Value("${init.}")更新进去。
 * @datetime: 2024/8/16 13:28
 */
@Configuration
// @Order(-100)
@Component
public class ShardingSphereContextRefreshedEventListener implements ApplicationListener<ContextRefreshedEvent> {

    @Value("${init.mysqlHost}")
    private String mysqlHost;

    @Value("${init.mysqlUserName}")
    private String mysqlUserName;

    @Value("${init.mysqlPassword}")
    private String mysqlPassword;

    /**
     * 环境准备完成时
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext applicationContext = event.getApplicationContext();
        NacosConfigManager nacosConfigManager = applicationContext.getBean(NacosConfigManager.class);
        if (DataUtils.isNotEmpty(mysqlHost)){
            // 因为刷新的时机不同，只有获得了赋值后的nacos配置才有效
            Map<String, String> nacosProps = ShardingNacosPropsEnum.getValuedMap(mysqlHost, mysqlUserName, mysqlPassword);
            NacosConfigManagerUtils.initWithProps(nacosConfigManager, nacosProps);
        }
        NacosConfigManagerUtils.init(nacosConfigManager);
    }

}
