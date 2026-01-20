package hwddns;

import com.alibaba.fastjson.JSONObject;
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
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Component
@Slf4j
public class DomainsAnalysis {

    private static final String IPV4_TYPE = "A";
    private static final String IPV6_TYPE = "AAAA";
    // 预编译正则，提高性能
    private static final Pattern IPV4_PATTERN = Pattern.compile("((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2})(\\.((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2})){3}");
    private static final Pattern IPV6_PATTERN = Pattern.compile("^\\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:)))(%.+)?\\s*$");

    // 使用实例变量替代静态变量
    private String currentV4Ip = "";
    private String currentV6Ip = "";

    // 复用 HttpClient
    private CloseableHttpClient httpClient;

    @Autowired
    private DomainConfigBean configBean;

    @PostConstruct
    public void init() {
        // 配置超时时间
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(5000)
                .setSocketTimeout(5000)
                .setConnectionRequestTimeout(5000)
                .build();
        this.httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @PreDestroy
    public void destroy() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                log.error("关闭HttpClient时发生异常", e);
            }
        }
    }

    /**
     * 定时任务：每600秒（10分钟）执行一次
     * 获取当前公网IP，对比旧IP和DNS记录，如果不同则更新
     */
    @Scheduled(fixedDelay = 600000)
    public void executeDdnsTask() {
        log.info("=== 开始执行DDNS任务 ===");

        List<Domain> domainList = configBean.getList();
        if (domainList == null || domainList.isEmpty()) {
            log.warn("未配置域名信息，任务结束");
            return;
        }

        log.debug("获取到的域名配置: {}", JSONObject.toJSONString(domainList));

        // 1. 获取最新的公网IP
        String newV4Ip = getPublicIp(IPV4_TYPE);
        String newV6Ip = getPublicIp(IPV6_TYPE);

        log.info("当前检测到的公网IP - IPv4: {}, IPv6: {}", newV4Ip, newV6Ip);

        // 2. 遍历域名进行处理
        for (Domain domain : domainList) {
            processDomain(domain, newV4Ip, newV6Ip);
        }

        // 3. 更新本地缓存的IP
        if (StringUtils.isNotBlank(newV4Ip)) {
            this.currentV4Ip = newV4Ip;
        }
        if (StringUtils.isNotBlank(newV6Ip)) {
            this.currentV6Ip = newV6Ip;
        }
        
        log.info("=== DDNS任务执行结束 ===");
    }

    private void processDomain(Domain domain, String newV4Ip, String newV6Ip) {
        String type = domain.getType();
        String newIp;
        String oldIp;

        if (IPV4_TYPE.equals(type)) {
            newIp = newV4Ip;
            oldIp = currentV4Ip;
        } else if (IPV6_TYPE.equals(type)) {
            newIp = newV6Ip;
            oldIp = currentV6Ip;
        } else {
            log.warn("不支持的域名类型: {}, 域名: {}", type, domain.getDomainName());
            return;
        }

        // 如果没有获取到对应类型的IP，跳过
        if (StringUtils.isBlank(newIp)) {
            log.warn("无法获取有效的 {} 地址，跳过域名: {}", type, domain.getDomainName());
            return;
        }

        // 校验IP格式
        if (!isValidIp(type, newIp)) {
             log.warn("获取到的IP格式无效: {}, 类型: {}", newIp, type);
             return;
        }

        log.info("域名: {}, 类型: {}, 新IP: {}, 本地缓存IP: {}", domain.getDomainName(), type, newIp, oldIp);

        // 如果IP没有变化，则不更新
        if (StringUtils.equals(oldIp, newIp)) {
            log.info("域名 {} 的IP未发生变化，无需更新", domain.getDomainName());
            return;
        }

        log.info("检测到IP变化，准备更新DNS记录...");
        updateDnsRecord(domain, newIp);
    }

    private void updateDnsRecord(Domain domain, String ip) {
        try {
            ICredential auth = new BasicCredentials()
                    .withAk(domain.getAk())
                    .withSk(domain.getSk());
            
            // TODO: Region 目前硬编码为 cn-east-3，建议后续支持配置
            DnsClient client = DnsClient.newBuilder()
                    .withCredential(auth)
                    .withRegion(DnsRegion.valueOf("cn-east-3"))
                    .build();

            UpdateRecordSetRequest request = new UpdateRecordSetRequest();
            request.withZoneId(domain.getZoneId());
            request.withRecordsetId(domain.getRecordsetId());
            
            UpdateRecordSetReq body = new UpdateRecordSetReq();
            body.withRecords(Collections.singletonList(ip));
            body.withType(domain.getType());
            // 华为云DNS API 要求域名后加点
            body.withName(domain.getDomainName() + "."); 
            request.withBody(body);

            UpdateRecordSetResponse response = client.updateRecordSet(request);
            log.info("DNS更新成功 - 域名: {}, 响应: {}", domain.getDomainName(), response.toString());
            
        } catch (ConnectionException | RequestTimeoutException e) {
            log.error("DNS更新连接异常: {}", e.getMessage());
        } catch (ServiceResponseException e) {
            log.error("DNS服务响应异常: Code={}, Msg={}, RequestId={}", e.getErrorCode(), e.getErrorMsg(), e.getRequestId());
        } catch (Exception e) {
            log.error("DNS更新未知异常", e);
        }
    }

    private String getPublicIp(String type) {
        if (IPV4_TYPE.equals(type)) {
            String[] urls = {
                "https://4.ipw.cn",
                "http://www.3322.org/dyndns/getip",
                "http://www.net.cn/static/customercare/yourip.asp",
                "https://www.taobao.com/help/getip.php"
            };
            
            for (String url : urls) {
                String result = fetchContent(url);
                if (StringUtils.isBlank(result)) continue;
                
                // 针对不同接口的返回格式进行解析
                try {
                    if (url.contains("net.cn")) {
                        result = result.substring(result.indexOf("<h2>") + 4, result.indexOf("</h2>"));
                    } else if (url.contains("taobao.com")) {
                        result = result.substring(result.indexOf("ip:\"") + 4, result.indexOf("\"})"));
                    }
                } catch (Exception e) {
                    log.warn("解析IP响应内容失败: {}, url: {}", e.getMessage(), url);
                    continue;
                }
                
                if (isValidIp(IPV4_TYPE, result)) {
                    return result;
                }
            }
        } else if (IPV6_TYPE.equals(type)) {
            String url = "https://6.ipw.cn";
            String result = fetchContent(url);
             if (isValidIp(IPV6_TYPE, result)) {
                return result;
            }
        }
        return null;
    }

    private String fetchContent(String url) {
        HttpGet httpGet = new HttpGet(url);
        // 设置Header模拟浏览器
        httpGet.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3");

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            if (response.getStatusLine().getStatusCode() == 200) {
                String content = EntityUtils.toString(response.getEntity(), "UTF-8");
                return StringUtils.trim(content);
            } else {
                log.warn("请求URL返回非200状态: {}, Code: {}", url, response.getStatusLine().getStatusCode());
            }
        } catch (IOException e) {
            log.warn("请求URL失败: {}, 错误: {}", url, e.getMessage());
        }
        return null;
    }

    private boolean isValidIp(String type, String ip) {
        if (StringUtils.isBlank(ip)) return false;
        if (IPV4_TYPE.equals(type)) {
            return IPV4_PATTERN.matcher(ip).matches();
        } else if (IPV6_TYPE.equals(type)) {
            return IPV6_PATTERN.matcher(ip).matches();
        }
        return false;
    }
}
