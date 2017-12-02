package com.athaydes.rawhttp.core

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.StringSpec
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8

fun fileFromResource(resource: String): File {
    val file = File.createTempFile("raw-http", "txt")
    file.writeBytes(SimpleHttpRequestTests::class.java.getResource(resource).readBytes())
    return file
}

class SimpleHttpRequestTests : StringSpec({

    "Should be able to parse simplest HTTP Request" {
        RawHttp().parseRequest("GET localhost:8080").eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe "HTTP/1.1" // the default
            uri shouldEqual URI.create("http://localhost:8080")
            headers shouldEqual mapOf("Host" to listOf("localhost"))
            body should notBePresent()
        }
    }

    "Should be able to parse HTTP Request with path and HTTP version" {
        RawHttp().parseRequest("GET https://localhost:8080/my/resource/234 HTTP/1.0").eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe "HTTP/1.0"
            uri shouldEqual URI.create("https://localhost:8080/my/resource/234")
            headers.keys should beEmpty()
            body should notBePresent()
        }
    }

    "Uses Host header to identify target server if missing from method line" {
        RawHttp().parseRequest("GET /hello\nHost: www.example.com").eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe "HTTP/1.1" // the default
            uri shouldEqual URI.create("http://www.example.com/hello")
            headers shouldEqual mapOf("Host" to listOf("www.example.com"))
            body should notBePresent()
        }
    }

    "Request can have a body" {
        RawHttp().parseRequest("""
            POST http://host.com/myresource/123456
            Content-Type: application/json
            Content-Length: 49
            Accept: text/html

            {
                "hello": true,
                "from": "kotlin-test"
            }
            """.trimIndent()).eagerly().run {
            method shouldBe "POST"
            startLine.httpVersion shouldBe "HTTP/1.1"
            uri shouldEqual URI.create("http://host.com/myresource/123456")
            headers shouldEqual mapOf(
                    "Host" to listOf("host.com"),
                    "Content-Type" to listOf("application/json"),
                    "Content-Length" to listOf("49"),
                    "Accept" to listOf("text/html"))
            body should bePresent {
                it.asString(UTF_8) shouldEqual "{\n    \"hello\": true,\n    \"from\": \"kotlin-test\"\n}"
            }
        }
    }

    "Should be able to parse HTTP Request with path and HTTP version from a file" {
        val requestFile = fileFromResource("simple.request")

        RawHttp().parseRequest(requestFile).eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe "HTTP/1.1"
            uri shouldEqual URI.create("http://example.com/resources/abcde")
            headers shouldEqual mapOf(
                    "Accept" to listOf("application/json"),
                    "Host" to listOf("example.com"))
            body should notBePresent()
        }
    }

    "Should be able to parse HTTP Request with body from a file" {
        val requestFile = fileFromResource("post.request")

        RawHttp().parseRequest(requestFile).eagerly().run {
            method shouldBe "POST"
            startLine.httpVersion shouldBe "HTTP/1.1"
            uri shouldEqual URI.create("https://example.com/my-resource/SDFKJWEKLKLKWERLWKEGJGJE")
            headers shouldEqual mapOf(
                    "Accept" to listOf("text/plain", "*/*"),
                    "Content-Type" to listOf("text/encrypted"),
                    "Content-Length" to listOf("766"),
                    "User-Agent" to listOf("rawhttp"),
                    "Host" to listOf("example.com"))
            body should bePresent {
                it.asString(UTF_8) shouldEqual "BEGIN KEYBASE SALTPACK ENCRYPTED MESSAGE. " +
                        "kiOUtMhcc4NXXRb XMxIeCbf5rCmoNO Z9cuk3vFu4WUHGE FbP7OCGjWcildtW gRRS2oOGl0tDgNc " +
                        "yZBlB9lxbNQs77O RLN5mMqTNWbKrwQ mSZolwGEonepkkk seiN0mXd8vwWM9S 7ssjvDZGbGjAfdO " +
                        "AUJmEHLdsRKrmUX yGqKzFKkG9XuiX9 8odcxJUhBMuUAUT dPpaL3sntmQTWal FfD5rj2o0ysBE92 " +
                        "lQjYk9Sok2Ofjod ytMjCDOF0eowY67 TgdmD9xmjC9kt0N v3XJB8FQA6mntYY QvTGvMyEInxfyd0 " +
                        "4GnXi1PgbwwH9O4 Ntyrt73xVko2RdV 7yaEPrSxveTEQMh P5RxWbTqXsNNagf UfgvsZlpJFxKlPs " +
                        "DxovufvUTamC5G8 Hq5XtAT811RZlro rXjZmgoS2uUinRO 0BCq3LujBBrEzQS vV4ZV6DroIjJ6kz " +
                        "fm0sr8nIZ4pdUVS qNi5LhWIgGwPlg1 KKIOuv6aCFLUFtO pYzmPXilv7ntnES 88EnMhI1wPLDiih " +
                        "Cy1LQyPzT7gUM3A josP5Nne89rWCD9 QrKxhczapyUSch4 E4qqihxkujRPqEu toCyI5eKEnvVbfn " +
                        "ldCLQWSoA7RLYRZ E8x3TY7EqFJpmLP iulp9YqVZj. END KEYBASE SALTPACK ENCRYPTED MESSAGE."
            }
        }
    }

})

