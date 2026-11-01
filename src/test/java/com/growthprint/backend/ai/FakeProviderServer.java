package com.growthprint.backend.ai;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local stand-in for an AI provider (JDK built-in HTTP server, no extra dependencies).
 * Records every request and replies with scripted responses, so adapter tests never call a real,
 * paid API and never need a real key.
 */
final class FakeProviderServer implements AutoCloseable {

    record RecordedRequest(String method, String path, String query, Map<String, List<String>> headers, String body) {
        String header(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().get(0))
                    .findFirst().orElse(null);
        }
    }

    record Reply(int status, String body, long delayMillis) {
        static Reply ok(String body) {
            return new Reply(200, body, 0);
        }

        static Reply status(int status, String body) {
            return new Reply(status, body, 0);
        }

        static Reply slow(long delayMillis) {
            return new Reply(200, "{}", delayMillis);
        }
    }

    private final HttpServer server;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final List<Reply> replies = new ArrayList<>();
    private final AtomicInteger next = new AtomicInteger();

    FakeProviderServer(Reply... scripted) throws IOException {
        replies.addAll(List.of(scripted));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getQuery(), Map.copyOf(exchange.getRequestHeaders()), body));
            Reply reply = replies.get(Math.min(next.getAndIncrement(), replies.size() - 1));
            if (reply.delayMillis() > 0) {
                try {
                    Thread.sleep(reply.delayMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(reply.status(), bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (IOException ignored) {
                // client gave up (timeout test)
            }
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<RecordedRequest> requests() {
        return requests;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
