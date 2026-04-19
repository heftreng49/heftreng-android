package com.heftreng.app.ui.notifications;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.heftreng.app.R;
import com.heftreng.app.model.HeftNotification;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class NotificationsFragment extends Fragment {

    private RecyclerView recycler;
    private SwipeRefreshLayout swipeRefresh;
    private View layoutEmpty;
    private NotifAdapter adapter;
    private List<HeftNotification> notifs = new ArrayList<>();
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        recycler      = view.findViewById(R.id.recyclerNotifications);
        swipeRefresh  = view.findViewById(R.id.swipeRefresh);
        layoutEmpty   = view.findViewById(R.id.layoutEmpty);

        adapter = new NotifAdapter(notifs);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        swipeRefresh.setColorSchemeResources(R.color.brand_primary);
        swipeRefresh.setOnRefreshListener(this::loadNotifications);

        if (currentUser == null) {
            Toast.makeText(getContext(),
                "Bildirimleri görmek için giriş yapın", Toast.LENGTH_SHORT).show();
        } else {
            loadNotifications();
        }
    }

    private void loadNotifications() {
        if (currentUser == null) return;
        swipeRefresh.setRefreshing(true);

        // Tema: userNotifs/{uid}/msgs subcollection, orderBy ts
        db.collection("userNotifs")
            .document(currentUser.getUid())
            .collection("msgs")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener(snap -> {
                notifs.clear();
                for (QueryDocumentSnapshot doc : snap) {
                    HeftNotification n = doc.toObject(HeftNotification.class);
                    n.id = doc.getId();
                    notifs.add(n);
                    // Okundu işaretle
                    if (!n.read) {
                        doc.getReference().update("read", true);
                    }
                }
                adapter.notifyDataSetChanged();
                swipeRefresh.setRefreshing(false);
                if (layoutEmpty != null) {
                    layoutEmpty.setVisibility(notifs.isEmpty() ? View.VISIBLE : View.GONE);
                }
            })
            .addOnFailureListener(e -> {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(), "Bildirimler yüklenemedi", Toast.LENGTH_SHORT).show();
            });
    }

    // ── Adapter ──────────────────────────────────────────────────────────

    static class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.NotifVH> {
        private final List<HeftNotification> list;
        NotifAdapter(List<HeftNotification> list) { this.list = list; }

        @NonNull
        @Override
        public NotifVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
            return new NotifVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull NotifVH h, int pos) {
            HeftNotification n = list.get(pos);

            // Başlık — tema: title (message değil)
            h.tvMessage.setText(n.title != null ? n.title : buildMessage(n));

            // Alt metin — tema: sub
            if (h.tvSub != null) {
                if (n.sub != null && !n.sub.isEmpty()) {
                    h.tvSub.setVisibility(View.VISIBLE);
                    h.tvSub.setText(n.sub);
                } else {
                    h.tvSub.setVisibility(View.GONE);
                }
            }

            // Zaman — tema: ts (createdAt değil)
            if (n.ts != null && h.tvTime != null) {
                h.tvTime.setText(timeAgo(n.ts.toDate()));
            }

            // İkon / avatar
            if (n.fromPhoto != null && !n.fromPhoto.isEmpty()) {
                Glide.with(h.itemView.getContext())
                    .load(n.fromPhoto)
                    .circleCrop()
                    .placeholder(R.drawable.ic_bell)
                    .into(h.ivIcon);
            } else {
                int iconRes;
                switch (n.type != null ? n.type : "") {
                    case "like":           iconRes = R.drawable.ic_heart_filled; break;
                    case "cmt":            iconRes = R.drawable.ic_chat;         break;
                    case "follow":         iconRes = R.drawable.ic_person;       break;
                    case "post_approved":  iconRes = R.drawable.ic_web;          break;
                    default:               iconRes = R.drawable.ic_bell;         break;
                }
                h.ivIcon.setImageResource(iconRes);
            }

            // Okunmamış nokta
            if (h.dotUnread != null) {
                h.dotUnread.setVisibility(n.read ? View.GONE : View.VISIBLE);
            }
        }

        private String buildMessage(HeftNotification n) {
            String from = n.fromName != null ? n.fromName : "Biri";
            switch (n.type != null ? n.type : "") {
                case "like":           return from + " gönderini beğendi";
                case "cmt":            return from + " gönderine yorum yaptı";
                case "follow":         return from + " seni takip etmeye başladı";
                case "post_approved":  return "Yazın onaylandı!";
                case "post_rejected":  return "Yazın reddedildi.";
                default:               return "Yeni bildirim";
            }
        }

        private String timeAgo(Date date) {
            long diff    = System.currentTimeMillis() - date.getTime();
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
            long hours   = TimeUnit.MILLISECONDS.toHours(diff);
            long days    = TimeUnit.MILLISECONDS.toDays(diff);
            if (minutes < 1)  return "az önce";
            if (minutes < 60) return minutes + " dk önce";
            if (hours   < 24) return hours   + " saat önce";
            if (days    < 7)  return days    + " gün önce";
            return new SimpleDateFormat("dd MMM", new Locale("tr")).format(date);
        }

        @Override public int getItemCount() { return list.size(); }

        static class NotifVH extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvMessage, tvSub, tvTime;
            View dotUnread;
            NotifVH(@NonNull View v) {
                super(v);
                ivIcon    = v.findViewById(R.id.ivIcon);
                tvMessage = v.findViewById(R.id.tvMessage);
                tvSub     = v.findViewById(R.id.tvSub);
                tvTime    = v.findViewById(R.id.tvTime);
                dotUnread = v.findViewById(R.id.dotUnread);
            }
        }
    }
}
