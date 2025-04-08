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
     * generate a transport id for transport which should be set in the request header of the prompt request
     * @return uuid
     */
    String generateTransportId();

    /**
     * get the callback uri of the cri-mcs
     * @param transportId the transport id
     * @return callback uri
     */
    String getCallbackUri(String transportId);

    /**
     * get the callback header of the cri-mcs
     * @param transportId the transport id
     * @return callback header
     */
    Map<String, String> getCallbackHeader(String transportId);

    /**
     * get the server info of the cri-ms
     * @param transportId the transport id
     * @param timeout timeout
     * @return the information of a mcp-server
     */
    CriMcpServerInfo getServerInfo(String transportId, long timeout);

    /**
     * send a message to the cri-mcui
     * @param transportId the transport id
     * @param criMcsRequest the request
     * @return mono
     */
    Mono<Void> sendMessage(String transportId, CriMcsRequest criMcsRequest);

    /**
     * accept a message handler
     * @param transportId the transport id
     * @param handler the handler
     * @return mono
     */
    Mono<Void> acceptMessageHandler(String transportId, Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler);

    /**
     * process the request body, such as a compress, an encryption...
     * @param transportId the transport id
     * @param messageJson the message json
     * @param criMcpServerInfo the server info
     * @return the processed message json
     */
    String processRequestBody(String transportId, String messageJson, CriMcpServerInfo criMcpServerInfo);

    /**
     * process the request header, may the headers that the external system requires
     * @param transportId the transport id
     * @param defaultRequestHeader the default request header
     * @param criMcpServerInfo the server info
     * @return the processed request header
     */
    Map<String, String> processRequestHeader(String transportId, Map<String, String> defaultRequestHeader, CriMcpServerInfo criMcpServerInfo);

    /**
     * close the cri-mcs gracefully
     * @param transportId the transport id
     * @return mono
     */
    Mono<Void> closeGracefully(String transportId);

}
