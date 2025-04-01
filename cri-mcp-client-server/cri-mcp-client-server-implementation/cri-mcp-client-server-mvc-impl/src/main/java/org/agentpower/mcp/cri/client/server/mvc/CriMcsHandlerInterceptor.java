package org.agentpower.mcp.cri.client.server.mvc;

import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.agentpower.mcp.cri.api.client.server.CriMcpClientServer;
import org.agentpower.mcp.cri.api.client.server.CriMcsRequest;
import org.agentpower.mcp.cri.api.spec.CriMcpServerInfo;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

public class CriMcsHandlerInterceptor implements CriMcpClientServer, HandlerInterceptor {
    private static final String GENERATE_TRANSPORT_ID = "transport/id/generate";
    private static final String HEADER_TRANSPORT_ID = "transport-id";
    private final String callbackUri;
    private final RouterFunction<ServerResponse> routerFunction;
    private static final Map<String, Tuple3<LongAdder, HttpServletRequest, HttpServletResponse>>
        TRANSPORT_CONTAINER = new ConcurrentHashMap<>();

    public CriMcsHandlerInterceptor(String callbackUri) {
        this.callbackUri = callbackUri;
        this.routerFunction = RouterFunctions.route()
                .GET(GENERATE_TRANSPORT_ID, this::handleTransportIdGenerateRequest)
                .POST(this.callbackUri, this::handleCallbackRequest)
                .build();
    }

    public RouterFunction<ServerResponse> getRouterFunction() {
        return routerFunction;
    }

    private ServerResponse handleTransportIdGenerateRequest(ServerRequest serverRequest) {
        return null;
    }

    private ServerResponse handleCallbackRequest(ServerRequest serverRequest) {
        return null;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (request.getPathInfo().equals(GENERATE_TRANSPORT_ID)) {
            return true;
        }
        String transportId = request.getHeader(HEADER_TRANSPORT_ID);
        if (transportId == null || transportId.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            PrintWriter writer = response.getWriter();
            writer.write("Missing header: transport-id");
            writer.flush();
            return false;
        }
        var transportContainer = TRANSPORT_CONTAINER.computeIfAbsent(transportId, key -> Tuples.of(new LongAdder(), request, response));
        transportContainer.getT1().increment();
        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        String transportId = request.getHeader(HEADER_TRANSPORT_ID);
        if (transportId != null) {
            TRANSPORT_CONTAINER.computeIfPresent(transportId, (key, value) -> {
                if (value.getT1().intValue() == 1) {
                    // time to remove
                    return null;
                } else {
                    value.getT1().decrement();
                    return value;
                }
            });
        }
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

    @Override
    public String getCallbackUri() {
        return callbackUri;
    }

    @Override
    public String getCallbackHeader(String transportId) {
        return "";
    }

    @Override
    public CriMcpServerInfo getServerInfo(String transportId) {
        return null;
    }

    @Override
    public Mono<Void> sendMessage(String transportId, CriMcsRequest criMcsRequest) {
        return null;
    }

    @Override
    public Mono<Void> acceptMessageHandler(String transportId, Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
        return null;
    }

    @Override
    public String processRequestBody(String transportId, String requestBody, CriMcpServerInfo criMcpServerInfo) {
        return "";
    }

    @Override
    public String processRequestHeader(String transportId, String requestHeader, CriMcpServerInfo criMcpServerInfo) {
        return "";
    }

    @Override
    public String processCallbackHeader(String transportId, Map<String, String> callbackHeader) {
        return "";
    }

    @Override
    public Mono<Void> closeGracefully(String transportId) {
        return null;
    }
}
