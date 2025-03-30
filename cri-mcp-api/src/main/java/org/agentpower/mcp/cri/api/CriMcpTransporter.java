package org.agentpower.mcp.cri.api;

public interface CriMcpTransporter<Request, Response> {
    /**
     * transport the request from other transporter
     * @param request request defined by the actual transporter
     * @return response defined by the actual transporter
     */
    Response send(Request request);

}
