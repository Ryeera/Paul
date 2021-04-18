package de.Ryeera.PaulBot;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class HTTPSUtils{

	private static final String			METHOD_POST		= "POST", METHOD_GET = "GET";
	private final URL					url;
	private final String				method;
	private final Map<String, String>	args;
	private int							responseCode	= -1;
	private byte[]						content;

	public HTTPSUtils(URL url) throws IOException{
		this(url, METHOD_GET, null);
	}

	public HTTPSUtils(URL url, String method, Map<String, String> args) throws IOException{
		this.url = url;
		this.method = method;
		this.args = args;
	}

	private void getContent() throws IOException{
		URL url = this.url;
		String rawArgs = "";
		if(args != null){
			for(String key : args.keySet())
				rawArgs += (key + "=" + URLEncoder.encode(args.get(key), "UTF-8") + "&");
			rawArgs = rawArgs.substring(0, rawArgs.length() - 1);
		}
		if(method == METHOD_GET && args != null) url = new URL(this.url.toString() + "?" + rawArgs);
		int connectErrors = 0;
		while(connectErrors < 3){
			try{
				HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
				conn.setConnectTimeout(5000);
				conn.setDoInput(true);
				conn.setDoOutput(true);
				conn.setUseCaches(false);
				conn.setRequestMethod(method);
				if(method == METHOD_POST && args != null){
					OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
					writer.write(rawArgs);
					writer.close();
				}else conn.connect();
				if(conn.getResponseCode() >= HttpsURLConnection.HTTP_BAD_REQUEST) content = IOUtils.toByteArray(conn.getErrorStream());
				else content = IOUtils.toByteArray(conn.getInputStream());
				responseCode = conn.getResponseCode();
				conn.disconnect();
				return;
			}catch(Exception e){
				if(connectErrors >= 3){
					throw new IOException("Error downloading http");
				}
				connectErrors++;
			}
		}
	}

	public int getResponseCode() {
		return responseCode;
	}
	
	public JSONObject getJSONObject() throws IOException {
		if (responseCode == -1) getContent();
		return new JSONObject(new String(content));
	}
	
	public JSONArray getJSONArray() throws IOException {
		if (responseCode == -1) getContent();
		return new JSONArray(new String(content));
	}
}