package com.heftreng.app.ui.messages;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.heftreng.app.R;
import com.heftreng.app.adapter.MessageAdapter;
import com.heftreng.app.model.Message;
import com.heftreng.app.util.StorageHelper;
import com.heftreng.app.util.SupabaseClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChatFragment extends Fragment implements MessageAdapter.OnMessageActionListener {

    private String convId, otherUid, otherName, otherPhoto;
    private RecyclerView recyclerView;
    private TextInputEditText etMessage;
    private ImageButton btnSend, btnImage, btnCancelReply;
    private TextView tvToolbarName, tvReplyPreview, tvTypingStatus;
    private View layoutReplyPreview;
    private CircleImageView ivOtherAvatar;
    private MessageAdapter adapter;
    private List<Message> messages = new ArrayList<>();
    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private Message replyingTo = null;
    private ActivityResultLauncher<String> imagePickerLauncher;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        db          = FirebaseFirestore.getInstance();

        Bundle args = getArguments();
        if (args != null) {
            convId     = args.getString("convId");
            otherUid   = args.getString("otherUid");
            otherName  = args.getString("otherName");
            otherPhoto = args.getString("otherPhoto", "");
        }

        initViews(view);
        setupImagePicker();
        loadMessages();
        startRealtime();
    }

    private void initViews(View view) {
        recyclerView       = view.findViewById(R.id.recyclerMessages);
        etMessage          = view.findViewById(R.id.etMessage);
        btnSend            = view.findViewById(R.id.btnSend);
        btnImage           = view.findViewById(R.id.btnImage);
        btnCancelReply     = view.findViewById(R.id.btnCancelReply);
        tvToolbarName      = view.findViewById(R.id.tvToolbarName);
        tvReplyPreview     = view.findViewById(R.id.tvReplyPreview);
        tvTypingStatus     = view.findViewById(R.id.tvTypingStatus);
        layoutReplyPreview = view.findViewById(R.id.layoutReplyPreview);
        ivOtherAvatar      = view.findViewById(R.id.ivOtherAvatar);

        if (otherName  != null && tvToolbarName  != null)
            tvToolbarName.setText(otherName);

        // Diğer kullanıcı avatarı
        if (ivOtherAvatar != null && otherPhoto != null && !otherPhoto.isEmpty()) {
            Glide.with(this).load(otherPhoto).circleCrop()
                .placeholder(R.drawable.ic_account_circle).into(ivOtherAvatar);
        }

        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        llm.setStackFromEnd(true);
        recyclerView.setLayoutManager(llm);

        adapter = new MessageAdapter(messages,
            currentUser != null ? currentUser.getUid() : "", this);
        recyclerView.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendTextMessage());
        btnImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        if (btnCancelReply != null)
            btnCancelReply.setOnClickListener(v -> cancelReply());

        view.findViewById(R.id.btnBack).setOnClickListener(v ->
            requireActivity().onBackPressed());
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (uri != null) sendImageMessage(uri); });
    }

    private void loadMessages() {
        if (convId == null) return;
        SupabaseClient.getMessages(convId, result -> {
            if (result != null && isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    messages.clear();
                    messages.addAll(result);
                    adapter.notifyDataSetChanged();
                    scrollToBottom();
                });
            }
        });
    }

    private void startRealtime() {
        if (convId == null) return;
        long lastId = messages.isEmpty() ? 0 : messages.get(messages.size() - 1).id;
        SupabaseClient.startPolling(convId, lastId, newMessages -> {
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    messages.addAll(newMessages);
                    adapter.notifyDataSetChanged();
                    scrollToBottom();
                });
            }
        });
    }

    private void sendTextMessage() {
        String text = etMessage.getText() != null
            ? etMessage.getText().toString().trim() : "";
        if (text.isEmpty() || currentUser == null) return;

        etMessage.setText("");
        Message msg = buildMessage(text, null);
        SupabaseClient.sendMessage(msg, success -> {
            if (!success && isAdded()) {
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "Gönderilemedi", Toast.LENGTH_SHORT).show());
            }
            updateConversation(text);
        });
        cancelReply();
    }

    private void sendImageMessage(Uri uri) {
        if (currentUser == null) return;
        Toast.makeText(getContext(), "Görsel yükleniyor...", Toast.LENGTH_SHORT).show();
        StorageHelper.uploadMessageImage(requireContext(), uri, new StorageHelper.UploadCallback() {
            @Override public void onSuccess(String downloadUrl) {
                Message msg = buildMessage("", downloadUrl);
                SupabaseClient.sendMessage(msg, success -> updateConversation("📷 Fotoğraf"));
            }
            @Override public void onFailure(Exception e) {
                if (isAdded()) requireActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "Yükleme başarısız", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private Message buildMessage(String text, String imageUrl) {
        Message msg    = new Message();
        msg.convId     = convId;
        msg.fromUid    = currentUser.getUid();
        msg.toUid      = otherUid;
        msg.text       = text;
        msg.imageUrl   = imageUrl;
        if (replyingTo != null) {
            msg.replyToId   = String.valueOf(replyingTo.id);
            msg.replyToText = replyingTo.text;
            msg.replyToName = replyingTo.fromUid.equals(currentUser.getUid()) ? "Sen" : otherName;
        }
        return msg;
    }

    private void updateConversation(String lastMessage) {
        if (convId == null || currentUser == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("last_message",  lastMessage);
        data.put("last_at",       new java.util.Date().toString());
        // Supabase üzerinde conversations tablosunu güncelle (REST)
        // Firestore'da da conversations dokümanı tut (fallback)
        db.collection("conversations").document(convId).set(data, SetOptions.merge());
    }

    @Override
    public void onReply(Message message) {
        replyingTo = message;
        if (layoutReplyPreview != null) layoutReplyPreview.setVisibility(View.VISIBLE);
        String preview = message.text != null && !message.text.isEmpty()
            ? message.text : "📷 Fotoğraf";
        if (tvReplyPreview != null) tvReplyPreview.setText(preview);
        if (etMessage != null) etMessage.requestFocus();
    }

    @Override
    public void onDelete(Message message) {
        SupabaseClient.deleteMessage(message.id, success -> {
            if (success && isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    int idx = messages.indexOf(message);
                    if (idx >= 0) {
                        message.isDeleted = true;
                        adapter.notifyItemChanged(idx);
                    }
                });
            }
        });
    }

    private void cancelReply() {
        replyingTo = null;
        if (layoutReplyPreview != null) layoutReplyPreview.setVisibility(View.GONE);
        if (tvReplyPreview     != null) tvReplyPreview.setText("");
    }

    private void scrollToBottom() {
        if (!messages.isEmpty())
            recyclerView.scrollToPosition(messages.size() - 1);
    }

    @Override
    public void onDestroy() {
        SupabaseClient.stopPolling();
        super.onDestroy();
    }
}
