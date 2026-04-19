package com.heftreng.app.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.heftreng.app.R;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private NavController navController;

    // Bu fragment'larda bottom nav gizlensin
    private static final List<Integer> HIDE_NAV_FRAGMENTS = Arrays.asList(
        R.id.chatFragment,
        R.id.composeFragment,
        R.id.blogDetailFragment
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
            .findFragmentById(R.id.nav_host_fragment);

        if (navHost != null) {
            navController = navHost.getNavController();
            bottomNav     = findViewById(R.id.bottom_nav);

            NavigationUI.setupWithNavController(bottomNav, navController);

            // Belirli ekranlarda bottom nav'ı gizle
            navController.addOnDestinationChangedListener((ctrl, dest, args) -> {
                if (HIDE_NAV_FRAGMENTS.contains(dest.getId())) {
                    bottomNav.setVisibility(View.GONE);
                } else {
                    bottomNav.setVisibility(View.VISIBLE);
                }
            });

            // Bildirim rozeti
            loadNotificationBadge();
        }
    }

    private void loadNotificationBadge() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
            .collection("userNotifs")
            .document(user.getUid())
            .collection("msgs")
            .whereEqualTo("read", false)
            .get()
            .addOnSuccessListener(snap -> {
                int count = snap.size();
                if (count > 0) {
                    BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.notificationsFragment);
                    badge.setNumber(count > 9 ? 9 : count);
                    badge.setVisible(true);
                } else {
                    bottomNav.removeBadge(R.id.notificationsFragment);
                }
            });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotificationBadge();
    }
}
