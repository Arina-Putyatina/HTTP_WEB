
public class Main {

    public static final int PORT = 9999;

    public static void main(String[] args) {
        final Server server = new Server();
        server.listen(PORT);
    }
}
