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
import com.heftreng.app.model.BlogPost;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class BlogAdapter extends RecyclerView.Adapter<BlogAdapter.BlogViewHolder> {

    public interface OnBlogClickListener {
        void onPostClick(BlogPost post);
        void onAuthorClick(String uid);
    }

    private final List<BlogPost> posts;
    private final OnBlogClickListener listener;

    public BlogAdapter(List<BlogPost> posts, OnBlogClickListener listener) {
        this.posts = posts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public BlogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_blog_post, parent, false);
        return new BlogViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull BlogViewHolder h, int position) {
        BlogPost post = posts.get(position);

        h.tvTitle.setText(post.title != null ? post.title : "");
        h.tvSummary.setText(post.summary != null ? post.summary : "");
        h.tvCategory.setText(post.category != null ? post.category : "Genel");
        h.tvReadTime.setText(post.readTimeMinutes > 0 ? post.readTimeMinutes + " dk okuma" : "");

        // Yazar adı + tarih
        String authorLabel = post.authorName != null ? post.authorName : "";
        if (post.createdAt != null) {
            String date = new SimpleDateFormat("dd MMM yyyy", new Locale("tr"))
                .format(post.createdAt.toDate());
            authorLabel += (authorLabel.isEmpty() ? "" : " · ") + date;
        }
        h.tvAuthorName.setText(authorLabel);

        // Kapak görseli — tema: cover (coverUrl değil)
        String coverUrl = post.cover != null ? post.cover : "";
        if (!coverUrl.isEmpty()) {
            h.ivCover.setVisibility(View.VISIBLE);
            Glide.with(h.itemView.getContext())
                .load(coverUrl)
                .centerCrop()
                .into(h.ivCover);
        } else {
            h.ivCover.setVisibility(View.GONE);
        }

        // Yazar fotoğrafı
        if (post.authorPhoto != null && !post.authorPhoto.isEmpty()) {
            Glide.with(h.itemView.getContext())
                .load(post.authorPhoto)
                .circleCrop()
                .placeholder(R.drawable.ic_account_circle)
                .into(h.ivAuthorPhoto);
        } else {
            h.ivAuthorPhoto.setImageResource(R.drawable.ic_account_circle);
        }

        // Onay durumu rozeti
        if (h.tvStatus != null) {
            if ("approved".equals(post.status)) {
                h.tvStatus.setVisibility(View.GONE);
            } else if ("rejected".equals(post.status)) {
                h.tvStatus.setVisibility(View.VISIBLE);
                h.tvStatus.setText("Reddedildi");
                h.tvStatus.setTextColor(0xFFEF4444);
            } else {
                h.tvStatus.setVisibility(View.VISIBLE);
                h.tvStatus.setText("İncelemede");
                h.tvStatus.setTextColor(0xFFF59E0B);
            }
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPostClick(post);
        });
        h.ivAuthorPhoto.setOnClickListener(v -> {
            if (listener != null && post.authorId != null)
                listener.onAuthorClick(post.authorId);
        });
    }

    @Override
    public int getItemCount() { return posts.size(); }

    static class BlogViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSummary, tvCategory, tvAuthorName, tvReadTime, tvStatus;
        ImageView ivCover, ivAuthorPhoto;

        BlogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle      = itemView.findViewById(R.id.tvTitle);
            tvSummary    = itemView.findViewById(R.id.tvSummary);
            tvCategory   = itemView.findViewById(R.id.tvCategory);
            tvAuthorName = itemView.findViewById(R.id.tvAuthorName);
            tvReadTime   = itemView.findViewById(R.id.tvReadTime);
            tvStatus     = itemView.findViewById(R.id.tvStatus);   // opsiyonel
            ivCover      = itemView.findViewById(R.id.ivCover);
            ivAuthorPhoto= itemView.findViewById(R.id.ivAuthorPhoto);
        }
    }
}
