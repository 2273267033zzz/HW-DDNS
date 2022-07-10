package hwddns;

import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.auth.ICredential;
import com.huaweicloud.sdk.core.exception.ConnectionException;
import com.huaweicloud.sdk.core.exception.RequestTimeoutException;
import com.huaweicloud.sdk.core.exception.ServiceResponseException;
import com.huaweicloud.sdk.dns.v2.DnsClient;
import com.huaweicloud.sdk.dns.v2.model.UpdateRecordSetReq;
import com.huaweicloud.sdk.dns.v2.model.UpdateRecordSetRequest;
import com.huaweicloud.sdk.dns.v2.model.UpdateRecordSetResponse;
import com.huaweicloud.sdk.dns.v2.region.DnsRegion;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
@Slf4j
public class DomainsAnalysis {

    private static String v4Ip = "";
    private static String v6Ip = "";

    private final static String IPV4TYPE = "A";
    private final static String IPV6TYPE = "AAAA";

    @Autowired
    private DomainConfigBean configBean;

    /**
     * @param
     * @return void
     * @Author zzz
     * @Description //TODO 定时任务 600秒查询一次当前公网ip,对比旧的公网ip和dns里的ip,如果不同就更新解析记录
     * @Date 15:17 2021/3/18
     */
    @Scheduled(cron = "0 0/10 * * * ?")
    public void DDNS() throws IOException {
        log.info("进入DDNS");
        //获取ip地址
        String newV4Ip = getIp(IPV4TYPE);
        Boolean ipv4Pattern = matchIp(IPV4TYPE, newV4Ip);
        log.info("newV4Ip:{}", newV4Ip);
        log.info("ipv4Pattern:{}", ipv4Pattern);
        log.info("原v4ip:{}", v4Ip);

        String newV6Ip = getIp(IPV6TYPE);
        Boolean ipv6Pattern = matchIp(IPV6TYPE, newV6Ip);
        log.info("newV6Ip:{}", newV6Ip);
        log.info("ipv6Pattern:{}", ipv6Pattern);
        log.info("原v6ip:{}", v6Ip);
        List<Domain> domainList = configBean.getList();
        log.info("获取配置文件域名：{}", domainList);

        for (Domain domain : domainList) {
            log.info("循环解析域名当前域名:{}", domain);
            boolean ipPattern = domain.getType().equals(IPV4TYPE) ? ipv4Pattern : ipv6Pattern;
            String newIp = domain.getType().equals(IPV4TYPE) ? newV4Ip : newV6Ip;
            String ip = domain.getType().equals(IPV4TYPE) ? v4Ip : v6Ip;
            if (ipPattern && StringUtils.isNotBlank(newIp) && !ip.equals(newIp)) {
                log.info("开始调用dns接口");
                ip = newIp;
                String ak = domain.getAk();
                String sk = domain.getSk();
                ICredential auth = new BasicCredentials()
                        .withAk(ak)
                        .withSk(sk);
                DnsClient client = DnsClient.newBuilder()
                        .withCredential(auth)
                        .withRegion(DnsRegion.valueOf("cn-east-3"))
                        .build();
                UpdateRecordSetRequest request = new UpdateRecordSetRequest();
                request.withZoneId(domain.getZoneId());
                request.withRecordsetId(domain.getRecordsetId());
                UpdateRecordSetReq body = new UpdateRecordSetReq();
                List<String> listbodyRecords = new ArrayList<>();
                listbodyRecords.add(ip);
                body.withRecords(listbodyRecords);
                body.withType(domain.getType());
                body.withName(domain.getDomainName() + ".");
                request.withBody(body);
                try {
                    UpdateRecordSetResponse response = client.updateRecordSet(request);
                    log.info(response.toString());
                } catch (ConnectionException | RequestTimeoutException e) {
                    log.info("{}", e.getMessage());
                } catch (ServiceResponseException e) {
                    e.printStackTrace();
                    log.info("{}", e.getHttpStatusCode());
                    log.info("{}", e.getErrorCode());
                    log.info("{}", e.getErrorMsg());
                }
                v4Ip = newV4Ip;
                v6Ip = newV6Ip;
            }
        }
    }

    public static String getIp(String type) {
        //默认ipv4
        if ("A".equals(type)) {
            String url = "http://www.3322.org/dyndns/getip";
            String result = sendRequest(url);
            if (StringUtils.isBlank(result)) {
                url = "http://www.net.cn/static/customercare/yourip.asp";
                result = sendRequest(url);
                result = result.substring(result.indexOf("<h2>") + 4, result.indexOf("</h2>"));
            }
            if (StringUtils.isBlank(result)) {
                url = "https://www.taobao.com/help/getip.php";
                result = sendRequest(url);
                result = result.substring(result.indexOf("ip:\"") + 4, result.indexOf("\"})"));
            }
            return result;
        } else {//如果是主机类型为AAAA 获取ipv6地址
            String url = "http://v6.meibu.com/ips.asp";
            return sendRequest(url);
        }

    }

    private static String sendRequest(String url) {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        String result = "";
        try {
            CloseableHttpResponse response = client.execute(httpGet);
            HttpEntity entity = response.getEntity();
            result = EntityUtils.toString(entity, "UTF-8");
            log.info("接口返回:{}", result);
            result = StringUtils.trim(result);
            response.close();
            client.close();
        } catch (IOException e) {
            log.info("接口异常");
        }
        return result;
    }


    /**
     * @param ip
     * @return java.lang.Boolean
     * @Author zzz
     * @Description //TODO 验证接口获取到的是ip
     * @Date 13:28 2022/2/25
     */
    public static Boolean matchIp(String type, String ip) {
        String pattern = "((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2})(\\.((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2})){3}";
        if (type.equals("AAAA")) {
            pattern = "^\\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:)))(%.+)?\\s*$";
        }
        Pattern r = Pattern.compile(pattern);
        return r.matcher(ip).matches();
    }


}
