package com.heftreng.app.ui.compose;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.heftreng.app.R;
import com.heftreng.app.util.StorageHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class ComposeFragment extends Fragment {

    private static final int MAX_CHARS = 500;

    private EditText etContent;
    private ShapeableImageView ivPreview;
    private View framePreview;
    private MaterialButton btnShare;
    private ImageButton btnAddImage, btnBack, btnRemoveImage;
    private TextView tvCharCount;
    private CircleImageView ivMyAvatar;
    private TextView tvMyName;

    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private Uri selectedImageUri = null;
    private ActivityResultLauncher<String> imagePickerLauncher;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_compose, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        db = FirebaseFirestore.getInstance();

        etContent     = view.findViewById(R.id.etContent);
        ivPreview     = view.findViewById(R.id.ivPreview);
        framePreview  = view.findViewById(R.id.framePreview);
        btnShare      = view.findViewById(R.id.btnShare);
        btnAddImage   = view.findViewById(R.id.btnAddImage);
        btnBack       = view.findViewById(R.id.btnBack);
        btnRemoveImage = view.findViewById(R.id.btnRemoveImage);
        tvCharCount   = view.findViewById(R.id.tvCharCount);
        ivMyAvatar    = view.findViewById(R.id.ivMyAvatar);
        tvMyName      = view.findViewById(R.id.tvMyName);

        // Kullanıcı bilgilerini doldur
        if (currentUser != null) {
            if (tvMyName != null && currentUser.getDisplayName() != null)
                tvMyName.setText(currentUser.getDisplayName());
            if (ivMyAvatar != null && currentUser.getPhotoUrl() != null) {
                Glide.with(this)
                    .load(currentUser.getPhotoUrl())
                    .circleCrop()
                    .placeholder(R.drawable.ic_account_circle)
                    .into(ivMyAvatar);
            }
        }

        // Karakter sayacı
        etContent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                int len = s.length();
                if (tvCharCount != null)
                    tvCharCount.setText(len + "/" + MAX_CHARS);
                if (tvCharCount != null)
                    tvCharCount.setTextColor(len > MAX_CHARS * 0.9
                        ? 0xFFEF4444 : 0xFF888888);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    if (framePreview != null) framePreview.setVisibility(View.VISIBLE);
                    if (ivPreview != null) ivPreview.setImageURI(uri);
                }
            }
        );

        btnAddImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        if (btnRemoveImage != null) btnRemoveImage.setOnClickListener(v -> {
            selectedImageUri = null;
            if (framePreview != null) framePreview.setVisibility(View.GONE);
        });
        btnShare.setOnClickListener(v -> sharePost());
        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
    }

    private void sharePost() {
        String content = etContent.getText().toString().trim();
        if (content.isEmpty() && selectedImageUri == null) {
            Toast.makeText(getContext(), "Bir şeyler yaz veya görsel ekle", Toast.LENGTH_SHORT).show();
            return;
        }
        if (content.length() > MAX_CHARS) {
            Toast.makeText(getContext(), "Karakter limitini aştınız", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentUser == null) return;

        btnShare.setEnabled(false);
        btnShare.setText("Paylaşılıyor...");

        if (selectedImageUri != null) {
            uploadImageAndPost(content);
        } else {
            savePost(content, null);
        }
    }

    private void uploadImageAndPost(String content) {
        StorageHelper.uploadMessageImage(requireContext(), selectedImageUri,
            new StorageHelper.UploadCallback() {
                @Override public void onSuccess(String downloadUrl) { savePost(content, downloadUrl); }
                @Override public void onFailure(Exception e) {
                    Toast.makeText(getContext(), "Resim yüklenemedi", Toast.LENGTH_SHORT).show();
                    btnShare.setEnabled(true);
                    btnShare.setText("Paylaş");
                }
            });
    }

    private void savePost(String content, @Nullable String imageUrl) {
        Map<String, Object> post = new HashMap<>();
        post.put("uid",      currentUser.getUid());
        post.put("name",     currentUser.getDisplayName() != null
                             ? currentUser.getDisplayName() : "Kullanıcı");
        post.put("photoURL", currentUser.getPhotoUrl() != null
                             ? currentUser.getPhotoUrl().toString() : "");
        post.put("text",     content);
        post.put("imgUrl",   imageUrl);
        post.put("likes",    0);
        post.put("cmtCount", 0);
        post.put("likedBy",  new ArrayList<>());
        post.put("ts",       Timestamp.now());
        post.put("type",     "post");

        db.collection("feed").add(post)
            .addOnSuccessListener(ref -> {
                db.collection("users").document(currentUser.getUid())
                    .update("postCount",
                        com.google.firebase.firestore.FieldValue.increment(1));
                Toast.makeText(getContext(), "Paylaşıldı!", Toast.LENGTH_SHORT).show();
                requireActivity().onBackPressed();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(getContext(), "Paylaşılamadı", Toast.LENGTH_SHORT).show();
                btnShare.setEnabled(true);
                btnShare.setText("Paylaş");
            });
    }
}
