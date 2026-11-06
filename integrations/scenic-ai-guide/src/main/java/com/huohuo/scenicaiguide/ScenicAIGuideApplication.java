package com.huohuo.scenicaiguide;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.huohuo.scenicaiguide.analytics.config.AnalyticsConfigProperties;
import com.huohuo.scenicaiguide.digitalhuman.config.DhConfigProperties;
import com.huohuo.scenicaiguide.qa.config.QaConfigProperties;
import com.huohuo.scenicaiguide.route.config.RouteConfigProperties;
import com.huohuo.scenicaiguide.system.config.AppConfigProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
    AppConfigProperties.class,
    QaConfigProperties.class,
    DhConfigProperties.class,
    RouteConfigProperties.class,
    AnalyticsConfigProperties.class
})
public class ScenicAIGuideApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScenicAIGuideApplication.class, args);
    }

}
