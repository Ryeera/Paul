package de.Ryeera.PaulBot;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class HTTPSUtils {

    private static final String METHOD_POST = "POST";
    private static final String METHOD_GET = "GET";
    private final URL url;
    private final String method;
    private final Map<String, String> args;
    private int responseCode = -1;
    private byte[] content;

    public HTTPSUtils(URL url) throws IOException {
        this(url, METHOD_GET, null);
    }

    public HTTPSUtils(URL url,
                      String method,
                      Map<String, String> args) throws IOException {
        this.url = url;
        this.method = method;
        this.args = args;
    }

    private void getContent() throws IOException {
        URL url = this.url;
        StringBuilder rawArgs = new StringBuilder();
        if (args != null) {
            for (String key : args.keySet())
                rawArgs.append(key)
						.append("=")
						.append(URLEncoder.encode(args.get(key), StandardCharsets.UTF_8))
						.append("&");
            rawArgs = new StringBuilder(rawArgs.substring(0, rawArgs.length() - 1));
        }

        if (method.equals(METHOD_GET) && args != null) {
            url = new URL(this.url.toString() + "?" + rawArgs);
        }

        int connectErrors = 0;
        while (connectErrors < 3) {
            try {
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setRequestMethod(method);

                if (method.equals(METHOD_POST) && args != null) {
                    OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
                    writer.write(rawArgs.toString());
                    writer.close();
                } else {
                    conn.connect();
                }

                if (conn.getResponseCode() >= HttpsURLConnection.HTTP_BAD_REQUEST) {
                    content = IOUtils.toByteArray(conn.getErrorStream());
                } else {
                    content = IOUtils.toByteArray(conn.getInputStream());
                }

                responseCode = conn.getResponseCode();
                conn.disconnect();
                return;
            } catch (Exception e) {
				connectErrors++;
            }
        }
    }

    public JSONArray getJSONArray() throws IOException {
        if (responseCode == -1) getContent();
        return new JSONArray(new String(content));
    }
}