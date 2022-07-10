package hwddns;

import lombok.Data;

import java.io.Serializable;

/**
 * @ClassName DomainNameList
 * @Deacription TODO yml配置文件的集合对象
 * @Author TZ227
 * @Date 2022/4/27 19:46
 * @Version 1.0
 **/
@Data
public class Domain implements Serializable {
    private String ak;
    private String sk;
    private String zoneId;
    private String recordsetId;
    private String type;
    private String domainName;
}
