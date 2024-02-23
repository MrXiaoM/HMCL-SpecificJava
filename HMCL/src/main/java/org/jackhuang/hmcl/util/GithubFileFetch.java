package org.jackhuang.hmcl.util;

import com.google.gson.*;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.util.io.NetworkUtils.resolveConnection;

public class GithubFileFetch {
    private static final String[] MIRRORS = {
            "https://ghproxy.net/",
            "https://ghproxy.homeboyc.cn/",
            "https://ghps.cc/",
            "https://gh.ddlc.top/",
            "https://ghproxy.com/",
            "https://hub.gitmirror.com/",
            "https://github.moeyy.cn/",
            ""
    };

    @Nullable
    public static ByteArrayOutputStream get(String owner, String repo, String branch, String filePath) {
        String url = String.format("https://github.com/%s/%s/raw/%s/%s", owner, repo, branch, filePath);
        for (String mirror : MIRRORS) {
            ByteArrayOutputStream os = get(mirror + url);
            if (os != null) return os;
        }
        return null;
    }

    @Nullable
    public static String calcHash(String filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (
                    FileInputStream in = new FileInputStream(filePath);
                    FileChannel channel = in.getChannel();
                    DigestInputStream ignored = new DigestInputStream(in, digest)) {
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                while (channel.read(buffer) != -1) {
                    buffer.flip();
                    digest.update(buffer);
                    buffer.clear();
                }
                byte[] bytes = digest.digest();
                StringBuilder result = new StringBuilder();
                for (byte b : bytes) {
                    result.append(String.format("%02x", b));
                }
                return result.toString().toLowerCase();
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static class ModInfo {
        public final String fileName;
        public final String modId;
        public final String hash;

        protected ModInfo(String fileName, String modId, String hash) {
            this.fileName = fileName;
            this.modId = modId;
            this.hash = hash;
        }

        public boolean isSameMod(LocalModFile mod) {
            return modId.equalsIgnoreCase(mod.getId()) || fileName.equalsIgnoreCase(mod.getFileName() + ".jar");
        }
    }

    @NotNull
    public static List<ModInfo> fetchUpdate() {
        List<ModInfo> list = new ArrayList<>();
        ByteArrayOutputStream stream;
        if ((stream = fetch("package.json", true)) != null) {
            String str = new String(stream.toByteArray(), StandardCharsets.UTF_8);
            if (str.contains("<!DOCTYPE html>") && str.contains("gitee")) {
                try {
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                stream = null;
            }
        }
        if (stream == null) {
            if ((stream = fetch("package.json")) == null) return list;
        }
        try (ByteArrayOutputStream os = stream) {
            String str = new String(os.toByteArray(), StandardCharsets.UTF_8);
            JsonObject jsonRoot = JsonParser.parseString(str).getAsJsonObject();

            JsonObject files = jsonRoot.get("files").getAsJsonObject();
            for (String file : files.keySet()) {
                JsonObject obj = files.get(file).getAsJsonObject();
                String modId = obj.get("modId").getAsString();
                String hash = obj.get("hash").getAsString();
                list.add(new ModInfo(file, modId, hash));
            }
        } catch (IOException | JsonParseException | NullPointerException | IllegalStateException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static ByteArrayOutputStream fetch(String filePath) {
        return fetch(filePath, false);
    }
    @Nullable
    public static ByteArrayOutputStream fetch(String filePath, boolean gitee) {
        if (gitee) return get("https://gitee.com/SweetRiceMC/UpdateSource/raw/mods/" + filePath);
        return get("SweetRiceMC", "UpdateSource", "mods", filePath);
    }

    @Nullable
    public static ByteArrayOutputStream get(String url) {
        try {
            HttpRequest.HttpGetRequest request = HttpRequest.HttpGetRequest.GET(url);
            HttpURLConnection con = request.createConnection();
            con = resolveConnection(con);
            return IOUtils.readFully(con.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
