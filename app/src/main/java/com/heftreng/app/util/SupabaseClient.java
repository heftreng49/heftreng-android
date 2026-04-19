package com.heftreng.app.util;

import android.util.Log;

import com.heftreng.app.model.Message;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SupabaseClient {

    // TODO: kendi değerlerinle değiştir
    public static final String SUPABASE_URL = "https://amjenakdiqgrcmlmeqeo.supabase.co";
    public static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFtamVuYWtkaXFncmNtbG1lcWVvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQ3MTIwMDksImV4cCI6MjA5MDI4ODAwOX0.a8wzzfDo8wjuWa_CzhRbkVlB0t-rAi-QB820rhN4oPc";

    private static final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> pollingTask;

    public interface Callback<T> { void onResult(T result); }

    // ── Realtime polling ──────────────────────────────────────────────────

    public static void startPolling(String convId, long lastId, Callback<List<Message>> callback) {
        stopPolling();
        final long[] since = {lastId};
        pollingTask = scheduler.scheduleWithFixedDelay(() -> {
            try {
                String endpoint = SUPABASE_URL
                    + "/rest/v1/messages?conv_id=eq." + convId
                    + "&id=gt." + since[0]
                    + "&is_deleted=eq.false&order=ts.asc";
                HttpURLConnection conn = openConn(endpoint, "GET");
                if (conn.getResponseCode() == 200) {
                    String body = readStream(conn);
                    JSONArray arr = new JSONArray(body);
                    if (arr.length() > 0) {
                        List<Message> list = parseMessages(arr);
                        since[0] = list.get(list.size() - 1).id;
                        callback.onResult(list);
                    }
                }
            } catch (Exception e) {
                Log.e("Supabase", "polling error", e);
            }
        }, 2, 3, TimeUnit.SECONDS);
    }

    public static void stopPolling() {
        if (pollingTask != null && !pollingTask.isCancelled()) {
            pollingTask.cancel(false);
        }
    }

    // ── Mesajları getir ───────────────────────────────────────────────────

    public static void getMessages(String convId, Callback<List<Message>> callback) {
        scheduler.execute(() -> {
            try {
                String endpoint = SUPABASE_URL
                    + "/rest/v1/messages?conv_id=eq." + convId
                    + "&is_deleted=eq.false&order=ts.asc";
                HttpURLConnection conn = openConn(endpoint, "GET");
                if (conn.getResponseCode() == 200) {
                    List<Message> list = parseMessages(new JSONArray(readStream(conn)));
                    callback.onResult(list);
                } else {
                    callback.onResult(null);
                }
            } catch (Exception e) {
                Log.e("Supabase", "getMessages error", e);
                callback.onResult(null);
            }
        });
    }

    // ── Mesaj gönder ──────────────────────────────────────────────────────

    public static void sendMessage(Message msg, Callback<Boolean> callback) {
        scheduler.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("conv_id",       msg.convId);
                body.put("from_uid",      msg.fromUid);
                body.put("to_uid",        msg.toUid);
                body.put("text",          msg.text != null ? msg.text : "");
                if (msg.imageUrl != null)
                    body.put("image_url", msg.imageUrl);
                if (msg.replyToId != null)
                    body.put("reply_to_id", msg.replyToId);
                if (msg.replyToText != null)
                    body.put("reply_to_text", msg.replyToText);
                if (msg.replyToName != null)
                    body.put("reply_to_name", msg.replyToName);

                HttpURLConnection conn = openConn(SUPABASE_URL + "/rest/v1/messages", "POST");
                conn.setRequestProperty("Prefer", "return=minimal");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                callback.onResult(code == 201 || code == 200);
            } catch (Exception e) {
                Log.e("Supabase", "sendMessage error", e);
                callback.onResult(false);
            }
        });
    }

    // ── Mesaj sil (soft delete) ───────────────────────────────────────────

    public static void deleteMessage(long msgId, Callback<Boolean> callback) {
        scheduler.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("is_deleted", true);
                HttpURLConnection conn = openConn(
                    SUPABASE_URL + "/rest/v1/messages?id=eq." + msgId, "PATCH");
                conn.setRequestProperty("Prefer", "return=minimal");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                callback.onResult(conn.getResponseCode() == 204);
            } catch (Exception e) {
                Log.e("Supabase", "deleteMessage error", e);
                callback.onResult(false);
            }
        });
    }

    // ── Resim URL kaydet ──────────────────────────────────────────────────

    public static void uploadImageUrl(long msgId, String imageUrl, Callback<Boolean> callback) {
        scheduler.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("image_url", imageUrl);
                HttpURLConnection conn = openConn(
                    SUPABASE_URL + "/rest/v1/messages?id=eq." + msgId, "PATCH");
                conn.setRequestProperty("Prefer", "return=minimal");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                callback.onResult(conn.getResponseCode() == 204);
            } catch (Exception e) {
                Log.e("Supabase", "uploadImageUrl error", e);
                callback.onResult(false);
            }
        });
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────

    private static List<Message> parseMessages(JSONArray arr) throws Exception {
        List<Message> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Message m = new Message();
            m.id          = o.optLong("id");
            m.convId      = o.optString("conv_id");
            m.fromUid     = o.optString("from_uid");
            m.toUid       = o.optString("to_uid");
            m.text        = o.optString("text");
            m.imageUrl    = o.optString("image_url");
            m.ts          = o.optString("ts");
            m.replyToId   = o.optString("reply_to_id");
            m.replyToText = o.optString("reply_to_text");
            m.replyToName = o.optString("reply_to_name");
            m.isDeleted   = o.optBoolean("is_deleted");
            list.add(m);
        }
        return list;
    }

    private static HttpURLConnection openConn(String endpoint, String method) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("apikey", SUPABASE_ANON_KEY);
        conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_ANON_KEY);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput("POST".equals(method) || "PATCH".equals(method));
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        return conn;
    }

    private static String readStream(HttpURLConnection conn) throws Exception {
        Scanner s = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}
