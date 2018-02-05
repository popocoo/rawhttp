package com.athaydes.rawhttp.core;

/**
 * HTTP message start line.
 * <p>
 * See <a href="https://tools.ietf.org/html/rfc7230#section-3.1">Section 3.1</a> of RFC-7230.
 */
public interface StartLine {

    /**
     * @return message's HTTP version.
     * Known values are {@code HTTP/1.0}, {@code HTTP/1.1} and {@code HTTP/2}.
     */
    String getHttpVersion();
}
