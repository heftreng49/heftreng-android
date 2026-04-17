package com.heftreng.app.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.messaging.FirebaseMessaging;
import com.heftreng.app.R;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "HeftMain";

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    // Layout views
    private LinearLayout layoutSignIn;
    private LinearLayout layoutHome;
    private TextView tvUserName;
    private TextView tvUserEmail;
    private TextView tvFcmToken;
    private ImageView ivUserPhoto;

    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private ActivityResultLauncher<String> notifPermLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupFirebase();
        setupGoogleSignIn();
        setupActivityResultLaunchers();
        requestNotificationPermission();

        // Giriş yapmışsa profili göster, yapmamışsa misafir olarak aç
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            showHomeScreen(currentUser);
        } else {
            showHomeScreenGuest();
        }
    }

    // ── View Başlatma ─────────────────────────────────────────────────────

    private void initViews() {
        layoutSignIn = findViewById(R.id.layoutSignIn);
        layoutHome   = findViewById(R.id.layoutHome);
        tvUserName   = findViewById(R.id.tvUserName);
        tvUserEmail  = findViewById(R.id.tvUserEmail);
        tvFcmToken   = findViewById(R.id.tvFcmToken);
        ivUserPhoto  = findViewById(R.id.ivUserPhoto);

        MaterialButton btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        btnGoogleSignIn.setOnClickListener(v -> triggerNativeGoogleSignIn());

        Button btnSignOut = findViewById(R.id.btnSignOut);
        btnSignOut.setOnClickListener(v -> signOut());
    }

    // ── Firebase & Google Kurulum ─────────────────────────────────────────

    private void setupFirebase() {
        mAuth = FirebaseAuth.getInstance();
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestProfile()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void setupActivityResultLaunchers() {
        googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Task<GoogleSignInAccount> task =
                        GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    handleGoogleSignInResult(task);
                } else {
                    Log.w(TAG, "Google Sign-In iptal, kod: " + result.getResultCode());
                    Toast.makeText(this, "Giriş iptal edildi", Toast.LENGTH_SHORT).show();
                }
            }
        );

        notifPermLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) {
                    loadFcmToken();
                }
            }
        );
    }

    // ── Google Sign-In Akışı ──────────────────────────────────────────────

    private void triggerNativeGoogleSignIn() {
        // Önceki oturumu temizle — eski token takılı kalmasın
        mGoogleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            Log.d(TAG, "Google Sign-In başarılı: " + account.getEmail());
            firebaseAuthWithGoogle(account.getIdToken());
        } catch (ApiException e) {
            Log.w(TAG, "Google Sign-In başarısız, kod=" + e.getStatusCode(), e);
            Toast.makeText(this, "Giriş başarısız: " + e.getStatusCode(),
                Toast.LENGTH_SHORT).show();
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    Log.d(TAG, "Firebase Auth başarılı: " +
                        (user != null ? user.getEmail() : "?"));
                    if (user != null) showHomeScreen(user);
                } else {
                    Log.w(TAG, "Firebase Auth başarısız", task.getException());
                    String err = task.getException() != null
                        ? task.getException().getMessage() : "Bilinmeyen hata";
                    Toast.makeText(this, "Firebase hatası: " + err,
                        Toast.LENGTH_LONG).show();
                }
            });
    }

    // ── Sign-Out ──────────────────────────────────────────────────────────

    private void signOut() {
        mAuth.signOut();
        mGoogleSignInClient.signOut().addOnCompleteListener(task -> {
            showHomeScreenGuest();
            Toast.makeText(this, "Çıkış yapıldı", Toast.LENGTH_SHORT).show();
        });
    }

    // ── FCM Token ─────────────────────────────────────────────────────────

    private void loadFcmToken() {
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "FCM token alınamadı", task.getException());
                    return;
                }
                String token = task.getResult();
                Log.d(TAG, "FCM Token: " + token);
                runOnUiThread(() -> {
                    String preview = token.length() > 40
                        ? token.substring(0, 40) + "…" : token;
                    tvFcmToken.setText("FCM: " + preview);
                });
            });
    }

    // ── Bildirim İzni ─────────────────────────────────────────────────────

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                loadFcmToken();
            }
        } else {
            loadFcmToken();
        }
    }

    // ── Ekran Geçişleri ───────────────────────────────────────────────────

    private void showSignInScreen() {
        layoutSignIn.setVisibility(View.VISIBLE);
        layoutHome.setVisibility(View.GONE);
    }

    private void showHomeScreenGuest() {
        layoutSignIn.setVisibility(View.GONE);
        layoutHome.setVisibility(View.VISIBLE);
        tvUserName.setText("Misafir");
        tvUserEmail.setText("");
        loadFcmToken();
    }

    private void showHomeScreen(FirebaseUser user) {
        layoutSignIn.setVisibility(View.GONE);
        layoutHome.setVisibility(View.VISIBLE);

        String name  = user.getDisplayName() != null ? user.getDisplayName() : "Kullanıcı";
        String email = user.getEmail() != null ? user.getEmail() : "";

        tvUserName.setText(name);
        tvUserEmail.setText(email);

        // Profil fotoğrafı yükle (Glide ile)
        if (user.getPhotoUrl() != null) {
            com.bumptech.glide.Glide.with(this)
                .load(user.getPhotoUrl())
                .circleCrop()
                .placeholder(R.drawable.ic_account_circle)
                .into(ivUserPhoto);
        }

        loadFcmToken();
    }

    // ── Bildirime Tıklanınca ──────────────────────────────────────────────

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNotificationIntent(intent);
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent == null) return;
        String type   = intent.getStringExtra("type");
        String postId = intent.getStringExtra("postId");
        String msg    = intent.getStringExtra("body");

        if (type != null || postId != null) {
            String info = "Bildirim: " + (msg != null ? msg : type);
            Toast.makeText(this, info, Toast.LENGTH_LONG).show();
        }
    }
    }
                
