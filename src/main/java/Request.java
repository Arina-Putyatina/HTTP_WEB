import org.apache.http.NameValuePair;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

public class Request {

    private final String method;
    private final String path;
    private final List<String> headers;
    private InputStream body;
    private List<NameValuePair> queryParams;

    public Request(String method, String path, List<String> headers) {
        this.method = method;
        this.path = path;
        this.headers = headers;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public InputStream getBody() {
        return body;
    }

    public List<NameValuePair> getQueryParams() {
        return queryParams;
    }

    public String getQueryParam(String name) {

        String value = "";
        for (NameValuePair queryParam : queryParams) {
            if (queryParam.getName().equals(name)) {
                value = queryParam.getValue();
                break;
            }
        }
        return value;
    }

    public void setBody(InputStream body) {
        this.body = body;
    }

    public void setQueryParams(List<NameValuePair> queryParams) {
        this.queryParams = queryParams;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Request request = (Request) o;
        return Objects.equals(method, request.method) && Objects.equals(path, request.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, path);
    }

    @Override
    public String toString() {
        return "Request{" +
                "method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", headers='" + headers + '\'' +
                '}';
    }
}
