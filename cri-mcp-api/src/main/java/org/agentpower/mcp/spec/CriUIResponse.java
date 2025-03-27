package org.agentpower.mcp.spec;

/**
 * {@link CriMcsRequest} 由 MCS 发送给 MCUI，MCUI解析后对 MS 发起请求，返回 {@link CriUIResponse} 给 MCS
 */
public class CriUIResponse {
    public final String respHeaders;
    public final String body;
    public CriUIResponse(String respHeaders, String body) {
        this.respHeaders = respHeaders;
        this.body = body;
    }
}
