package org.agentpower.mcp.cri.api.server;

import org.agentpower.mcp.cri.api.client.server.CriMcsRequest;

/**
 * {@link CriMcsRequest} 由 MCS 发送给 MCUI，MCUI解析后对 MS 发起请求，返回 {@link CriMCUIResponse} 给 MCS
 */
public class CriMCUIResponse {
    public final String respHeaders;
    public final String body;
    public CriMCUIResponse(String respHeaders, String body) {
        this.respHeaders = respHeaders;
        this.body = body;
    }
}
