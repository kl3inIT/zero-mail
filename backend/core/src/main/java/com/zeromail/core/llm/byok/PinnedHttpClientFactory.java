package com.zeromail.core.llm.byok;

import com.zeromail.core.llm.config.LlmProperties;
import com.zeromail.core.llm.config.LlmProperties.ByokProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PinnedHttpClientFactory {

    private final ByokProperties byokProperties;
    private final PinnedResolutionObserver pinnedResolutionObserver;

    @Autowired
    public PinnedHttpClientFactory(LlmProperties llmProperties) {
        this(llmProperties.byok(), PinnedResolutionObserver.noop());
    }

    PinnedHttpClientFactory(
            ByokProperties byokProperties, PinnedResolutionObserver pinnedResolutionObserver) {
        this.byokProperties = Objects.requireNonNull(byokProperties, "byokProperties");
        this.pinnedResolutionObserver =
                Objects.requireNonNull(pinnedResolutionObserver, "pinnedResolutionObserver");
    }

    public PinnedHttpClient clientFor(BaseUrlValidator.ValidatedTarget validatedTarget) {
        return new PinnedHttpClient(validatedTarget, byokProperties, pinnedResolutionObserver);
    }

    public static final class PinnedHttpClient {

        private final BaseUrlValidator.ValidatedTarget validatedTarget;
        private final ByokProperties byokProperties;
        private final PinnedResolutionObserver pinnedResolutionObserver;

        private PinnedHttpClient(
                BaseUrlValidator.ValidatedTarget validatedTarget,
                ByokProperties byokProperties,
                PinnedResolutionObserver pinnedResolutionObserver) {
            this.validatedTarget = Objects.requireNonNull(validatedTarget, "validatedTarget");
            this.byokProperties = Objects.requireNonNull(byokProperties, "byokProperties");
            this.pinnedResolutionObserver =
                    Objects.requireNonNull(pinnedResolutionObserver, "pinnedResolutionObserver");
        }

        public PinnedHttpResponse get(String relativePath, Map<String, String> headers)
                throws IOException {
            URI requestUri = appendPath(validatedTarget.uri(), relativePath);
            if ("http".equalsIgnoreCase(requestUri.getScheme())) {
                return getOverPlainSocket(requestUri, headers);
            }
            if ("https".equalsIgnoreCase(requestUri.getScheme())) {
                return getOverHttpsConnection(requestUri, headers);
            }
            throw new IOException("Unsupported BYOK probe scheme");
        }

        private PinnedHttpResponse getOverHttpsConnection(
                URI requestUri, Map<String, String> headers) throws IOException {
            URL requestUrl = requestUri.toURL();
            HttpsURLConnection connection = (HttpsURLConnection) requestUrl.openConnection();
            connection.setSSLSocketFactory(
                    new PinnedSSLSocketFactory(
                            (SSLSocketFactory) SSLSocketFactory.getDefault(),
                            validatedTarget.resolvedAddress(),
                            connectTimeoutMillis(),
                            pinnedResolutionObserver));
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(connectTimeoutMillis());
            connection.setReadTimeout(readTimeoutMillis());
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            int statusCode = connection.getResponseCode();
            String body = statusCode >= 200 && statusCode < 300 ? readBody(connection) : "";
            connection.disconnect();
            return new PinnedHttpResponse(statusCode, body);
        }

        private PinnedHttpResponse getOverPlainSocket(URI requestUri, Map<String, String> headers)
                throws IOException {
            String host = requestUri.getHost();
            int port = effectivePort(requestUri);
            pinnedResolutionObserver.observe(host, validatedTarget.resolvedAddress());
            try (Socket socket = new Socket()) {
                socket.connect(
                        new InetSocketAddress(validatedTarget.resolvedAddress(), port),
                        connectTimeoutMillis());
                socket.setSoTimeout(readTimeoutMillis());
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(httpRequestBytes(requestUri, headers));
                outputStream.flush();
                byte[] responseBytes = socket.getInputStream().readAllBytes();
                return parseRawHttpResponse(responseBytes);
            }
        }

        private byte[] httpRequestBytes(URI requestUri, Map<String, String> headers) {
            Map<String, String> requestHeaders = new LinkedHashMap<>();
            requestHeaders.put("Host", hostHeader(requestUri));
            requestHeaders.put("Accept", "application/json");
            requestHeaders.put("Connection", "close");
            requestHeaders.putAll(headers);
            StringBuilder requestBuilder = new StringBuilder();
            requestBuilder.append("GET ").append(pathAndQuery(requestUri)).append(" HTTP/1.1\r\n");
            for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
                requestBuilder
                        .append(header.getKey())
                        .append(": ")
                        .append(header.getValue())
                        .append("\r\n");
            }
            requestBuilder.append("\r\n");
            return requestBuilder.toString().getBytes(StandardCharsets.US_ASCII);
        }

        private int connectTimeoutMillis() {
            return Math.toIntExact(byokProperties.connectTimeout().toMillis());
        }

        private int readTimeoutMillis() {
            return Math.toIntExact(byokProperties.readTimeout().toMillis());
        }
    }

    public record PinnedHttpResponse(int statusCode, String responsePayload) {}

    @FunctionalInterface
    interface PinnedResolutionObserver {
        void observe(String host, InetAddress resolvedAddress);

        static PinnedResolutionObserver noop() {
            return (ignoredHost, ignoredAddress) -> {};
        }
    }

    private static final class PinnedSSLSocketFactory extends SSLSocketFactory {

        private final SSLSocketFactory delegate;
        private final InetAddress pinnedAddress;
        private final int connectTimeoutMillis;
        private final PinnedResolutionObserver pinnedResolutionObserver;

        private PinnedSSLSocketFactory(
                SSLSocketFactory delegate,
                InetAddress pinnedAddress,
                int connectTimeoutMillis,
                PinnedResolutionObserver pinnedResolutionObserver) {
            this.delegate = delegate;
            this.pinnedAddress = pinnedAddress;
            this.connectTimeoutMillis = connectTimeoutMillis;
            this.pinnedResolutionObserver = pinnedResolutionObserver;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            Socket rawSocket = new Socket();
            pinnedResolutionObserver.observe(host, pinnedAddress);
            rawSocket.connect(new InetSocketAddress(pinnedAddress, port), connectTimeoutMillis);
            SSLSocket sslSocket = (SSLSocket) delegate.createSocket(rawSocket, host, port, true);
            configureServerIdentity(sslSocket, host);
            return sslSocket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                throws IOException {
            Socket rawSocket = new Socket();
            rawSocket.bind(new InetSocketAddress(localHost, localPort));
            pinnedResolutionObserver.observe(host, pinnedAddress);
            rawSocket.connect(new InetSocketAddress(pinnedAddress, port), connectTimeoutMillis);
            SSLSocket sslSocket = (SSLSocket) delegate.createSocket(rawSocket, host, port, true);
            configureServerIdentity(sslSocket, host);
            return sslSocket;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return delegate.createSocket(host, port);
        }

        @Override
        public Socket createSocket(
                InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return delegate.createSocket(address, port, localAddress, localPort);
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose)
                throws IOException {
            SSLSocket sslSocket = (SSLSocket) delegate.createSocket(socket, host, port, autoClose);
            configureServerIdentity(sslSocket, host);
            return sslSocket;
        }

        private static void configureServerIdentity(SSLSocket sslSocket, String host) {
            var sslParameters = sslSocket.getSSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
            sslParameters.setServerNames(List.of(new SNIHostName(host)));
            sslSocket.setSSLParameters(sslParameters);
        }
    }

    private static URI appendPath(URI baseUri, String relativePath) {
        String base = baseUri.toString().replaceAll("/+$", "");
        String suffix = relativePath.replaceAll("^/+", "");
        return URI.create(base + "/" + suffix);
    }

    private static String readBody(HttpURLConnection connection) throws IOException {
        try (InputStream inputStream = connection.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static PinnedHttpResponse parseRawHttpResponse(byte[] responseBytes)
            throws IOException {
        String response = new String(responseBytes, StandardCharsets.ISO_8859_1);
        int firstLineEnd = response.indexOf("\r\n");
        if (firstLineEnd < 0) {
            throw new IOException("Malformed HTTP response");
        }
        String[] statusParts = response.substring(0, firstLineEnd).split(" ", 3);
        if (statusParts.length < 2) {
            throw new IOException("Malformed HTTP status line");
        }
        int statusCode = Integer.parseInt(statusParts[1]);
        int bodyStart = response.indexOf("\r\n\r\n");
        String body = "";
        if (bodyStart >= 0 && statusCode >= 200 && statusCode < 300) {
            byte[] bodyBytes = new byte[responseBytes.length - (bodyStart + 4)];
            System.arraycopy(responseBytes, bodyStart + 4, bodyBytes, 0, bodyBytes.length);
            body = new String(bodyBytes, StandardCharsets.UTF_8);
        }
        return new PinnedHttpResponse(statusCode, body);
    }

    private static String pathAndQuery(URI uri) {
        String path =
                uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    private static String hostHeader(URI uri) {
        int port = uri.getPort();
        if (port < 0 || port == 80 || port == 443) {
            return uri.getHost();
        }
        return uri.getHost() + ":" + port;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
    }
}
