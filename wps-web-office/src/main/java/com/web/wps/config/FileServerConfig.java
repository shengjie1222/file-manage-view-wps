package com.web.wps.config;

import com.web.wps.propertie.LocalhostProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

/**
 * @author S.j@onlying.cn
 * @date 2019/12/23 11:12:24
 */
@Configuration
@Slf4j
public class FileServerConfig extends WebMvcConfigurationSupport {
    private static String[] URL_PATTERNS = new String[]{"/v1/**"};


    private final LocalhostProperties localhostProperties;

    @Autowired
    public FileServerConfig(LocalhostProperties localhostProperties) {
        this.localhostProperties = localhostProperties;
    }

    @Value("${server.port}")
    private String port;

    /**
     * 注册拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserHandlerAdapter()).addPathPatterns(URL_PATTERNS);
        super.addInterceptors(registry);
    }


    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String osName = System.getProperties().getProperty("os.name");
        String prefix = "file:";
        if(osName.equals("Linux"))  prefix = "";
        String resourceLocations = prefix + this.getLocalFileServerDir() ;
        String pathPatterns = this.getLocalFileServerPath() + "**";
        registry.addResourceHandler(pathPatterns).addResourceLocations(resourceLocations);
        log.warn("当前系统运行环境:{},映射仓库:{}->{}",osName,pathPatterns,resourceLocations);
        super.addResourceHandlers(registry);
    }

    /**
     * 文件实际路径转为服务器url路径
     * @param absolutePath
     * @return
     */
    public String toServerPath(String absolutePath) {
        String url = getLocalFileHttp() + absolutePath.replace(getLocalFileServerDir(), getLocalFileServerPath());
        url = url.replaceAll("\\\\","/");
        return url;
    }

    public String getLocalFileHttp() {
        return "http://" + localhostProperties.getOuterNetIp() + ":" + port;
    }

    public String getLocalFileServerDir() {
        return localhostProperties.getFileDir();
    }


    public String getLocalFileServerPath() {
        return localhostProperties.getFileUrlPrefix();
    }

}
