package org.agentpower.mcp.cri.api.client.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.AllArgsConstructor;
import org.agentpower.mcp.cri.api.client.ui.CriMcpServerInfo;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

@AllArgsConstructor
public class CriMcpClientServerTransport implements CriMcpClientServer {

    /** SSE event type for JSON-RPC messages */
    private static final String MESSAGE_EVENT_TYPE = "message";

    /** SSE event type for endpoint discovery */
    private static final String ENDPOINT_EVENT_TYPE = "endpoint";

    /**
     * Default SSE endpoint path as specified by the MCP transport specification. This
     * endpoint is used to establish the SSE connection with the server.
     */
    private static final String DEFAULT_SSE_ENDPOINT = "/sse";

    private final Supplier<ServletResponse> responseSupplier;
    private final Supplier<ServletRequest> requestSupplier;
    private final Function<String, String> eventMessageSupplier;
    private final BiFunction<String, CriMcpServerInfo, String> requestBodyProcessor;
    private final BiFunction<String, CriMcpServerInfo, String> requestHeaderProcessor;
    private final Function<Map<String, String>, String> callbackHeaderProcessor;
    private final Function<String, CriMcpServerInfo> endpointEventHandler;
    protected final ObjectMapper objectMapper;
    private final String id;
    private final String baseUrl;
    private final String defaultJsonHeaders;
    private final String endpoint;
    private final String callbackUri;
    private final Map<String, String> callbackHeaders;

    private final


    @Override
    public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
        return Mono.fromRunnable(() -> {
            CriMcsRequest request = CriMcsRequest.builder()
                    .requestId(id)
                    .msUrl(baseUrl + endpoint)
                    .headers(defaultJsonHeaders)
                    .body(ENDPOINT_EVENT_TYPE)
                    .buildCallback()
                    .uri(callbackUri)
                    .headers(callbackHeaderProcessor.apply(callbackHeaders)).build()
                    .build();
            sendMessage(request);
            // handle
            while (true) {
                String encryptedMessage = eventMessageSupplier.apply(id);
                if (encryptedMessage != null) {
                    Mono<McpSchema.JSONRPCMessage> message = decData(encryptedMessage);
                    handler.apply(message);
                }
            }
        }).then();
    }

    private Mono<McpSchema.JSONRPCMessage> decData(String encryptedMessage) {
        return null;
    }

    @Override
    public Mono<Void> closeGracefully() {
        // TODO
        return null;
    }

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
        // ServerInfo
        return Mono.<Void>fromRunnable(() -> {
            CriMcpServerInfo serverInfo = endpointEventHandler.apply(id);
            if (serverInfo == null) {
                throw new McpError("Failed to wait for the message endpoint");
            }
            try {
                String jsonText = this.objectMapper.writeValueAsString(message);
                CriMcsRequest request = CriMcsRequest.builder()
                        .requestId(id)
                        .msUrl(baseUrl + serverInfo.messageEndpoint())
                        .headers(requestHeaderProcessor.apply(defaultJsonHeaders, serverInfo))
                        .body(requestBodyProcessor.apply(jsonText, serverInfo))
                        .buildCallback()
                        .uri(callbackUri)
                        .headers(callbackHeaderProcessor.apply(callbackHeaders)).build()
                        .build();
                sendMessage(request);
            } catch (Exception e) {
                throw new RuntimeException("Failed to send message", e);
            }
        });
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
        return this.objectMapper.convertValue(data, typeRef);
    }

    private void sendMessage(CriMcsRequest request) {
        // TODO
    }

}
