package hwddns;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @ClassName YmlConfig
 * @Deacription TODO
 * @Author TZ227
 * @Date 2022/4/27 19:54
 * @Version 1.0
 **/
@Data
@Component
@ConfigurationProperties(prefix = "domain")
public class DomainConfigBean {

    private List<Domain> list;

}
