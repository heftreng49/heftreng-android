package com.heftreng.app.ui.notifications;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.heftreng.app.R;
import com.heftreng.app.model.HeftNotification;
import java.text.SimpleDateFormat;
import java.util.*;

public class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.VH> {

    private final List<HeftNotification> items;
    public NotifAdapter(List<HeftNotification> items) { this.items = items; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext())
            .inflate(R.layout.item_notification, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        HeftNotification n = items.get(pos);

        int ico = R.drawable.ic_bell;
        if ("like".equals(n.type))   ico = R.drawable.ic_heart_filled;
        else if ("cmt".equals(n.type))    ico = R.drawable.ic_chat;
        else if ("follow".equals(n.type)) ico = R.drawable.ic_person;

        if (n.fromPhoto != null && !n.fromPhoto.isEmpty()) {
            Glide.with(h.ivAvatar).load(n.fromPhoto).circleCrop()
                .placeholder(ico).into(h.ivAvatar);
        } else {
            h.ivAvatar.setImageResource(ico);
        }

        String text = n.title != null ? n.title : "";
        if (n.sub != null && !n.sub.isEmpty()) text += "\n" + n.sub;
        h.tvText.setText(text);

        if (n.ts != null) {
            long diff = System.currentTimeMillis() - n.ts.toDate().getTime();
            String t2 = diff < 60000 ? "Az önce"
                : diff < 3600000 ? (diff/60000)+"dk"
                : diff < 86400000 ? (diff/3600000)+"sa"
                : new SimpleDateFormat("d MMM", Locale.getDefault()).format(n.ts.toDate());
            h.tvTime.setText(t2);
        }

        h.dotUnread.setVisibility(n.read ? View.GONE : View.VISIBLE);
        h.itemView.setAlpha(n.read ? 0.7f : 1f);
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivAvatar; TextView tvText, tvTime; View dotUnread;
        VH(View v) {
            super(v);
            ivAvatar  = v.findViewById(R.id.ivAvatar);
            tvText    = v.findViewById(R.id.tvNotifText);
            tvTime    = v.findViewById(R.id.tvNotifTime);
            dotUnread = v.findViewById(R.id.dotUnread);
        }
    }
}
