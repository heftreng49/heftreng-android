package com.heftreng.app.ui.profile;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

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
import com.google.firebase.firestore.SetOptions;
import com.heftreng.app.R;
import com.heftreng.app.model.HeftUser;
import com.heftreng.app.util.StorageHelper;

import java.util.HashMap;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileFragment extends Fragment {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleSignInClient;

    private CircleImageView ivAvatar;
    private TextView tvName, tvUsername, tvBio, tvPosts, tvFollowers, tvFollowing;
    private MaterialButton btnSignIn, btnFollow, btnChangePhoto, btnMessage;
    private MaterialButton btnSignOut;
    private View layoutLoggedIn, layoutGuest;

    private String viewingUid;
    private boolean isOwnProfile = true;
    private boolean isFollowing   = false;

    private ActivityResultLauncher<Intent>  googleSignInLauncher;
    private ActivityResultLauncher<String>  photoPickerLauncher;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        Bundle args = getArguments();
        if (args != null && args.containsKey("uid")) {
            viewingUid   = args.getString("uid");
            isOwnProfile = viewingUid != null && mAuth.getCurrentUser() != null
                && viewingUid.equals(mAuth.getCurrentUser().getUid());
        } else {
            isOwnProfile = true;
            viewingUid   = mAuth.getCurrentUser() != null
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
        btnMessage     = view.findViewById(R.id.btnMessage);
        layoutLoggedIn = view.findViewById(R.id.layoutLoggedIn);
        layoutGuest    = view.findViewById(R.id.layoutGuest);

        btnSignIn.setOnClickListener(v -> startGoogleSignIn());

        if (btnSignOut != null)
            btnSignOut.setOnClickListener(v -> signOut());

        if (btnFollow != null)
            btnFollow.setOnClickListener(v -> toggleFollow());

        if (btnChangePhoto != null)
            btnChangePhoto.setOnClickListener(v -> photoPickerLauncher.launch("image/*"));

        // Ayarlar butonu
        View btnSettings = view.findViewById(R.id.btnSettings);
        if (btnSettings != null)
            btnSettings.setOnClickListener(v ->
                Toast.makeText(getContext(), "Ayarlar yakında", Toast.LENGTH_SHORT).show());

        // Mesaj butonu (başkasının profilinde)
        if (btnMessage != null)
            btnMessage.setOnClickListener(v -> openMessage());

        // Kendi profili mi kontrol et
        if (!isOwnProfile) {
            if (btnChangePhoto != null) btnChangePhoto.setVisibility(View.GONE);
            if (btnSignOut    != null) btnSignOut.setVisibility(View.GONE);
            if (btnSettings   != null) btnSettings.setVisibility(View.GONE);
        } else {
            if (btnFollow  != null) btnFollow.setVisibility(View.GONE);
            if (btnMessage != null) btnMessage.setVisibility(View.GONE);
        }
    }

    private void loadProfile(String uid) {
        if (layoutGuest    != null) layoutGuest.setVisibility(View.GONE);
        if (layoutLoggedIn != null) layoutLoggedIn.setVisibility(View.VISIBLE);

        db.collection("users").document(uid).get()
            .addOnSuccessListener(doc -> {
                if (!isAdded()) return;
                if (doc.exists()) {
                    HeftUser user = doc.toObject(HeftUser.class);
                    if (user != null) {
                        if (tvName != null)
                            tvName.setText(user.name != null ? user.name : "Kullanıcı");
                        if (tvUsername != null && user.username != null && !user.username.isEmpty())
                            tvUsername.setText("@" + user.username);
                        if (tvBio != null && user.bio != null)
                            tvBio.setText(user.bio);
                        if (tvPosts     != null) tvPosts.setText(String.valueOf(user.postCount));
                        if (tvFollowers != null) tvFollowers.setText(String.valueOf(user.followerCount));
                        if (tvFollowing != null) tvFollowing.setText(String.valueOf(user.followingCount));

                        if (ivAvatar != null && user.photoURL != null && !user.photoURL.isEmpty()) {
                            Glide.with(this).load(user.photoURL).circleCrop()
                                .placeholder(R.drawable.ic_account_circle).into(ivAvatar);
                        }
                    }
                }
                if (!isOwnProfile && mAuth.getCurrentUser() != null)
                    checkFollowing(uid);
            });
    }

    // ── Mesaj aç ─────────────────────────────────────────────────────────

    private void openMessage() {
        if (mAuth.getCurrentUser() == null || viewingUid == null) return;
        String myUid = mAuth.getCurrentUser().getUid();
        // conv_id deterministik üret (aynı iki kullanıcı arasında daima aynı)
        String convId = myUid.compareTo(viewingUid) < 0
            ? myUid + "_" + viewingUid
            : viewingUid + "_" + myUid;

        Bundle args = new Bundle();
        args.putString("convId",    convId);
        args.putString("otherUid",  viewingUid);
        args.putString("otherName", tvName != null ? tvName.getText().toString() : "");
        Navigation.findNavController(requireView()).navigate(R.id.chatFragment, args);
    }

    // ── Takip sistemi ─────────────────────────────────────────────────────

    private void checkFollowing(String targetUid) {
        String myUid = mAuth.getCurrentUser().getUid();
        db.collection("follows").document(myUid + "_" + targetUid).get()
            .addOnSuccessListener(doc -> {
                if (!isAdded()) return;
                isFollowing = doc.exists();
                updateFollowButton();
            });
    }

    private void toggleFollow() {
        if (mAuth.getCurrentUser() == null || viewingUid == null) return;
        String myUid = mAuth.getCurrentUser().getUid();
        String docId = myUid + "_" + viewingUid;

        if (isFollowing) {
            db.collection("follows").document(docId).delete()
                .addOnSuccessListener(v -> {
                    isFollowing = false;
                    updateFollowButton();
                    db.collection("users").document(myUid)
                        .update("followingCount", FieldValue.increment(-1));
                    db.collection("users").document(viewingUid)
                        .update("followerCount", FieldValue.increment(-1));
                    if (tvFollowers != null) {
                        int n = Integer.parseInt(tvFollowers.getText().toString());
                        tvFollowers.setText(String.valueOf(Math.max(0, n - 1)));
                    }
                });
        } else {
            Map<String, Object> follow = new HashMap<>();
            follow.put("fromUid",   myUid);
            follow.put("targetUid", viewingUid);
            follow.put("ts",        com.google.firebase.Timestamp.now());
            db.collection("follows").document(docId).set(follow)
                .addOnSuccessListener(v -> {
                    isFollowing = true;
                    updateFollowButton();
                    db.collection("users").document(myUid)
                        .update("followingCount", FieldValue.increment(1));
                    db.collection("users").document(viewingUid)
                        .update("followerCount", FieldValue.increment(1));
                    if (tvFollowers != null) {
                        int n = Integer.parseInt(tvFollowers.getText().toString());
                        tvFollowers.setText(String.valueOf(n + 1));
                    }
                });
        }
    }

    private void updateFollowButton() {
        if (btnFollow == null) return;
        btnFollow.setVisibility(View.VISIBLE);
        if (isFollowing) {
            btnFollow.setText("Takiptesiniz");
            btnFollow.setStrokeColorResource(R.color.brand_primary);
        } else {
            btnFollow.setText("Takip Et");
        }
        if (btnMessage != null) btnMessage.setVisibility(View.VISIBLE);
    }

    // ── Profil fotoğrafı ──────────────────────────────────────────────────

    private void changeProfilePhoto(Uri uri) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        Toast.makeText(getContext(), "Fotoğraf yükleniyor...", Toast.LENGTH_SHORT).show();
        StorageHelper.uploadProfilePhoto(requireContext(), user.getUid(), uri,
            new StorageHelper.UploadCallback() {
                @Override public void onSuccess(String downloadUrl) {
                    db.collection("users").document(user.getUid())
                        .update("photoURL", downloadUrl)
                        .addOnSuccessListener(v -> {
                            if (ivAvatar != null)
                                Glide.with(ProfileFragment.this).load(downloadUrl)
                                    .circleCrop().into(ivAvatar);
                            Toast.makeText(getContext(), "Güncellendi", Toast.LENGTH_SHORT).show();
                        });
                }
                @Override public void onFailure(Exception e) {
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
            });

        photoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (uri != null) changeProfilePhoto(uri); });
    }

    private void startGoogleSignIn() {
        googleSignInClient.signOut().addOnCompleteListener(t ->
            googleSignInLauncher.launch(googleSignInClient.getSignInIntent()));
    }

    private void firebaseAuth(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
            .addOnSuccessListener(result -> {
                FirebaseUser fbUser = mAuth.getCurrentUser();
                if (fbUser == null) return;
                Map<String, Object> data = new HashMap<>();
                data.put("uid",      fbUser.getUid());
                data.put("name",     fbUser.getDisplayName() != null
                                     ? fbUser.getDisplayName() : "Kullanıcı");
                data.put("photoURL", fbUser.getPhotoUrl() != null
                                     ? fbUser.getPhotoUrl().toString() : "");
                data.put("email",    fbUser.getEmail() != null ? fbUser.getEmail() : "");
                db.collection("users").document(fbUser.getUid())
                    .set(data, SetOptions.merge());
                viewingUid   = fbUser.getUid();
                isOwnProfile = true;
                loadProfile(fbUser.getUid());
            })
            .addOnFailureListener(e ->
                Toast.makeText(getContext(), "Hata: " + e.getMessage(),
                    Toast.LENGTH_LONG).show());
    }

    private void showGuest() {
        if (layoutLoggedIn != null) layoutLoggedIn.setVisibility(View.GONE);
        if (layoutGuest    != null) layoutGuest.setVisibility(View.VISIBLE);
    }

    private void signOut() {
        mAuth.signOut();
        googleSignInClient.signOut().addOnCompleteListener(t -> showGuest());
    }
}
