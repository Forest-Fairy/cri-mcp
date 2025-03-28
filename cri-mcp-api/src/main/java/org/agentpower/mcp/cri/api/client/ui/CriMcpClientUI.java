package org.agentpower.mcp.cri.api.client.ui;

import org.agentpower.mcp.cri.api.CriMcpTransporter;

/**
 * MCUI interface
 *  it has two implementations for MCS and MS
 * @param <Request> the request type from MCS or MS
 * @param <Response> the response type to MCS or MS
 */
public interface CriMcpClientUI<Request, Response> extends CriMcpTransporter<Request, Response> {
    CriMcpServerInfo getServerInfo();
}
