package com.heftreng.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.heftreng.app.R;
import com.heftreng.app.model.Message;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {

    private static final int VIEW_SENT = 1;
    private static final int VIEW_RECEIVED = 2;

    public interface OnMessageActionListener {
        void onReply(Message message);
        void onDelete(Message message);
    }

    private final List<Message> messages;
    private final String myUid;
    private final OnMessageActionListener listener;

    public MessageAdapter(List<Message> messages, String myUid, OnMessageActionListener listener) {
        this.messages = messages;
        this.myUid = myUid;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isMine(myUid) ? VIEW_SENT : VIEW_RECEIVED;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == VIEW_SENT
            ? R.layout.item_message_sent
            : R.layout.item_message_received;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Message msg = messages.get(position);

        // Silindi mi?
        if (msg.isDeleted) {
            holder.tvText.setText("Bu mesaj silindi");
            holder.tvText.setAlpha(0.4f);
            holder.ivImage.setVisibility(View.GONE);
        } else {
            holder.tvText.setAlpha(1f);

            // Metin
            if (msg.text != null && !msg.text.isEmpty()) {
                holder.tvText.setVisibility(View.VISIBLE);
                holder.tvText.setText(msg.text);
            } else {
                holder.tvText.setVisibility(View.GONE);
            }

            // Resim
            if (msg.imageUrl != null && !msg.imageUrl.isEmpty()
                    && !msg.imageUrl.equals("null")) {
                holder.ivImage.setVisibility(View.VISIBLE);
                Glide.with(holder.ivImage.getContext())
                    .load(msg.imageUrl)
                    .placeholder(R.drawable.ic_account_circle)
                    .into(holder.ivImage);
            } else {
                holder.ivImage.setVisibility(View.GONE);
            }
        }

        // Reply
        if (holder.tvReply != null && msg.replyToText != null
                && !msg.replyToText.isEmpty() && !msg.replyToText.equals("null")) {
            holder.tvReply.setVisibility(View.VISIBLE);
            String replyName = msg.replyToName != null ? msg.replyToName : "";
            holder.tvReply.setText(replyName + ": " + msg.replyToText);
        } else if (holder.tvReply != null) {
            holder.tvReply.setVisibility(View.GONE);
        }

        // Uzun basınca sil (kendi mesajsa)
        if (msg.isMine(myUid)) {
            holder.itemView.setOnLongClickListener(v -> {
                if (!msg.isDeleted) listener.onDelete(msg);
                return true;
            });
        }

        // Swipe reply - çift tıkla reply (basit alternatif)
        holder.itemView.setOnClickListener(v -> {
            if (!msg.isDeleted) listener.onReply(msg);
        });
    }

    @Override
    public int getItemCount() { return messages.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvText, tvReply;
        ImageView ivImage;

        VH(@NonNull View v) {
            super(v);
            tvText  = v.findViewById(R.id.tvMessageText);
            tvReply = v.findViewById(R.id.tvReplyText);
            ivImage = v.findViewById(R.id.ivMessageImage);
        }
    }
}
