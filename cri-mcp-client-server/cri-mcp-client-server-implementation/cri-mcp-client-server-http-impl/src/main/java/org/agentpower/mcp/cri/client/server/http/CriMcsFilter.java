package org.agentpower.mcp.cri.client.server.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.agentpower.mcp.cri.api.client.server.CriMcpClientServer;
import org.agentpower.mcp.cri.api.client.server.CriMcsRequest;
import org.agentpower.mcp.cri.api.spec.CriMcpCommonEvents;
import org.agentpower.mcp.cri.api.spec.CriMcpEvent;
import org.agentpower.mcp.cri.api.spec.CriMcpServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class CriMcsFilter implements CriMcpClientServer, Filter {
    private static final Logger logger = LoggerFactory.getLogger(CriMcsFilter.class);
    private static final String HEADER_TRANSPORT_ID = "transport-id";
    private final ObjectMapper objectMapper;
    private final Set<String> promptUris;
    private final String callbackUri;
    private static final Map<String, Tuple4<
            AtomicReference<Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>>>,
            BlockingReference<CriMcpServerInfo>,
            HttpServletRequest,
            HttpServletResponse
            >> TRANSPORT_CONTAINER = new ConcurrentHashMap<>();

    public CriMcsFilter(ObjectMapper objectMapper, String promptUris, String callbackUri) {
        this.objectMapper = objectMapper;
        this.promptUris = Set.of(promptUris.split(","));
        this.callbackUri = callbackUri;
    }


    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        if (! (servletRequest instanceof HttpServletRequest request)) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }
        if (! (servletResponse instanceof HttpServletResponse response)) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }
        String requestPath = request.getPathInfo();
        if (requestPath == null) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }
        if (! requestPath.equals(callbackUri)) {
            if (promptUris.contains(requestPath)) {
                String transportId = response.getHeader(HEADER_TRANSPORT_ID);
                if (this.isBlankString(transportId)) {
                    badRequestResponse(response, "Missing Header: " + HEADER_TRANSPORT_ID);
                    return;
                }
                TRANSPORT_CONTAINER.computeIfAbsent(transportId,
                        key -> Tuples.of(new AtomicReference<>(), new BlockingReference<>(), request, response));
                response.setContentType("text/event-stream");
                response.setCharacterEncoding(StandardCharsets.UTF_8);
                response.setHeader("Cache-Control", "no-cache");
                response.setHeader("Connection", "keep-alive");
                response.setHeader("Access-Control-Allow-Origin", "*");
                try {
                    chain.doFilter(servletRequest, servletResponse);
                } finally {
                    clearAfterRequest(transportId);
                }
            } else {
                // not prompt uri
                // let go directly
                chain.doFilter(servletRequest, servletResponse);
            }
            return;
        }
        // callback request
        String transportId = getTidFromParameter(request);
        if (this.isBlankString(transportId)) {
            badRequestResponse(response, "Missing transportId");
            return;
        }
        var container = TRANSPORT_CONTAINER.get(transportId);
        if (container == null) {
            // it means the request is aborted
            badRequestResponse(response, "request is aborted");
            return;
        }
        try {
            BufferedReader reader = request.getReader();
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            CriMcpEvent event = objectMapper.readValue(body.toString(), CriMcpEvent.class);
            switch (event.event()) {
                case CriMcpCommonEvents.ENDPOINT_EVENT_TYPE:
                    // the endpoint event is sent with the server information
                    handleEndpointEvent(container, request, response, event);
                    break;
                case CriMcpCommonEvents.MESSAGE_EVENT_TYPE:
                    // receive a message which may be an encoded message
                    handleMessageEvent(container, request, response, event);
                    break;
                case CriMcpCommonEvents.CLOSE_EVENT_TYPE:
                    // the server is closing
                    handleCloseEvent(container, request, response, event);
                    break;
                default:
                    badRequestResponse(response, "Unknown event type");
                    break;
            }
        } catch (Exception e) {
            internalErrorResponse(response, e);
        }
    }

    private void clearAfterRequest(String transportId) {
        if (! isBlankString(transportId)) {
            TRANSPORT_CONTAINER.remove(transportId);
        }
//        TRANSPORT_CONTAINER.computeIfPresent(transportId, (key, value) -> {
//            if (value.getT1().intValue() == 1) {
//                // time to remove
//                return null;
//            } else {
//                value.getT1().decrement();
//                return value;
//            }
//        });
    }

    @Override
    public String generateTransportId() {
        return randomTransportId();
    }

    @Override
    public String getCallbackUri(String transportId) {
        return callbackUri + "?tid=" + transportId;
    }

    @Override
    public String getCallbackHeader(String transportId) {
        // TODO get callback header
        return null;
    }

    @Override
    public CriMcpServerInfo getServerInfo(String transportId, long timeout) {
        // notnull
        return TRANSPORT_CONTAINER.get(transportId).getT2().get(timeout);
    }

    @Override
    public Mono<Void> sendMessage(String transportId, CriMcsRequest criMcsRequest) {
        return Mono.fromRunnable(() -> {
            try {
                var container = TRANSPORT_CONTAINER.get(transportId);
                HttpServletResponse response = container.getT4();
                PrintWriter writer = response.getWriter();
                String requestJson = objectMapper.writeValueAsString(criMcsRequest);
                sendEvent(writer, CriMcpCommonEvents.MESSAGE_EVENT_TYPE, requestJson);
            } catch (IOException e){
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public Mono<Void> acceptMessageHandler(String transportId, Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
        Optional.ofNullable(TRANSPORT_CONTAINER.get(transportId))
                .map(Tuple2::getT1)
                .ifPresent(ref -> ref.set(handler));
        return Mono.empty();
    }

    @Override
    public String processRequestBody(String transportId, String messageJson, CriMcpServerInfo criMcpServerInfo) {
        // TODO process
        return messageJson;
    }

    @Override
    public String processRequestHeader(String transportId, String defaultRequestHeader, CriMcpServerInfo criMcpServerInfo) {
        // TODO process
        return defaultRequestHeader;
    }

    @Override
    public Mono<Void> closeGracefully(String transportId) {
        return !TRANSPORT_CONTAINER.containsKey(transportId) ? Mono.empty() :
                Mono.fromRunnable(() -> closeTransport(TRANSPORT_CONTAINER.get(transportId)));
    }

    protected void handleEndpointEvent(
            Tuple4<AtomicReference<Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>>>, BlockingReference<CriMcpServerInfo>, HttpServletRequest, HttpServletResponse> container,
            HttpServletRequest request, HttpServletResponse response, CriMcpEvent event) throws JsonProcessingException {
        String messageEndpoint = event.data();
        String[] split = messageEndpoint.split("&", 2);
        messageEndpoint = split[0];
//                serverInfo=xxx
        CriMcpServerInfo serverInfo;
        String serverInfoStr = split.length > 1 ? split[1] : "";
        if (this.isBlankString(serverInfoStr)) {
            serverInfo = CriMcpServerInfo.onlyEndpoint(messageEndpoint);
        } else {
            serverInfoStr = serverInfoStr.substring("serverInfo=".length());
            serverInfoStr = new String(Base64.getDecoder().decode(serverInfoStr), StandardCharsets.UTF_8);
            CriMcpServerInfo receivedInfo = objectMapper.readValue(serverInfoStr, CriMcpServerInfo.class);
            serverInfo = new CriMcpServerInfo(messageEndpoint,
                    receivedInfo.tokenCodec(), receivedInfo.paramCodec(), receivedInfo.dataCodec());
        }
        container.getT2().setAndCount(serverInfo);
        finishResponse(response);
    }

    protected void handleMessageEvent(
            Tuple4<AtomicReference<Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>>>, BlockingReference<CriMcpServerInfo>, HttpServletRequest, HttpServletResponse> container,
            HttpServletRequest request, HttpServletResponse response, CriMcpEvent event) throws IOException {
        String data = event.data();
        if (isEncoded(data)) {
            // TODO decode the data
            logger.debug("Received encoded message: {}", data);
        }
        McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(objectMapper, data);
        if (container.getT1().get() != null) {
            container.getT1().get().apply(Mono.just(message)).subscribe();
            finishResponse(response);
        } else {
            badRequestResponse(response, "Server is not ready yet");
        }
    }

    protected void handleCloseEvent(Tuple4<AtomicReference<Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>>>, BlockingReference<CriMcpServerInfo>, HttpServletRequest, HttpServletResponse> container, HttpServletRequest request, HttpServletResponse response, CriMcpEvent event) {
        closeTransport(container);
        finishResponse(response);
    }
    protected void closeTransport(Tuple4<AtomicReference<Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>>>, BlockingReference<CriMcpServerInfo>, HttpServletRequest, HttpServletResponse> container) {
        Optional.ofNullable(container)
                .ifPresent(value -> {
                    try {
                        PrintWriter writer = value.getT4().getWriter();
                        sendEvent(writer, CriMcpCommonEvents.CLOSE_EVENT_TYPE, "server is closing");
                        value.getT3().getAsyncContext().complete();
                    } catch (Exception e) {
                        logger.error("Failed to send close event to client: {}", e.getMessage());
                    }
                });
    }

    protected String getTidFromParameter(HttpServletRequest request) {
        String value = request.getParameter("tid");
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    protected String randomTransportId() {
        return UUID.randomUUID().toString();
    }

    private boolean isEncoded(String data) {
        String trim = data.trim();
        return ! trim.startsWith("{") && ! trim.startsWith("[");
    }

    private boolean isBlankString(String s) {
        return s == null || s.trim()
                .replace("\n", "")
                .replace("\r", "")
                .isEmpty();
    }

    private void sendEvent(PrintWriter writer, String eventType, String data) throws IOException {
        writer.write("event: " + eventType + "\n");
        writer.write("data: " + data + "\n\n");
        writer.flush();
        if (writer.checkError()) {
            throw new IOException("Client disconnected");
        }
    }

    private void finishResponse(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void badRequestResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        PrintWriter writer = response.getWriter();
        writer.write(objectMapper.writeValueAsString(new McpError(message)));
        writer.flush();
    }
    private void internalErrorResponse(HttpServletResponse response, Exception e) throws IOException {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        PrintWriter writer = response.getWriter();
        e.printStackTrace(writer);
        writer.flush();
    }
    protected static class BlockingReference<V> {
        private final AtomicReference<V> reference = new AtomicReference<>();
        private volatile CountDownLatch latch = new CountDownLatch(1);

        public void setAndCount(V value) {
            if (value != null) {
                latch.countDown();
            } else {
                latch = new CountDownLatch(1);
            }
            reference.set(value);
        }

        public V get(long timeout) {
            try {
                while (latch.await(timeout, TimeUnit.MILLISECONDS)) {
                }
            } catch (InterruptedException ignored) {}
            return reference.get();
        }
    }
}
