package org.agentpower.mcp.cri.api.client.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.function.Function;

public abstract class CriMcpClientTransportAdapter<Request extends CriMcsRequest, Response> implements CriMcpClientServer<Request, Response>, McpClientTransport {
    private static final Logger logger = LoggerFactory.getLogger(CriMcpClientTransportAdapter.class);

    @Override
    public Response send(Request criMcsRequest) {
        McpSchema.JSONRPCRequest jsonrpcRequest = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, "cri-mcp-method", criMcsRequest.requestId, criMcsRequest);
        this.sendMessage(jsonrpcRequest)
                .subscribe(
                        success -> {
                            this.pendingResponses.remove(requestId);
                            sink.success(success);
                            },
                        error -> {

                        });
        this.transport.sendMessage(jsonrpcRequest)
                // TODO: It's most efficient to create a dedicated Subscriber here
                .subscribe(v -> {
                }, error -> {
                    this.pendingResponses.remove(requestId);
                    sink.error(error);
                });
        return null;
    }

}
