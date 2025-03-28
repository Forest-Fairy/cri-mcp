package org.agentpower.mcp.cri.api.server;

import org.agentpower.mcp.cri.api.CriMcpTransporter;

/**
 * mcp server interface
 *  it receive the request defined by self but return a standard response defined by cri-mcp
 * @param <Request> the request type
 * @param <Response> the response type extends {@link CriMCUIResponse}
 */
public interface CriMcpServer<Request, Response extends CriMCUIResponse> extends CriMcpTransporter<Request, Response> {
    @Override
    Response send(Request request);
}
