import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    public static final int POOL_SIZE = 64;
    public static final int REQUEST_LINE_COUNT = 3;
    public static final String GET = "GET";
    public static final String POST = "POST";
    private Handler defaultHandler = null;
    private final Map<String, Map<String, Handler>> mapHandlers = new ConcurrentHashMap<>();
    private final int limit = 4096;

    public void setDefaultHandler(Handler defaultHandler) {
        this.defaultHandler = defaultHandler;
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
            if (defaultHandler != null) {
                defaultHandler.handle(request, responseStream);
            } else {
                try {
                    sendNotFound(responseStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void connection(Socket socket) {
        try (final var in = new BufferedInputStream(socket.getInputStream());
             final var out = new BufferedOutputStream(socket.getOutputStream());) {

            in.mark(limit);
            final var buffer = new byte[limit];
            final var read = in.read(buffer);

            // ищем request line
            final var requestLineDelimiter = new byte[]{'\r', '\n'};
            final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
            if (requestLineEnd == -1) {
                sendNotFound(out);
                return;
            }

            // читаем request line
            final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
            if (requestLine.length != 3) {
                sendNotFound(out);
                return;
            }

            final var method = requestLine[0];

            final var pathEndQuery = requestLine[1];
            if (!pathEndQuery.startsWith("/")) {
                sendNotFound(out);
                return;
            }

            String[] pathEndQueryParts = pathEndQuery.split("\\?");
            String path = pathEndQueryParts[0];
            String query = pathEndQueryParts.length > 1 ? pathEndQueryParts[1] : "";

            // ищем заголовки
            final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
            final var headersStart = requestLineEnd + requestLineDelimiter.length;
            final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
            if (headersEnd == -1) {
                sendNotFound(out);
                return;
            }

            // отматываем на начало буфера
            in.reset();
            // пропускаем requestLine
            in.skip(headersStart);

            final var headersBytes = in.readNBytes(headersEnd - headersStart);
            final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));

            List<NameValuePair> requestParameters = URLEncodedUtils.parse(query, Charset.defaultCharset());

            HashSet<PostParameter> postParameters = new HashSet<>();

            if (!method.equals(GET)) {
                in.skip(headersDelimiter.length);
                // вычитываем Content-Length, чтобы прочитать body
                final var contentLength = extractHeader(headers, "Content-Length");
                if (contentLength.isPresent()) {
                    final var length = Integer.parseInt(contentLength.get());
                    final var bodyBytes = in.readNBytes(length);

                    final var body = new String(bodyBytes);
                    fillPostParameters(postParameters, body);
                }
            }
            Request request = new Request(method, path, headers);
            request.setBody(socket.getInputStream());
            request.setQueryParams(requestParameters);
            request.setPostParameters(postParameters);

            System.out.println("Параметры запроса:" + request.getQueryParams());
            System.out.println("Параметры формы (login):" + request.getPostParam("login"));
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

    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static void fillPostParameters(HashSet<PostParameter> postParameters, String body) {

        String[] bodyParts = body.split("&");
        for (String parameter : bodyParts) {
            String[] parameterParts = parameter.split("=");
            if (parameterParts.length == 2) {
                PostParameter postParameter = new PostParameter(parameterParts[0], parameterParts[1]);
                postParameters.add(postParameter);
            }
        }
    }
}

