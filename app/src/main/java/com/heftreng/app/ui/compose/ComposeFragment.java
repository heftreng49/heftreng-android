package com.heftreng.app.ui.compose;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.heftreng.app.util.StorageHelper;
import com.heftreng.app.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ComposeFragment extends Fragment {

    private EditText etContent;
    private ImageView ivPreview;
    private MaterialButton btnShare;
    private ImageButton btnAddImage, btnBack;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private Uri selectedImageUri = null;
    private ActivityResultLauncher<String> imagePickerLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_compose, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        db = FirebaseFirestore.getInstance();

        etContent   = view.findViewById(R.id.etContent);
        ivPreview   = view.findViewById(R.id.ivPreview);
        btnShare    = view.findViewById(R.id.btnShare);
        btnAddImage = view.findViewById(R.id.btnAddImage);
        btnBack     = view.findViewById(R.id.btnBack);

        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    ivPreview.setVisibility(View.VISIBLE);
                    ivPreview.setImageURI(uri);
                }
            }
        );

        btnAddImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        btnShare.setOnClickListener(v -> sharePost());
        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
    }

    private void sharePost() {
        String content = etContent.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(getContext(), "Bir şeyler yaz", Toast.LENGTH_SHORT).show();
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
        StorageHelper.uploadMessageImage(requireContext(), selectedImageUri, new StorageHelper.UploadCallback() {
            @Override
            public void onSuccess(String downloadUrl) {
                savePost(content, downloadUrl);
            }
            @Override
            public void onFailure(Exception e) {
                Toast.makeText(getContext(), "Resim yüklenemedi", Toast.LENGTH_SHORT).show();
                btnShare.setEnabled(true);
                btnShare.setText("Paylaş");
            }
        });
    }

    private void savePost(String content, @Nullable String imageUrl) {
        Map<String, Object> post = new HashMap<>();
        post.put("authorId",    currentUser.getUid());
        post.put("authorName",  currentUser.getDisplayName());
        post.put("authorPhoto", currentUser.getPhotoUrl() != null
            ? currentUser.getPhotoUrl().toString() : "");
        post.put("content",     content);
        post.put("imageUrl",    imageUrl);
        post.put("likeCount",   0);
        post.put("commentCount", 0);
        post.put("likedBy",     new ArrayList<>());
        post.put("createdAt",   Timestamp.now());
        post.put("type",        "post");

        db.collection("feed").add(post)
            .addOnSuccessListener(ref -> {
                // Kullanıcı post sayısını artır
                db.collection("users").document(currentUser.getUid())
                    .update("postCount", com.google.firebase.firestore.FieldValue.increment(1));
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
