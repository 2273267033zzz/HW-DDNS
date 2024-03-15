package hwddns;

import java.io.Serializable;

/**
 * @ClassName DomainNameList
 * @Deacription TODO yml配置文件的集合对象
 * @Author TZ227
 * @Date 2022/4/27 19:46
 * @Version 1.0
 **/
public class Domain implements Serializable {
    private String ak;
    private String sk;
    private String zoneId;
    private String recordsetId;
    private String type;
    private String domainName;

    public String getAk() {
        return ak;
    }

    public void setAk(String ak) {
        this.ak = ak;
    }

    public String getSk() {
        return sk;
    }

    public void setSk(String sk) {
        this.sk = sk;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getRecordsetId() {
        return recordsetId;
    }

    public void setRecordsetId(String recordsetId) {
        this.recordsetId = recordsetId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }
}
