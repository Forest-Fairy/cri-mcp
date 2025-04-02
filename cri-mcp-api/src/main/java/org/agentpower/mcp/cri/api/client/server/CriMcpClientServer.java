package org.agentpower.mcp.cri.api.client.server;

import io.modelcontextprotocol.spec.McpSchema;
import org.agentpower.mcp.cri.api.spec.CriMcpServerInfo;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Function;

/**
 * mcp client server interface
 *  it handles the messages with client transport
 */
public interface CriMcpClientServer {
    /**
     * generate a transport id for transport and some initial variables
     * @return uuid
     */
    String generateTransportId();
    String getCallbackUri(String transportId);
    String getCallbackHeader(String transportId);
    CriMcpServerInfo getServerInfo(String transportId, long timeout);
    Mono<Void> sendMessage(String transportId, CriMcsRequest criMcsRequest);
    Mono<Void> acceptMessageHandler(String transportId, Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler);
    String processRequestBody(String transportId, String messageJson, CriMcpServerInfo criMcpServerInfo);
    String processRequestHeader(String transportId, String defaultRequestHeader, CriMcpServerInfo criMcpServerInfo);
    Mono<Void> closeGracefully(String transportId);

}
