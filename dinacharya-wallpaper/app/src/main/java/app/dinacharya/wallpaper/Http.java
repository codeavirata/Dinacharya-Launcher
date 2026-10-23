package app.dinacharya.wallpaper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** The one network call the app makes: a plain HTTPS request. Kept behind an interface so it can be tested. */
public interface Http {
    final class Resp {
        public final int code;
        public final byte[] body;

        public Resp(int code, byte[] body) {
            this.code = code;
            this.body = body == null ? new byte[0] : body;
        }

        public String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    Resp send(String method, String url, Map<String, String> headers, byte[] body) throws IOException;

    /** The real thing, over HttpURLConnection. */
    final class Url implements Http {
        @Override
        public Resp send(String method, String url, Map<String, String> headers, byte[] body) throws IOException {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            try {
                c.setRequestMethod(method);
                c.setConnectTimeout(20000);
                c.setReadTimeout(60000);
                c.setInstanceFollowRedirects(false);
                if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
                if (body != null) {
                    c.setDoOutput(true);
                    c.setFixedLengthStreamingMode(body.length);
                    try (OutputStream os = c.getOutputStream()) {
                        os.write(body);
                    }
                }
                int code = c.getResponseCode();
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                if (in != null) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    in.close();
                }
                return new Resp(code, bo.toByteArray());
            } finally {
                c.disconnect();
            }
        }
    }
}
