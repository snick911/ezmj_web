import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 開發時用的靜態網站，只聽本機。正式部署不會用到它：網頁放在靜態網站，服務器不提供網頁。
 * java tools/DevServer.java [port] [資料夾]
 */
public final class DevServer {

    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "woff2", "font/woff2",
            "txt", "text/plain; charset=utf-8",
            "svg", "image/svg+xml");

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Path root = Path.of(args.length > 1 ? args[1] : "target/site").toRealPath();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/", exchange -> serve(exchange, root));
        server.start();
        System.out.println("http://localhost:" + port + "/ -> " + root);
    }

    private static void serve(HttpExchange exchange, Path root) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            Path file = root.resolve(path.substring(1) + (path.endsWith("/") ? "index.html" : "")).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String name = file.getFileName().toString();
            String type = TYPES.getOrDefault(name.substring(name.lastIndexOf('.') + 1), "application/octet-stream");
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            byte[] body = Files.readAllBytes(file);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
