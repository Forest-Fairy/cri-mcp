package org.agentpower.mcp.cri.api.client.server;

import org.agentpower.mcp.cri.api.spec.CriMcpEvent;

import java.util.Map;

public class CriMcsCallback {
    /** id */
    public final String requestId;
    /** client-server callbackUri */
    public final String csUri;
    /** headers */
    public final Map<String, String> headers;
    /** event from mcp-server */
    public final CriMcpEvent event;

    public CriMcsCallback(String requestId, String csUri, Map<String, String> headers, CriMcpEvent event) {
        this.requestId = requestId;
        this.csUri = csUri;
        this.headers = headers;
        this.event = event;
    }

    public static CriMcsCallback fromRequest(CriMcsRequest request, CriMcpEvent event) {
        return new CriMcsCallback(request.requestId, request.callback.callbackUri(), request.callback.headers(), event);
    }
}
