package com.heftreng.app.util;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StorageHelper {

    private static final String CLOUD_NAME    = "dmmkr98us";
    private static final String UPLOAD_PRESET = "heftreng_upload";
    private static final String UPLOAD_URL    =
            "https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/image/upload";

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public interface UploadCallback {
        void onSuccess(String downloadUrl);
        void onFailure(Exception e);
    }

    public static void uploadMessageImage(Context context, Uri imageUri, UploadCallback callback) {
        upload(context, imageUri, "messages", callback);
    }

    public static void uploadProfilePhoto(Context context, String uid, Uri imageUri, UploadCallback callback) {
        upload(context, imageUri, "profiles/" + uid, callback);
    }

    private static void upload(Context context, Uri imageUri, String folder, UploadCallback callback) {
        executor.execute(() -> {
            try {
                byte[] imageBytes = readBytes(context, imageUri);
                String boundary = "----HeftBoundary" + UUID.randomUUID().toString().replace("-", "");

                URL url = new URL(UPLOAD_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                OutputStream out = conn.getOutputStream();
                writeField(out, boundary, "upload_preset", UPLOAD_PRESET);
                writeField(out, boundary, "folder", folder);
                writeBytes(out, boundary, "file", "image.jpg", imageBytes);
                out.write(("\r\n--" + boundary + "--\r\n").getBytes());
                out.flush();
                out.close();

                int status = conn.getResponseCode();
                InputStream in = (status == 200) ? conn.getInputStream() : conn.getErrorStream();
                String response = new String(readStream(in));

                if (status == 200) {
                    JSONObject json = new JSONObject(response);
                    String secureUrl = json.getString("secure_url");
                    runOnMain(callback, secureUrl, null);
                } else {
                    runOnMain(callback, null, new Exception("Cloudinary hata " + status + ": " + response));
                }

            } catch (Exception e) {
                runOnMain(callback, null, e);
            }
        });
    }

    private static byte[] readBytes(Context context, Uri uri) throws Exception {
        InputStream in = context.getContentResolver().openInputStream(uri);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) != -1) buf.write(tmp, 0, n);
        in.close();
        return buf.toByteArray();
    }

    private static byte[] readStream(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) != -1) buf.write(tmp, 0, n);
        in.close();
        return buf.toByteArray();
    }

    private static void writeField(OutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes());
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes());
        out.write((value + "\r\n").getBytes());
    }

    private static void writeBytes(OutputStream out, String boundary, String name,
                                   String filename, byte[] data) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes());
        out.write(("Content-Disposition: form-data; name=\"" + name +
                "\"; filename=\"" + filename + "\"\r\n").getBytes());
        out.write("Content-Type: image/jpeg\r\n\r\n".getBytes());
        out.write(data);
    }

    private static final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private static void runOnMain(UploadCallback cb, String url, Exception e) {
        mainHandler.post(() -> {
            if (url != null) cb.onSuccess(url);
            else cb.onFailure(e);
        });
    }
}
