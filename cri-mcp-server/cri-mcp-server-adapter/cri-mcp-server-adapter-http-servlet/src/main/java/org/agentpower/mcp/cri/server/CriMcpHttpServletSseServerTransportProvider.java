package org.agentpower.mcp.cri.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.*;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.agentpower.mcp.cri.api.server.CriMcpServer;
import org.agentpower.mcp.cri.api.spec.CriMcpCommonEvents;
import org.agentpower.mcp.cri.api.spec.CriMcpServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@WebServlet(asyncSupported = true)
public class CriMcpHttpServletSseServerTransportProvider extends HttpServlet implements McpServerTransportProvider {

    /** Logger for this class */
    private static final Logger logger = LoggerFactory.getLogger(CriMcpHttpServletSseServerTransportProvider.class);

    public static final String CRI_SID_PREFIX = "cri-";

    public static final String UTF_8 = "UTF-8";

    public static final String APPLICATION_JSON = "application/json";

    public static final String FAILED_TO_SEND_ERROR_RESPONSE = "Failed to send error response: {}";

    /** Default endpoint path for SSE connections */
    public static final String DEFAULT_SSE_ENDPOINT = "/sse";

    /** Event type for regular messages */
    public static final String MESSAGE_EVENT_TYPE = CriMcpCommonEvents.MESSAGE_EVENT_TYPE;

    /** Event type for endpoint information */
    public static final String ENDPOINT_EVENT_TYPE = CriMcpCommonEvents.ENDPOINT_EVENT_TYPE;

    /** JSON object mapper for serialization/deserialization */
    private final ObjectMapper objectMapper;

    /**
     * Current mcp-server
     */
    private final CriMcpServer server;

    /** The endpoint path for handling SSE connections */
    private final String sseEndpoint;

    /** Map of active client sessions, keyed by session ID */
    private final Map<String, McpServerSession> sessions = new ConcurrentHashMap<>();
    /** Map of active client headers, keyed by session ID */
    private final Map<String, Map<String, String>> sessionHeaders = new ConcurrentHashMap<>();

    /** Flag indicating if the transport is in the process of shutting down */
    private final AtomicBoolean isClosing = new AtomicBoolean(false);

    /** Session factory for creating new sessions */
    private McpServerSession.Factory sessionFactory;


    public CriMcpHttpServletSseServerTransportProvider(ObjectMapper objectMapper, CriMcpServer server, String sseEndpoint) {
        this.objectMapper = objectMapper;
        this.sseEndpoint = sseEndpoint;
        this.server = server;
    }

    public CriMcpHttpServletSseServerTransportProvider(ObjectMapper objectMapper, CriMcpServer server) {
        this(objectMapper, server, DEFAULT_SSE_ENDPOINT);
    }

