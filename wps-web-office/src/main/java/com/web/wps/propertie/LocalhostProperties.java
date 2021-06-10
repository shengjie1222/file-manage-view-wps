package com.web.wps.propertie;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author Administrator
 */
@Data
@Component
@ConfigurationProperties(prefix = "localhost")
public class LocalhostProperties {

    private String outerNetIp;
    private String fileUrlPrefix;
    private String fileDir;

}
