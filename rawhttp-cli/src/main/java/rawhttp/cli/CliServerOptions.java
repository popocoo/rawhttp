package rawhttp.cli;

import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.server.TcpRawHttpServer;

import java.io.IOException;
import java.net.ServerSocket;

final class CliServerOptions implements TcpRawHttpServer.TcpRawHttpServerOptions {

    private final int port;
    private final RequestLogger requestLogger;

    public CliServerOptions(int port, RequestLogger requestLogger) {
        this.port = port;
        this.requestLogger = requestLogger;
    }

    @Override
    public ServerSocket getServerSocket() throws IOException {
        return new ServerSocket(port);
    }

    @Override
    public RawHttpResponse<Void> onResponse(RawHttpRequest request, RawHttpResponse<Void> response) throws IOException {
        requestLogger.logRequest(request, response);
        return TcpRawHttpServer.TcpRawHttpServerOptions.super.onResponse(request, response);
    }
}