    @Override
    public void setSessionFactory(McpServerSession.Factory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Mono<Void> notifyClients(String method, Map<String, Object> params) {
        if (sessions.isEmpty()) {
            logger.debug("No active sessions to broadcast message to");
            return Mono.empty();
        }

        logger.debug("Attempting to broadcast message to {} active sessions", sessions.size());

        return Flux.fromIterable(sessions.values())
                .flatMap(session -> session.sendNotification(method, params)
                        .doOnError(
                                e -> logger.error("Failed to send message to session {}: {}", session.getId(), e.getMessage()))
                        .onErrorComplete())
                .then();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // TODO check if path info contains the path params
        String pathInfo = request.getPathInfo();
        if (!sseEndpoint.equals(pathInfo)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (isClosing.get()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Server is shutting down");
            return;
        }

        response.setContentType("text/event-stream");
        response.setCharacterEncoding(UTF_8);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Access-Control-Allow-Origin", "*");

        String sessionId = UUID.randomUUID().toString();
        if (request.getParameter(CriMcpCommonEvents.ENDPOINT_EVENT_TYPE) != null) {
            sessionId = CRI_SID_PREFIX + sessionId;
        }
        AsyncContext asyncContext = request.startAsync();
        asyncContext.setTimeout(0);

        PrintWriter writer = response.getWriter();

        // Create a new session transport
        HttpServletMcpSessionTransport sessionTransport = new HttpServletMcpSessionTransport(
                sessionId, asyncContext, writer);

        // Create a new session using the session factory
        McpServerSession session = sessionFactory.create(sessionTransport);
        this.sessions.put(sessionId, session);

        // Send initial endpoint event
        CriMcpServerInfo serverInfo = server.getServerInfo();
        String serverInfoJson = this.objectMapper.writeValueAsString(serverInfo);
        this.sendEvent(sessionId, writer, ENDPOINT_EVENT_TYPE, serverInfo.messageEndpoint() + "?sessionId=" + sessionId + (
                ! sessionId.startsWith(CRI_SID_PREFIX) ? ""
                        : "&serverInfo=" + Base64.getEncoder().encodeToString(serverInfoJson.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (isClosing.get()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Server is shutting down");
            return;
        }

        String pathInfo = request.getPathInfo();
        if (! this.server.getServerInfo().messageEndpoint().equals(pathInfo)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Get the session ID from the request parameter
        String sessionId = request.getParameter("sessionId");
        if (sessionId == null) {
            response.setContentType(APPLICATION_JSON);
            response.setCharacterEncoding(UTF_8);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            String jsonError = objectMapper.writeValueAsString(new McpError("Session ID missing in message endpoint"));
            PrintWriter writer = response.getWriter();
            writer.write(jsonError);
            writer.flush();
            return;
        }

        // Get the session from the sessions map
        McpServerSession session = sessions.get(sessionId);
        if (session == null) {
            response.setContentType(APPLICATION_JSON);
            response.setCharacterEncoding(UTF_8);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            String jsonError = objectMapper.writeValueAsString(new McpError("Session not found: " + sessionId));
            PrintWriter writer = response.getWriter();
            writer.write(jsonError);
            writer.flush();
            return;
        }

        try {
            BufferedReader reader = request.getReader();
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            String decodedBody = body.toString();
            if (sessionId.startsWith(CRI_SID_PREFIX)) {
                decodedBody = this.server.decodeRequestBodyToJson(decodedBody);
                sessionHeaders.put(sessionId, getRequestHeaders(request));
            }
            McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(objectMapper, decodedBody);

            // Process the message through the session's handle method
            session.handle(message).block(); // Block for Servlet compatibility

            response.setStatus(HttpServletResponse.SC_OK);
        }
        catch (Exception e) {
            logger.error("Error processing message: {}", e.getMessage());
            try {
                McpError mcpError = new McpError(e.getMessage());
                response.setContentType(APPLICATION_JSON);
                response.setCharacterEncoding(UTF_8);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                String jsonError = objectMapper.writeValueAsString(mcpError);
                PrintWriter writer = response.getWriter();
                writer.write(jsonError);
                writer.flush();
            }
            catch (IOException ex) {
                logger.error(FAILED_TO_SEND_ERROR_RESPONSE, ex.getMessage());
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing message");
            }
        }
    }

    /**
     * Initiates a graceful shutdown of the transport.
     * <p>
     * This method marks the transport as closing and closes all active client sessions.
     * New connection attempts will be rejected during shutdown.
     * @return A Mono that completes when all sessions have been closed
     */
    @Override
    public Mono<Void> closeGracefully() {
        isClosing.set(true);
        logger.debug("Initiating graceful shutdown with {} active sessions", sessions.size());

        return Flux.fromIterable(sessions.values()).flatMap(McpServerSession::closeGracefully).then();
    }

    /**
     * Sends an SSE event to a client.
     * @param sessionId The session ID of the client
     * @param writer The writer to send the event through
     * @param eventType The type of event (message or endpoint)
     * @param data The event data
     * @throws IOException If an error occurs while writing the event
     */
    private void sendEvent(String sessionId, PrintWriter writer, String eventType, String data) throws IOException {
        if (sessionId.startsWith(CRI_SID_PREFIX) && ! eventType.equals(ENDPOINT_EVENT_TYPE)) {
            data = server.encodeMessageJsonToString(sessionHeaders.computeIfAbsent(sessionId, key -> Map.of()), data);
        }
        writer.write("event: " + eventType + "\n");
        writer.write("data: " + data + "\n\n");
        writer.flush();

        if (writer.checkError()) {
            throw new IOException("Client disconnected");
        }
    }

    /**
     * Cleans up resources when the servlet is being destroyed.
     * <p>
     * This method ensures a graceful shutdown by closing all client connections before
     * calling the parent's destroy method.
     */
    @Override
    public void destroy() {
        closeGracefully().block();
        super.destroy();
    }

    /**
     * Implementation of McpServerTransport for HttpServlet SSE sessions. This class
     * handles the transport-level communication for a specific client session.
     */
    private class HttpServletMcpSessionTransport implements McpServerTransport {

        private final String sessionId;

        private final AsyncContext asyncContext;

        private final PrintWriter writer;

        HttpServletMcpSessionTransport(String sessionId, AsyncContext asyncContext, PrintWriter writer) {
            this.sessionId = sessionId;
            this.asyncContext = asyncContext;
            this.writer = writer;
            logger.debug("Session transport {} initialized with SSE writer", sessionId);
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return Mono.fromRunnable(() -> {
                try {
                    String jsonText = objectMapper.writeValueAsString(message);
                    sendEvent(sessionId, writer, MESSAGE_EVENT_TYPE, jsonText);
                    logger.debug("Message sent to session {}", sessionId);
                }
                catch (Exception e) {
                    logger.error("Failed to send message to session {}: {}", sessionId, e.getMessage());
                    sessions.remove(sessionId);
                    sessionHeaders.remove(sessionId);
                    asyncContext.complete();
                }
            });
        }

        /**
         * Converts data from one type to another using the configured ObjectMapper.
         * @param data The source data object to convert
         * @param typeRef The target type reference
         * @return The converted object of type T
         * @param <T> The target type
         */
        @Override
        public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
            return objectMapper.convertValue(data, typeRef);
        }

        /**
         * Initiates a graceful shutdown of the transport.
         * @return A Mono that completes when the shutdown is complete
         */
        @Override
        public Mono<Void> closeGracefully() {
            return Mono.fromRunnable(() -> {
                logger.debug("Closing session transport: {}", sessionId);
                try {
                    sessions.remove(sessionId);
                    sessionHeaders.remove(sessionId);
                    asyncContext.complete();
                    logger.debug("Successfully completed async context for session {}", sessionId);
                }
                catch (Exception e) {
                    logger.warn("Failed to complete async context for session {}: {}", sessionId, e.getMessage());
                }
            });
        }

        /**
         * Closes the transport immediately.
         */
        @Override
        public void close() {
            try {
                sessions.remove(sessionId);
                sessionHeaders.remove(sessionId);
                asyncContext.complete();
                logger.debug("Successfully completed async context for session {}", sessionId);
            }
            catch (Exception e) {
                logger.warn("Failed to complete async context for session {}: {}", sessionId, e.getMessage());
            }
        }

    }

    private static Map<String, String> getRequestHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return Map.copyOf(headers);
    }

}
