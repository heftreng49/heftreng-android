package com.heftreng.app.ui.messages;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import de.hdodenhof.circleimageview.CircleImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.heftreng.app.R;
import com.heftreng.app.util.SupabaseClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessagesFragment extends Fragment {

    private RecyclerView recyclerConversations;
    private EditText etSearch;
    private View layoutEmpty, layoutGuest, layoutLoading;
    private FirebaseUser currentUser;
    private ConvAdapter adapter;
    private List<ConvItem> conversations = new ArrayList<>();
    private List<ConvItem> filteredConvs = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_messages, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        recyclerConversations = view.findViewById(R.id.recyclerConversations);
        etSearch              = view.findViewById(R.id.etSearch);
        layoutEmpty           = view.findViewById(R.id.layoutEmpty);
        layoutGuest           = view.findViewById(R.id.layoutGuest);
        layoutLoading         = view.findViewById(R.id.layoutLoading);

        if (currentUser == null) {
            showGuest();
            return;
        }

        adapter = new ConvAdapter(filteredConvs, conv -> openChat(conv));
        recyclerConversations.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerConversations.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                filterConvs(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadConversations();
    }

    // ── Supabase'den konuşmaları çek ─────────────────────────────────────

    private void loadConversations() {
        if (layoutLoading != null) layoutLoading.setVisibility(View.VISIBLE);
        String myUid = currentUser.getUid();

        executor.execute(() -> {
            try {
                // Tema ile aynı sorgu:
                // conversations tablosu: id, participant_a, participant_b,
                // name_a, name_b, photo_a, photo_b, last_message, last_at
                String endpoint = SupabaseClient.SUPABASE_URL
                    + "/rest/v1/conversations"
                    + "?or=(participant_a.eq." + myUid + ",participant_b.eq." + myUid + ")"
                    + "&order=last_at.desc"
                    + "&limit=50";

                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("apikey", SupabaseClient.SUPABASE_ANON_KEY);
                conn.setRequestProperty("Authorization",
                    "Bearer " + SupabaseClient.SUPABASE_ANON_KEY);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() == 200) {
                    Scanner s = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A");
                    String body = s.hasNext() ? s.next() : "[]";
                    JSONArray arr = new JSONArray(body);

                    List<ConvItem> list = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        boolean iAmA = myUid.equals(o.optString("participant_a"));
                        ConvItem item = new ConvItem();
                        item.convId      = o.optString("id");
                        item.othUid      = iAmA ? o.optString("participant_b")
                                                : o.optString("participant_a");
                        item.othName     = iAmA ? o.optString("name_b", "?")
                                                : o.optString("name_a", "?");
                        item.othPhoto    = iAmA ? o.optString("photo_b", "")
                                                : o.optString("photo_a", "");
                        item.lastMessage = o.optString("last_message", "—");
                        item.lastAt      = o.optString("last_at", "");
                        list.add(item);
                    }

                    requireActivity().runOnUiThread(() -> {
                        if (layoutLoading != null) layoutLoading.setVisibility(View.GONE);
                        conversations.clear();
                        conversations.addAll(list);
                        filterConvs(etSearch.getText().toString().trim());
                        if (conversations.isEmpty() && layoutEmpty != null) {
                            layoutEmpty.setVisibility(View.VISIBLE);
                        }
                    });
                } else {
                    requireActivity().runOnUiThread(() -> {
                        if (layoutLoading != null) layoutLoading.setVisibility(View.GONE);
                        Toast.makeText(getContext(),
                            "Mesajlar yüklenemedi", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    if (layoutLoading != null) layoutLoading.setVisibility(View.GONE);
                    Toast.makeText(getContext(),
                        "Bağlantı hatası", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void filterConvs(String query) {
        filteredConvs.clear();
        if (query.isEmpty()) {
            filteredConvs.addAll(conversations);
        } else {
            String q = query.toLowerCase();
            for (ConvItem c : conversations) {
                if (c.othName.toLowerCase().contains(q)
                        || c.lastMessage.toLowerCase().contains(q)) {
                    filteredConvs.add(c);
                }
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        if (layoutEmpty != null) {
            layoutEmpty.setVisibility(filteredConvs.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void openChat(ConvItem conv) {
        Bundle args = new Bundle();
        args.putString("convId",     conv.convId);
        args.putString("otherUid",   conv.othUid);
        args.putString("otherName",  conv.othName);
        args.putString("otherPhoto", conv.othPhoto != null ? conv.othPhoto : "");
        Navigation.findNavController(requireView())
            .navigate(R.id.chatFragment, args);
    }

    private void showGuest() {
        if (recyclerConversations != null)
            recyclerConversations.setVisibility(View.GONE);
        if (layoutGuest != null)
            layoutGuest.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    // ── Veri modeli ───────────────────────────────────────────────────────

    static class ConvItem {
        String convId, othUid, othName, othPhoto, lastMessage, lastAt;
    }

    // ── Adapter ───────────────────────────────────────────────────────────

    static class ConvAdapter extends RecyclerView.Adapter<ConvAdapter.ConvVH> {

        interface OnConvClick { void onClick(ConvItem conv); }

        private final List<ConvItem> list;
        private final OnConvClick listener;

        ConvAdapter(List<ConvItem> list, OnConvClick listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ConvVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
            return new ConvVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ConvVH h, int pos) {
            ConvItem c = list.get(pos);
            h.tvName.setText(c.othName != null ? c.othName : "?");
            h.tvLastMessage.setText(c.lastMessage != null ? c.lastMessage : "—");

            // Zaman formatı
            if (c.lastAt != null && !c.lastAt.isEmpty()) {
                h.tvTime.setText(formatTime(c.lastAt));
            }

            // Profil fotoğrafı
            if (c.othPhoto != null && !c.othPhoto.isEmpty()) {
                Glide.with(h.itemView.getContext())
                    .load(c.othPhoto)
                    .circleCrop()
                    .placeholder(R.drawable.ic_account_circle)
                    .into(h.ivAvatar);
            } else {
                h.ivAvatar.setImageResource(R.drawable.ic_account_circle);
            }

            h.itemView.setOnClickListener(v -> listener.onClick(c));
        }

        private String formatTime(String isoStr) {
            try {
                java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",
                        java.util.Locale.getDefault());
                java.util.Date date = sdf.parse(isoStr.substring(0, 19));
                if (date == null) return "";
                long diff = System.currentTimeMillis() - date.getTime();
                long minutes = diff / 60000;
                long hours   = diff / 3600000;
                long days    = diff / 86400000;
                if (minutes < 1)  return "şimdi";
                if (minutes < 60) return minutes + "dk";
                if (hours   < 24) return hours + "sa";
                if (days    < 7)  return days + "g";
                return new java.text.SimpleDateFormat("dd MMM",
                    new java.util.Locale("tr")).format(date);
            } catch (Exception e) {
                return "";
            }
        }

        @Override public int getItemCount() { return list.size(); }

        static class ConvVH extends RecyclerView.ViewHolder {
            CircleImageView ivAvatar;
            TextView tvName, tvLastMessage, tvTime;
            ConvVH(@NonNull View v) {
                super(v);
                ivAvatar      = v.findViewById(R.id.ivAvatar);
                tvName        = v.findViewById(R.id.tvName);
                tvLastMessage = v.findViewById(R.id.tvLastMessage);
                tvTime        = v.findViewById(R.id.tvTime);
            }
        }
    }
}
