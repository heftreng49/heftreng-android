package com.heftreng.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.heftreng.app.R;
import com.heftreng.app.model.Message;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {

    private static final int VIEW_SENT     = 1;
    private static final int VIEW_RECEIVED = 2;

    public interface OnMessageActionListener {
        void onReply(Message message);
        void onDelete(Message message);
    }

    private final List<Message> messages;
    private final String myUid;
    private final OnMessageActionListener listener;

    // Diğer kullanıcının fotoğrafı (mesaj listesinde küçük avatar)
    private String otherPhotoUrl = "";

    public MessageAdapter(List<Message> messages, String myUid,
                          OnMessageActionListener listener) {
        this.messages = messages;
        this.myUid    = myUid;
        this.listener = listener;
    }

    public void setOtherPhotoUrl(String url) {
        this.otherPhotoUrl = url != null ? url : "";
    }

    @Override
    public int getItemViewType(int pos) {
        return messages.get(pos).isMine(myUid) ? VIEW_SENT : VIEW_RECEIVED;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == VIEW_SENT
            ? R.layout.item_message_sent
            : R.layout.item_message_received;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v, viewType == VIEW_RECEIVED);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Message msg = messages.get(pos);

        // Silindi mi?
        if (msg.isDeleted) {
            if (h.tvText != null) {
                h.tvText.setText("Bu mesaj silindi");
                h.tvText.setAlpha(0.4f);
            }
            if (h.ivImage != null) h.ivImage.setVisibility(View.GONE);
            if (h.layoutReply != null) h.layoutReply.setVisibility(View.GONE);
            return;
        }

        if (h.tvText != null) h.tvText.setAlpha(1f);

        // Metin
        boolean hasText = msg.text != null && !msg.text.isEmpty();
        if (h.tvText != null) {
            h.tvText.setVisibility(hasText ? View.VISIBLE : View.GONE);
            if (hasText) h.tvText.setText(msg.text);
        }

        // Görsel
        boolean hasImage = msg.imageUrl != null && !msg.imageUrl.isEmpty()
            && !msg.imageUrl.equals("null");
        if (h.ivImage != null) {
            h.ivImage.setVisibility(hasImage ? View.VISIBLE : View.GONE);
            if (hasImage) {
                Glide.with(h.itemView.getContext())
                    .load(msg.imageUrl)
                    .centerCrop()
                    .into(h.ivImage);
            }
        }

        // Reply önizleme
        boolean hasReply = msg.replyToText != null && !msg.replyToText.isEmpty()
            && !msg.replyToText.equals("null");
        if (h.layoutReply != null) {
            h.layoutReply.setVisibility(hasReply ? View.VISIBLE : View.GONE);
        }
        if (h.tvReply != null && hasReply) {
            String replyLabel = (msg.replyToName != null ? msg.replyToName + ": " : "")
                + msg.replyToText;
            h.tvReply.setText(replyLabel);
        }

        // Karşı taraf avatarı (alınan mesajlarda)
        if (h.ivAvatar != null && !otherPhotoUrl.isEmpty()) {
            Glide.with(h.itemView.getContext())
                .load(otherPhotoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_account_circle)
                .into(h.ivAvatar);
        }

        // Uzun basınca sil (kendi mesajı)
        if (msg.isMine(myUid)) {
            h.itemView.setOnLongClickListener(v -> {
                if (!msg.isDeleted) listener.onDelete(msg);
                return true;
            });
        }

        // Tek tıkla yanıtla
        h.itemView.setOnClickListener(v -> {
            if (!msg.isDeleted) listener.onReply(msg);
        });
    }

    @Override
    public int getItemCount() { return messages.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvText, tvReply;
        ShapeableImageView ivImage;
        CircleImageView ivAvatar;
        View layoutReply;

        VH(@NonNull View v, boolean isReceived) {
            super(v);
            tvText      = v.findViewById(R.id.tvMessageText);
            tvReply     = v.findViewById(R.id.tvReplyText);
            ivImage     = v.findViewById(R.id.ivMessageImage);
            layoutReply = v.findViewById(R.id.layoutReplyContainer);
            if (isReceived) {
                ivAvatar = v.findViewById(R.id.ivSenderAvatar);
            }
        }
    }
}
