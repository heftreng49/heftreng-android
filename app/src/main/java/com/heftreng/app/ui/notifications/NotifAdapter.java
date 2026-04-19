package com.heftreng.app.ui.notifications;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.heftreng.app.R;
import com.heftreng.app.model.HeftNotification;
import java.util.*;

import de.hdodenhof.circleimageview.CircleImageView;

public class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.VH> {

    private final List<HeftNotification> list;
    public NotifAdapter(List<HeftNotification> l) { this.list = l; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext())
            .inflate(R.layout.item_notification, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        HeftNotification n = list.get(pos);

        // Avatar / ikon
        int ico = R.drawable.ic_bell;
        if ("like".equals(n.type))        ico = R.drawable.ic_heart_filled;
        else if ("cmt".equals(n.type))    ico = R.drawable.ic_chat;
        else if ("follow".equals(n.type)) ico = R.drawable.ic_person;

        if (n.fromPhoto != null && !n.fromPhoto.isEmpty()) {
            Glide.with(h.ivAvatar)
                .load(n.fromPhoto)
                .circleCrop()
                .placeholder(ico)
                .into(h.ivAvatar);
        } else {
            h.ivAvatar.setImageResource(ico);
        }

        // Metin
        h.tvText.setText(n.text != null ? n.text : "");

        // Zaman
        h.tvTime.setText(n.ts != null
            ? android.text.format.DateUtils
                .getRelativeTimeSpanString(n.ts.toDate().getTime()).toString()
            : "");

        // Okunmamış noktası
        h.dotUnread.setVisibility(Boolean.TRUE.equals(n.read) ? View.GONE : View.VISIBLE);

        // Okunmuş opaklık
        h.itemView.setAlpha(Boolean.TRUE.equals(n.read) ? 0.65f : 1f);
    }

    @Override public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        CircleImageView ivAvatar;
        TextView tvText, tvTime;
        View dotUnread;

        VH(View v) {
            super(v);
            ivAvatar  = v.findViewById(R.id.ivAvatar);
            tvText    = v.findViewById(R.id.tvNotifText);
            tvTime    = v.findViewById(R.id.tvNotifTime);
            dotUnread = v.findViewById(R.id.dotUnread);
        }
    }
}
