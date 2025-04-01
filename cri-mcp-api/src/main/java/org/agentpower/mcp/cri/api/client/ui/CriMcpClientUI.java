package org.agentpower.mcp.cri.api.client.ui;

import org.agentpower.mcp.cri.api.client.server.CriMcsRequest;
import reactor.core.publisher.Mono;

/**
 * MCUI interface
 *  sending a request to mcp-server
 *  and sending the response with callback request to mcp-client-server
 */
public interface CriMcpClientUI {
    Mono<Void> sendRequest(CriMcsRequest request);
    Mono<Void> sendCallback(CriMcsRequest request);
    Mono<Void> closeGracefully();
}
