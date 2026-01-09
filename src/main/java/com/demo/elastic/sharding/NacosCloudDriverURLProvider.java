package com.sama.notice.sharding;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.shaded.com.google.common.base.Preconditions;
import com.core4ct.utils.DataUtils;
import com.core4ct.utils.StringUtils;
import org.apache.shardingsphere.driver.jdbc.core.driver.ShardingSphereURLProvider;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import static com.sama.api.notice.constant.ShardingNacosPropsEnum.getReplaceByShortKey;

/**
 * @author: huxh
 * @description: 实现ShardingSphereDriverURLProvider，调用NacosConfigManager获取配置
 * 注意点1：该Class需SPI注册
 * 注意点2：修改spring.datasource.url: jdbc:shardingsphere:nacos:nacos-sharding.yml
 * 注意点3：因为加载/监听事件的顺序，这里只加载了nacos-sharding.yml，无法通过载入 @Value("${init.mysqlUserName}")
 * @datetime: 2024/8/16 13:30
 */
public class NacosCloudDriverURLProvider implements ShardingSphereURLProvider {

    private static final String NACOS_TYPE = "nacos:";

    /**
     * url 包含了 nacos: 时，表示使用当前类加载配置
     */
    @Override
    public boolean accept(String url) {
        return StringUtils.isNotBlank(url) && url.contains(NACOS_TYPE);
    }

    @Override
    public byte[] getContent(String url, String s1) {
        // 检查
        String dataId = StringUtils.substringAfter(url, NACOS_TYPE);
        Preconditions.checkArgument(StrUtil.isNotEmpty(dataId), "dataId 不可为空！");

        NacosConfigManager nacosConfigManager = NacosConfigManagerUtils.getNacosConfigManager();
        Preconditions.checkArgument(ObjectUtil.isNotNull(nacosConfigManager), "NacosConfigManager 不可为空！");

        Map<String, String> nacosProps = NacosConfigManagerUtils.getNacosProps();
        Preconditions.checkArgument(DataUtils.isNotEmpty(nacosProps), "未获取到yml的其它初始化参数！");

        // 获取配置并赋值
        ConfigService configService = nacosConfigManager.getConfigService();
        NacosConfigProperties nacosConfigProperties = nacosConfigManager.getNacosConfigProperties();
        try {
            String content = configService.getConfig(dataId, nacosConfigProperties.getGroup(), nacosConfigProperties.getTimeout());
            // 手动替换
            for (Map.Entry<String, String> entry : nacosProps.entrySet()) {
                content = content.replace(Objects.requireNonNull(getReplaceByShortKey(entry.getKey())), entry.getValue());
            }
            System.out.println(content);
            return content.getBytes(StandardCharsets.UTF_8);
        } catch (NacosException e) {
            throw new RuntimeException(e);
        }
    }
}
