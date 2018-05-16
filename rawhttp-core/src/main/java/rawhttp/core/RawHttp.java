package rawhttp.core;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.List;
import javax.annotation.Nullable;
import rawhttp.core.body.BodyReader;
import rawhttp.core.body.BodyType;
import rawhttp.core.body.LazyBodyReader;
import rawhttp.core.errors.InvalidHttpRequest;
import rawhttp.core.errors.InvalidHttpResponse;
import rawhttp.core.errors.InvalidMessageFrame;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The main class of the raw-http library.
 * <p>
 * Instances of this class can parse HTTP requests and responses, as well as their subsets such as
 * the start-line and headers.
 * <p>
 * To use a default instance, which is not 100% raw (it fixes up new-lines and request's Host headers, for example)
 * use the default constructor, otherwise, call {@link #RawHttp(RawHttpOptions)} with the appropriate options.
 *
 * @see RawHttpOptions
 * @see RawHttpRequest
 * @see RawHttpResponse
 * @see RawHttpHeaders
 */
public class RawHttp {

    private final RawHttpOptions options;
    private final HttpMetadataParser metadataParser;

    /**
     * Create a new instance of {@link RawHttp} using the default {@link RawHttpOptions} instance.
     */
    public RawHttp() {
        this(RawHttpOptions.defaultInstance());
    }

    /**
     * Create a configured instance of {@link RawHttp}.
     *
     * @param options configuration options
     */
    public RawHttp(RawHttpOptions options) {
        this.options = options;
        this.metadataParser = new HttpMetadataParser(options);
    }

    /**
     * @return the HTTP options used by this instance of {@link RawHttp}.
     */
    public RawHttpOptions getOptions() {
        return options;
    }

    /**
     * @return the metadata parser used by this instance of {@link RawHttp}.
     */
    public HttpMetadataParser getMetadataParser() {
        return metadataParser;
    }

    /**
     * Parses the given HTTP request.
     *
     * @param request in text form
     * @return a parsed HTTP request object
     * @throws InvalidHttpRequest if the request is invalid
     */
    public final RawHttpRequest parseRequest(String request) {
        try {
            return parseRequest(new ByteArrayInputStream(request.getBytes(UTF_8)));
        } catch (IOException e) {
            // IOException should be impossible
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses the HTTP request contained in the given file.
     *
     * @param file containing a HTTP request
     * @return a parsed HTTP request object
     * @throws InvalidHttpRequest if the request is invalid
     * @throws IOException        if a problem occurs reading the file
     */
    public final RawHttpRequest parseRequest(File file) throws IOException {
        try (FileInputStream stream = new FileInputStream(file)) {
            return parseRequest(stream).eagerly();
        }
    }

    /**
     * Parses the HTTP request produced by the given stream.
     *
     * @param inputStream producing a HTTP request
     * @return a parsed HTTP request object
     * @throws InvalidHttpRequest if the request is invalid
     * @throws IOException        if a problem occurs accessing the stream
     */
    public RawHttpRequest parseRequest(InputStream inputStream) throws IOException {
        return parseRequest(inputStream, null);
    }

    /**
     * Parses the HTTP request produced by the given stream.
     *
     * @param inputStream   producing a HTTP request
     * @param senderAddress the address of the request sender, if known
     * @return a parsed HTTP request object
     * @throws InvalidHttpRequest if the request is invalid
     * @throws IOException        if a problem occurs accessing the stream
     */
    public RawHttpRequest parseRequest(InputStream inputStream,
                                       @Nullable InetAddress senderAddress) throws IOException {
        RequestLine requestLine = metadataParser.parseRequestLine(inputStream);
        RawHttpHeaders originalHeaders = metadataParser.parseHeaders(inputStream, InvalidHttpRequest::new);
        RawHttpHeaders.Builder modifiableHeaders = RawHttpHeaders.newBuilder(originalHeaders);

        // do a little cleanup to make sure the request is actually valid
        requestLine = verifyHost(requestLine, modifiableHeaders);

        RawHttpHeaders headers = modifiableHeaders.build();

        @Nullable BodyReader bodyReader = requestHasBody(headers)
                ? createBodyReader(inputStream, requestLine, headers)
                : null;

        return new RawHttpRequest(requestLine, headers, bodyReader, senderAddress);
    }

    /**
     * Parses the given HTTP response.
     *
     * @param response in text form
     * @return a parsed HTTP response object
     * @throws InvalidHttpResponse if the response is invalid
     */
    public final RawHttpResponse<Void> parseResponse(String response) {
        try {
            return parseResponse(
                    new ByteArrayInputStream(response.getBytes(UTF_8)),
                    null);
        } catch (IOException e) {
            // IOException should be impossible
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses the HTTP response contained in the given file.
     *
     * @param file containing a HTTP response
     * @return a parsed HTTP response object
     * @throws InvalidHttpResponse if the response is invalid
     * @throws IOException         if a problem occurs reading the file
     */
    public final RawHttpResponse<Void> parseResponse(File file) throws IOException {
        try (FileInputStream stream = new FileInputStream(file)) {
            return parseResponse(stream, null).eagerly();
        }
    }

    /**
     * Parses the HTTP response produced by the given stream.
     *
     * @param inputStream producing a HTTP response
     * @return a parsed HTTP response object
     * @throws InvalidHttpResponse if the response is invalid
     * @throws IOException         if a problem occurs accessing the stream
     */
    public final RawHttpResponse<Void> parseResponse(InputStream inputStream) throws IOException {
        return parseResponse(inputStream, null);
    }

    /**
     * Parses the HTTP response produced by the given stream.
     *
     * @param inputStream producing a HTTP response
     * @param requestLine optional {@link RequestLine} of the request which results in this response.
     *                    If provided, it is taken into consideration when deciding whether the response contains
     *                    a body. See <a href="https://tools.ietf.org/html/rfc7230#section-3.3">Section 3.3</a>
     *                    of RFC-7230 for details.
     * @return a parsed HTTP response object
     * @throws InvalidHttpResponse if the response is invalid
     * @throws IOException         if a problem occurs accessing the stream
     */
    public RawHttpResponse<Void> parseResponse(InputStream inputStream,
                                               @Nullable RequestLine requestLine) throws IOException {
        StatusLine statusLine = metadataParser.parseStatusLine(inputStream);
        RawHttpHeaders headers = metadataParser.parseHeaders(inputStream, InvalidHttpResponse::new);

        @Nullable BodyReader bodyReader = responseHasBody(statusLine, requestLine)
                ? createBodyReader(inputStream, statusLine, headers)
                : null;

        return new RawHttpResponse<>(null, null, statusLine, headers, bodyReader);
    }

    private BodyReader createBodyReader(InputStream inputStream, StartLine startLine, RawHttpHeaders headers) {
        return new LazyBodyReader(getBodyType(startLine, headers), inputStream);
    }

    /**
     * Get the body type of a HTTP message with the given start-line and headers.
     * <p>
     * This method assumes the message has a body. To check if a HTTP message has a body, call
     * {@link RawHttp#requestHasBody(RawHttpHeaders)} or {@link RawHttp#responseHasBody(StatusLine, RequestLine)}.
     *
     * @param startLine HTTP message's start-line
     * @param headers   HTTP message's headers
     * @return the body type of the HTTP message
     * @throws InvalidMessageFrame if the headers are insufficient to
     *                             safely determine the body type of a message
     */
    public BodyType getBodyType(StartLine startLine, RawHttpHeaders headers) {
        List<String> encodings = headers.get("Transfer-Encoding");
        boolean isChunked = !encodings.isEmpty() &&
                encodings.get(encodings.size() - 1).equalsIgnoreCase("chunked");
        if (isChunked) {
            return new BodyType.Chunked(encodings, metadataParser);
        }
        List<String> lengthValues = headers.get("Content-Length");
        if (lengthValues.isEmpty()) {
            if (startLine instanceof StatusLine) {
                // response has no message framing information available
                return new BodyType.CloseTerminated(encodings);
            }
            // request body without framing is not allowed
            throw new InvalidMessageFrame("The length of the request body cannot be determined. " +
                    "The Content-Length header is missing and the Transfer-Encoding header does not " +
                    "indicate the message is chunked");
        }
        if (lengthValues.size() > 1) {
            throw new InvalidMessageFrame("More than one Content-Length header value is present");
        }
        long bodyLength;
        try {
            bodyLength = Long.parseLong(lengthValues.get(0));
        } catch (NumberFormatException e) {
            throw new InvalidMessageFrame("Content-Length header value is not a valid number");
        }
        return new BodyType.ContentLength(bodyLength, encodings);
    }

    /**
     * Determines whether a request with the given headers should have a body.
     *
     * @param headers HTTP request's headers
     * @return true if the headers indicate the request should have a body, false otherwise
     */
    public static boolean requestHasBody(RawHttpHeaders headers) {
        // The presence of a message body in a request is signaled by a
        // Content-Length or Transfer-Encoding header field.  Request message
        // framing is independent of method semantics, even if the method does
        // not define any use for a message body.
        return headers.contains("Content-Length") || headers.contains("Transfer-Encoding");
    }

    /**
     * Determines whether a response with the given status-line should have a body.
     * <p>
     * This method ignores the request-line of the request which produced such response. If the request
     * is known, use the {@link #responseHasBody(StatusLine, RequestLine)} method instead.
     *
     * @param statusLine status-line of response
     * @return true if such response has a body, false otherwise
     */
    public static boolean responseHasBody(StatusLine statusLine) {
        return responseHasBody(statusLine, null);
    }

    /**
     * Determines whether a response with the given status-line should have a body.
     * <p>
     * If provided, the request-line of the request which produced such response is taken into
     * consideration. See <a href="https://tools.ietf.org/html/rfc7230#section-3.3">Section 3.3</a>
     * of RFC-7230 for details.
     *
     * @param statusLine  status-line of response
     * @param requestLine request-line of request, if any
     * @return true if such response has a body, false otherwise
     */
    public static boolean responseHasBody(StatusLine statusLine,
                                          @Nullable RequestLine requestLine) {
        if (requestLine != null) {
            if (requestLine.getMethod().equalsIgnoreCase("HEAD")) {
                return false; // HEAD response must never have a body
            }
            if (requestLine.getMethod().equalsIgnoreCase("CONNECT") &&
                    startsWith(2, statusLine.getStatusCode())) {
                return false; // CONNECT successful means start tunelling
            }
        }

        int statusCode = statusLine.getStatusCode();

        // All 1xx (Informational), 204 (No Content), and 304 (Not Modified)
        // responses do not include a message body.
        boolean hasNoBody = startsWith(1, statusCode) || statusCode == 204 || statusCode == 304;

        return !hasNoBody;
    }

    private static boolean startsWith(int firstDigit, int statusCode) {
        assert 0 < firstDigit && firstDigit < 10;
        int minCode = firstDigit * 100;
        int maxCode = minCode + 99;
        return minCode <= statusCode && statusCode <= maxCode;
    }

    private RequestLine verifyHost(RequestLine requestLine, RawHttpHeaders.Builder headers) {
        List<String> hostHeaderValues = headers.get("Host");
        @Nullable String requestLineHost = requestLine.getUri().getHost();
        if (hostHeaderValues.isEmpty()) {
            if (!options.insertHostHeaderIfMissing()) {
                throw new InvalidHttpRequest("Host header is missing", 1);
            } else if (requestLineHost == null) {
                throw new InvalidHttpRequest("Host not given either in request line or Host header", 1);
            } else {
                // add the Host header to make sure the request is legal
                headers.with("Host", requestLineHost);
            }
            return requestLine;
        } else if (hostHeaderValues.size() == 1) {
            if (requestLineHost != null) {
                throw new InvalidHttpRequest("Host specified both in Host header and in request line", 1);
            }
            try {
                RequestLine newRequestLine = requestLine.withHost(hostHeaderValues.iterator().next());
                // cleanup the host header
                headers.overwrite("Host", newRequestLine.getUri().getHost());
                return newRequestLine;
            } catch (IllegalArgumentException e) {
                int lineNumber = headers.getLineNumberAt("Host", 0);
                throw new InvalidHttpRequest("Invalid host header: " + e.getMessage(), lineNumber);
            }
        } else {
            int lineNumber = headers.getLineNumberAt("Host", 1);
            throw new InvalidHttpRequest("More than one Host header specified", lineNumber);
        }
    }

}
