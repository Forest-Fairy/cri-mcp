package org.agentpower.mcp.cri.api.client.ui;

/**
 * information of the mcp-server
 */
public interface CriMcpServerInfo {
    String messageEndpoint();

    /**
     * the encryption information
     *  which the mcp-server requires to encrypt the recognition token
     */
    TokenEncInfo tokenEnc();

    /**
     * the encryption information
     *  which the mcp-server requires to decrypt the request params
     */
    ParamEncInfo paramEnc();

    /**
     * the header information
     *  that the mcp-server will use to encrypt the data
     */
    DataDecInfo dataDec();




    interface TokenEncInfo {
        String headerOfToken();
        String algorithm();
        String keyForEncode();
        String encodingParams();
    }


    interface ParamEncInfo {
        String algorithm();
        String keyForEncode();
        String encodingParams();
    }


    interface DataDecInfo {
        String headerOfAlgorithm();
        String headerOfKeyForEncode();
        String headerOfEncodingParams();
    }

}
