package org.agentpower.mcp.cri.api.client.server;


import org.agentpower.mcp.cri.api.CriMcpTransporter;

/**
 * mcp client server interface
 *  it handles with the request type defined by cri-mcp and return the response type defined by itself
 * @param <Request> the request type extends {@link CriMcsRequest}
 * @param <Response> the response type
 */
public interface CriMcpClientServer<Request extends CriMcsRequest, Response> extends CriMcpTransporter<Request, Response> {
    @Override
    Response send(Request criMcsRequest);
}
