package org.jackhuang.hmcl.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.util.io.HttpRequest;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class ResourcePackUpdater {
    private static final String OWNER = "SweetRiceMC";
    private static final String REPO = "ResourcePack";
    private static final String REMOTE_PATH = "/resource.zip";
    private static final String LOCAL_PATH = "/resourcepacks/服务器材质包.zip";
    private static final String LOCAL_SHA1 = "/SweetRiceMC/last_sha1";
    private static final String[] MIRRORS = {
            "https://ghproxy.homeboyc.cn/",
            "https://ghps.cc/",
            "https://gh.ddlc.top/",
            "https://ghproxy.com/",
            "https://hub.gitmirror.com/",
            "https://ghproxy.net/",
            "https://github.moeyy.cn/",
            ""
    };
    public static File getResourcePackFile(File mcDir) {
        return new File(mcDir, LOCAL_PATH);
    }
    public static File getResourcePackSHA1File(File mcDir) {
        return new File(mcDir, LOCAL_SHA1);
    }
    public static List<URL> getAllDownloadLinks() {
        String url = "https://github.com/" + OWNER + "/" + REPO + "/blob/main" + REMOTE_PATH;
        List<URL> list = new ArrayList<>();
        try {
            for (String mirror : MIRRORS) {
                list.add(new URL(mirror + url));
            }
        } catch (MalformedURLException ignored) {
        }
        return list;
    }
    public static String getSHA1FromApi() throws IOException {
        HttpRequest.HttpGetRequest httpGet = HttpRequest.HttpGetRequest.GET("https://api.github.com/repos/" + OWNER + "/" + REPO + "/commits?path="
                + URLEncoder.encode(REMOTE_PATH, "utf-8") + "&per_page=1&page=1");
        httpGet.header("content-type", "application/json");
        String result = httpGet.getString();

        JsonElement element = JsonParser.parseString(result);
        JsonArray array = element.isJsonArray() ? element.getAsJsonArray() : null;
        if (array == null || array.size() == 0) {
            return null;
        }
        JsonObject obj = array.get(0).getAsJsonObject();
        return obj.get("sha").getAsString();
    }
}