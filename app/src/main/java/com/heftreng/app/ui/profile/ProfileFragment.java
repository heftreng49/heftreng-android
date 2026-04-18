package com.heftreng.app.ui.profile;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.heftreng.app.R;
import com.heftreng.app.model.HeftUser;
import com.heftreng.app.util.StorageHelper;

import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleSignInClient;

    private ImageView ivAvatar;
    private TextView tvName, tvUsername, tvBio, tvPosts, tvFollowers, tvFollowing;
    private MaterialButton btnSignIn, btnFollow;
    private Button btnSignOut, btnChangePhoto;
    private View layoutLoggedIn, layoutGuest;

    private String viewingUid; // null = kendi profili, dolu = başkasının profili
    private boolean isOwnProfile = true;
    private boolean isFollowing = false;

    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private ActivityResultLauncher<String> photoPickerLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Başkasının profili mi?
        Bundle args = getArguments();
        if (args != null && args.containsKey("uid")) {
            viewingUid = args.getString("uid");
            isOwnProfile = viewingUid != null
                && mAuth.getCurrentUser() != null
                && viewingUid.equals(mAuth.getCurrentUser().getUid());
        } else {
            isOwnProfile = true;
            viewingUid = mAuth.getCurrentUser() != null
                ? mAuth.getCurrentUser().getUid() : null;
        }

        initViews(view);
        setupGoogleSignIn();
        setupLaunchers();

        if (mAuth.getCurrentUser() != null && viewingUid != null) {
            loadProfile(viewingUid);
        } else if (mAuth.getCurrentUser() == null) {
            showGuest();
        }
    }

    private void initViews(View view) {
        ivAvatar       = view.findViewById(R.id.ivAvatar);
        tvName         = view.findViewById(R.id.tvName);
        tvUsername     = view.findViewById(R.id.tvUsername);
        tvBio          = view.findViewById(R.id.tvBio);
        tvPosts        = view.findViewById(R.id.tvPosts);
        tvFollowers    = view.findViewById(R.id.tvFollowers);
        tvFollowing    = view.findViewById(R.id.tvFollowing);
        btnSignIn      = view.findViewById(R.id.btnGoogleSignIn);
        btnSignOut     = view.findViewById(R.id.btnSignOut);
        btnFollow      = view.findViewById(R.id.btnFollow);
        btnChangePhoto = view.findViewById(R.id.btnChangePhoto);
        layoutLoggedIn = view.findViewById(R.id.layoutLoggedIn);
        layoutGuest    = view.findViewById(R.id.layoutGuest);

        btnSignIn.setOnClickListener(v -> startGoogleSignIn());
        btnSignOut.setOnClickListener(v -> signOut());
        btnFollow.setOnClickListener(v -> toggleFollow());
        btnChangePhoto.setOnClickListener(v -> photoPickerLauncher.launch("image/*"));

        // Kendi profili değilse fotoğraf değiştirme butonunu gizle
        if (!isOwnProfile) {
            if (btnChangePhoto != null) btnChangePhoto.setVisibility(View.GONE);
            if (btnSignOut != null) btnSignOut.setVisibility(View.GONE);
        } else {
            if (btnFollow != null) btnFollow.setVisibility(View.GONE);
        }
    }

    private void loadProfile(String uid) {
        layoutGuest.setVisibility(View.GONE);
        layoutLoggedIn.setVisibility(View.VISIBLE);

        db.collection("users").document(uid).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    HeftUser user = doc.toObject(HeftUser.class);
                    if (user != null) {
                        tvName.setText(user.displayName != null ? user.displayName : "Kullanıcı");
                        if (user.username != null)
                            tvUsername.setText("@" + user.username);
                        if (user.bio != null)
                            tvBio.setText(user.bio);
                        tvPosts.setText(String.valueOf(user.postCount));
                        tvFollowers.setText(String.valueOf(user.followerCount));
                        tvFollowing.setText(String.valueOf(user.followingCount));

                        if (user.photoUrl != null && !user.photoUrl.isEmpty()) {
                            Glide.with(this).load(user.photoUrl).circleCrop()
                                .placeholder(R.drawable.ic_account_circle).into(ivAvatar);
                        }
                    }
                }
                // Takip durumunu kontrol et
                if (!isOwnProfile && mAuth.getCurrentUser() != null) {
                    checkFollowing(uid);
                }
            });
    }

    // ── Takip sistemi ─────────────────────────────────────────────────────

    private void checkFollowing(String targetUid) {
        String myUid = mAuth.getCurrentUser().getUid();
        db.collection("follows")
            .document(myUid + "_" + targetUid)
            .get()
            .addOnSuccessListener(doc -> {
                isFollowing = doc.exists();
                updateFollowButton();
            });
    }

    private void toggleFollow() {
        if (mAuth.getCurrentUser() == null || viewingUid == null) return;
        String myUid = mAuth.getCurrentUser().getUid();
        String docId = myUid + "_" + viewingUid;

        if (isFollowing) {
            // Takibi bırak
            db.collection("follows").document(docId).delete()
                .addOnSuccessListener(v -> {
                    isFollowing = false;
                    updateFollowButton();
                    // Sayaçları güncelle
                    db.collection("users").document(myUid)
                        .update("followingCount", FieldValue.increment(-1));
                    db.collection("users").document(viewingUid)
                        .update("followerCount", FieldValue.increment(-1));
                });
        } else {
            // Takip et
            Map<String, Object> follow = new HashMap<>();
            follow.put("followerId", myUid);
            follow.put("followingId", viewingUid);
            follow.put("createdAt", com.google.firebase.Timestamp.now());

            db.collection("follows").document(docId).set(follow)
                .addOnSuccessListener(v -> {
                    isFollowing = true;
                    updateFollowButton();
                    // Sayaçları güncelle
                    db.collection("users").document(myUid)
                        .update("followingCount", FieldValue.increment(1));
                    db.collection("users").document(viewingUid)
                        .update("followerCount", FieldValue.increment(1));
                });
        }
    }

    private void updateFollowButton() {
        if (btnFollow == null) return;
        btnFollow.setVisibility(View.VISIBLE);
        if (isFollowing) {
            btnFollow.setText("Takibi Bırak");
            btnFollow.setStrokeColorResource(R.color.brand_primary);
        } else {
            btnFollow.setText("Takip Et");
        }
    }

    // ── Profil fotoğrafı ──────────────────────────────────────────────────

    private void changeProfilePhoto(Uri uri) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        Toast.makeText(getContext(), "Fotoğraf yükleniyor...", Toast.LENGTH_SHORT).show();
        StorageHelper.uploadProfilePhoto(requireContext(), user.getUid(), uri, new StorageHelper.UploadCallback() {
            @Override
            public void onSuccess(String downloadUrl) {
                // Firestore güncelle
                db.collection("users").document(user.getUid())
                    .update("photoUrl", downloadUrl)
                    .addOnSuccessListener(v -> {
                        Glide.with(ProfileFragment.this)
                            .load(downloadUrl).circleCrop()
                            .into(ivAvatar);
                        Toast.makeText(getContext(), "Fotoğraf güncellendi", Toast.LENGTH_SHORT).show();
                    });
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(getContext(), "Yükleme başarısız", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Google Sign-In ────────────────────────────────────────────────────

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions
            .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail().requestProfile().build();
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);
    }

    private void setupLaunchers() {
        googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Task<GoogleSignInAccount> task =
                        GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        firebaseAuth(account.getIdToken());
                    } catch (ApiException e) {
                        Toast.makeText(getContext(), "Giriş başarısız", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        );

        photoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (uri != null) changeProfilePhoto(uri); }
        );
    }

    private void startGoogleSignIn() {
        googleSignInClient.signOut().addOnCompleteListener(t ->
            googleSignInLauncher.launch(googleSignInClient.getSignInIntent()));
    }

    private void firebaseAuth(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
            .addOnSuccessListener(result -> {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    viewingUid = user.getUid();
                    isOwnProfile = true;
                    loadProfile(user.getUid());
                }
            })
            .addOnFailureListener(e ->
                Toast.makeText(getContext(), "Hata: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void showGuest() {
        layoutLoggedIn.setVisibility(View.GONE);
        layoutGuest.setVisibility(View.VISIBLE);
    }

    private void signOut() {
        mAuth.signOut();
        googleSignInClient.signOut().addOnCompleteListener(t -> showGuest());
    }
}
