package org.agentpower.mcp.cri.client.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.agentpower.mcp.cri.api.client.server.CriMcpClientServer;
import org.agentpower.mcp.cri.api.client.server.CriMcsRequest;
import org.agentpower.mcp.cri.api.spec.CriMcpCommonEvents;
import org.agentpower.mcp.cri.api.spec.CriMcpServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class CriMcpClientServerTransport implements McpClientTransport {

    private static final long DEFAULT_ENDPOINT_TIMEOUT_SECONDS = 60L;
    private static final long DEFAULT_MESSAGE_TIMEOUT_SECONDS = 60L;

    /**
     * Default SSE endpoint path as specified by the MCP transport specification. This
     * endpoint is used to establish the SSE connection with the server.
     */
    private static final String DEFAULT_SSE_ENDPOINT = "/sse";
    private static final Logger logger = LoggerFactory.getLogger(CriMcpClientServerTransport.class);
    protected final ObjectMapper objectMapper;
    private final String id;
    private final String endpoint;
    private final long endpointTimeoutSec;
    private final long messageTimeoutSec;
    private final String baseUrl;
    private final Map<String, String> defaultJsonHeaders;
    private final CriMcpClientServer clientServer;
    private boolean isClosing;

    public CriMcpClientServerTransport(
            ObjectMapper objectMapper, String transportId, String baseUrl, Map<String, String> defaultJsonHeaders,
            CriMcpClientServer clientServer) {
        this(objectMapper, DEFAULT_SSE_ENDPOINT,
                DEFAULT_ENDPOINT_TIMEOUT_SECONDS, DEFAULT_MESSAGE_TIMEOUT_SECONDS, transportId,
                baseUrl, defaultJsonHeaders, clientServer);
    }
    public CriMcpClientServerTransport(
            ObjectMapper objectMapper, String endpoint, long endpointTimeoutSec, long messageTimeoutSec,
            String transportId, String baseUrl, Map<String, String> defaultJsonHeaders,
            CriMcpClientServer clientServer) {
        this.objectMapper = objectMapper;
        this.id = transportId;
        this.endpoint = endpoint;
        this.endpointTimeoutSec = endpointTimeoutSec;
        this.messageTimeoutSec = messageTimeoutSec;
        this.baseUrl = baseUrl;
        this.defaultJsonHeaders = defaultJsonHeaders;
        this.clientServer = clientServer;
        this.isClosing = false;
    }

    @Override
    public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
        CriMcsRequest request = CriMcsRequest.builder()
                .requestId(id)
                .msUrl(baseUrl + endpoint)
                .headers(defaultJsonHeaders)
                .body(CriMcpCommonEvents.ENDPOINT_EVENT_TYPE)
                .callbackBuilder()
                .uri(clientServer.getCallbackUri(id))
                .headers(clientServer.getCallbackHeader(id)).buildCallback()
                .build();
        sendMessage(request);
        return clientServer.acceptMessageHandler(id, handler);
    }

    @Override
    public Mono<Void> closeGracefully() {
        return clientServer.closeGracefully(id)
                .doOnSuccess(__ -> isClosing = true);
    }

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
        return Mono.fromRunnable(() -> {
            CriMcpServerInfo serverInfo = clientServer.getServerInfo(
                    id, endpointTimeoutSec * 1000L);
            if (serverInfo == null) {
                throw new McpError("Failed to wait for the message endpoint");
            }
            try {
                if (isClosing) {
                    return;
                }
                String jsonText = this.objectMapper.writeValueAsString(message);
                CriMcsRequest request = CriMcsRequest.builder()
                        .requestId(id)
                        .msUrl(baseUrl + serverInfo.messageEndpoint())
                        .headers(clientServer
                                .processRequestHeader(id, defaultJsonHeaders, serverInfo))
                        .body(clientServer
                                .processRequestBody(id, jsonText, serverInfo))
                        .callbackBuilder()
                        .uri(clientServer.getCallbackUri(id))
                        .headers(clientServer.getCallbackHeader(id))
                        .buildCallback()
                        .build();
                sendMessage(request);
            } catch (Exception e) {
                if (isClosing) {
                    logger.error("Failed to send message", e);
                    return;
                }
                throw new RuntimeException("Failed to send message", e);
            }
        });
    }

    private void sendMessage(CriMcsRequest request) {
        if (isClosing) {
            return;
        }
        clientServer.sendMessage(id, request)
                .block(Duration.ofSeconds(messageTimeoutSec));
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
        return this.objectMapper.convertValue(data, typeRef);
    }

}
