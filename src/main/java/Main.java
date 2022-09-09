import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

public class Main {

    public static final int PORT = 9999;

    public static void main(String[] args) {
        final Server server = new Server();

        Handler defaultHandler = (request, responseStream) -> {
            final Path filePath = Path.of(".", "public", request.getPath());

            try {
                File f = filePath.toFile();
                if(!f.exists() || f.isDirectory()) {
                    server.sendNotFound(responseStream);
                    return;
                }

                final String mimeType;

                mimeType = Files.probeContentType(filePath);
                final long length = Files.size(filePath);
                Server.sendAnswer(responseStream, mimeType, length);
                Files.copy(filePath, responseStream);
                responseStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        };

        server.setDefaultHandler(defaultHandler);

        server.addHandler("GET", "/classic.html", new Handler() {
            public void handle(Request request, BufferedOutputStream responseStream) {
                try {
                    final Path filePath = Path.of(".", "public", request.getPath());
                    final var template = Files.readString(filePath);
                    final String mimeType = Files.probeContentType(filePath);
                    final var content = template.replace(
                            "{time}",
                            LocalDateTime.now().toString()
                    ).getBytes();

                    Server.sendAnswer(responseStream, mimeType, content.length);
                    responseStream.write(content);
                    responseStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        server.listen(PORT);
    }
}
