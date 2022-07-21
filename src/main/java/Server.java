import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    public static final int POOL_SIZE = 64;
    public static final int REQUEST_LINE_COUNT = 3;
    public List<String> validPaths = new ArrayList<>();
    private final Map<String, Map<String, Handler>> mapHandlers = new ConcurrentHashMap<>();

    public Server() {
        fillValidPaths();
    }

    public void fillValidPaths() {
        File f = new File("public");
        for (File s : f.listFiles()) {
            if (s.isFile()) {
                validPaths.add("/" + s.getName());
            }
        }
    }

    public void listen(int port) {

        ExecutorService service = Executors.newFixedThreadPool(POOL_SIZE);

        try (final var serverSocket = new ServerSocket(port)) {
            while (true) {
                try {
                    final var socket = serverSocket.accept();
                    service.submit(() -> connection(socket));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        if (mapHandlers.containsKey(method)) {
            mapHandlers.get(method).put(path, handler);
        } else {
            Map<String, Handler> mapPath = new ConcurrentHashMap<>();
            mapPath.put(path, handler);
            mapHandlers.put(method, mapPath);
        }
    }

    public void runHandler(Request request, BufferedOutputStream responseStream) {

        if (mapHandlers.containsKey(request.getMethod())
                && mapHandlers.get(request.getMethod()).containsKey(request.getPath())) {
            mapHandlers.get(request.getMethod()).get(request.getPath()).handle(request, responseStream);
        } else {
            try {
                sendNotFound(responseStream);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void connection(Socket socket) {
        try (final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             final var out = new BufferedOutputStream(socket.getOutputStream());) {
            // read only request line for simplicity
            // must be in form GET /path HTTP/1.1


            final var requestLine = in.readLine();
            final var parts = requestLine.split(" ");

            if (parts.length != REQUEST_LINE_COUNT) {
                // just close socket
                return;
            }

            Request request = new Request(parts[0], parts[1]);
            request.setBody(socket.getInputStream());
            runHandler(request, out);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sendNotFound(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 404 Not Found\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    public static void sendAnswer(BufferedOutputStream out, String mimeType, long length) throws IOException {
        out.write((
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
    }
}