class SimpleHttpResponseTests : StringSpec({

    "Should be able to parse simplest HTTP Response" {
        RawHttp().parseResponse("HTTP/1.0 404 NOT FOUND").eagerly().run {
            startLine.httpVersion shouldBe "HTTP/1.0"
            startLine.statusCode shouldBe 404
            startLine.reason shouldEqual "NOT FOUND"
            headers.keys should beEmpty()
            body should bePresent { it.toString() shouldEqual "" }
        }
    }

    "Should be able to parse HTTP Response that may not have a body" {
        RawHttp().parseResponse("HTTP/1.1 100 CONTINUE").eagerly().run {
            startLine.httpVersion shouldBe "HTTP/1.1"
            startLine.statusCode shouldBe 100
            startLine.reason shouldEqual "CONTINUE"
            headers.keys should beEmpty()
            body should notBePresent()
        }
    }

    "Should be able to parse simple HTTP Response with body" {
        RawHttp().parseResponse("HTTP/1.1 200 OK\r\nServer: Apache\r\n\r\nHello World!".trimIndent()).eagerly().run {
            startLine.httpVersion shouldBe "HTTP/1.1"
            startLine.statusCode shouldBe 200
            startLine.reason shouldEqual "OK"
            headers shouldEqual mapOf("Server" to listOf("Apache"))
            body should bePresent {
                it.asString(UTF_8) shouldEqual "Hello World!"
            }
        }
    }

    "Should be able to parse longer HTTP Response with invalid line-endings" {
        RawHttp().parseResponse("""
             HTTP/1.1 200 OK
             Date: Mon, 27 Jul 2009 12:28:53 GMT
             Server: Apache
             Last-Modified: Wed, 22 Jul 2009 19:15:56 GMT
             ETag: "34aa387-d-1568eb00"
             Accept-Ranges: bytes
             Content-Length: 51
             Vary: Accept-Encoding
             Content-Type: application/json

             {
               "hello": "world",
               "number": 123
             }
        """.trimIndent()).eagerly().run {
            startLine.httpVersion shouldBe "HTTP/1.1"
            startLine.statusCode shouldBe 200
            startLine.reason shouldEqual "OK"
            headers shouldEqual mapOf(
                    "Date" to listOf("Mon, 27 Jul 2009 12:28:53 GMT"),
                    "Server" to listOf("Apache"),
                    "Last-Modified" to listOf("Wed, 22 Jul 2009 19:15:56 GMT"),
                    "ETag" to listOf("\"34aa387-d-1568eb00\""),
                    "Accept-Ranges" to listOf("bytes"),
                    "Content-Length" to listOf("51"),
                    "Vary" to listOf("Accept-Encoding"),
                    "Content-Type" to listOf("application/json")
            )
            body should bePresent {
                it.asString(UTF_8) shouldEqual "{\n  \"hello\": \"world\",\n  \"number\": 123\n}"
            }
        }
    }

    "Should be able to parse HTTP Response with body from file" {
        val responseFile = fileFromResource("simple.response")

        RawHttp().parseResponse(responseFile).eagerly().run {
            startLine.httpVersion shouldBe "HTTP/1.1"
            startLine.statusCode shouldBe 200
            startLine.reason shouldEqual "OK"
            headers shouldEqual mapOf(
                    "Server" to listOf("super-server"),
                    "Content-Type" to listOf("application/json"),
                    "Content-Length" to listOf("50"))
            body should bePresent {
                it.asString(UTF_8) shouldEqual "{\n  \"message\": \"Hello World\",\n  \"language\": \"EN\"\n}"
            }
        }
    }

    "Should ignore body of HTTP Response that may not have a body" {
        val stream = "HTTP/1.1 304 Not Modified\r\nETag: 12345\r\n\r\nBODY".byteInputStream()

        RawHttp().parseResponse(stream).eagerly().run {
            startLine.httpVersion shouldBe "HTTP/1.1"
            startLine.statusCode shouldBe 304
            startLine.reason shouldEqual "Not Modified"
            headers shouldEqual mapOf("ETag" to listOf("12345"))
            body should notBePresent()
        }

        // verify that the stream was only consumed until the empty-line after the last header
        String(stream.readBytes(4)) shouldEqual "BODY"
    }

})