package com.web.wps.propertie;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
@ConfigurationProperties(prefix = "server")
public class ServerProperties extends org.springframework.boot.autoconfigure.web.ServerProperties {

    private String domain;

}
