package com.heftreng.app.ui.settings;

import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.heftreng.app.R;

public class SettingsFragment extends Fragment {

    private static final String PREFS    = "heftreng_prefs";
    private static final String KEY_THEME = "theme";
    private static final String KEY_LANG  = "lang";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        super.onViewCreated(view, s);
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);

        // Tema switch
        Switch switchTheme = view.findViewById(R.id.switchTheme);
        int curTheme = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_YES);
        switchTheme.setChecked(curTheme == AppCompatDelegate.MODE_NIGHT_NO);
        switchTheme.setOnCheckedChangeListener((btn, checked) -> {
            int mode = checked ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
            prefs.edit().putInt(KEY_THEME, mode).apply();
            AppCompatDelegate.setDefaultNightMode(mode);
        });

        // Dil
        RadioGroup rgLang = view.findViewById(R.id.rgLanguage);
        String lang = prefs.getString(KEY_LANG, "tr");
        rgLang.check(lang.equals("ku") ? R.id.rbKurdish : R.id.rbTurkish);
        rgLang.setOnCheckedChangeListener((g, id) -> {
            String sel = id == R.id.rbKurdish ? "ku" : "tr";
            prefs.edit().putString(KEY_LANG, sel).apply();
            Toast.makeText(getContext(),
                sel.equals("ku") ? "Ziman guherî: Kurdî" : "Dil: Türkçe",
                Toast.LENGTH_SHORT).show();
        });

        // Bildirimler
        View rowNotifs = view.findViewById(R.id.rowNotifications);
        if (rowNotifs != null) rowNotifs.setOnClickListener(v ->
            Navigation.findNavController(v).navigate(R.id.notificationsFragment));

        // Kaydedilenler
        View rowSaved = view.findViewById(R.id.rowSaved);
        if (rowSaved != null) rowSaved.setOnClickListener(v ->
            Navigation.findNavController(v).navigate(R.id.savedFragment));

        // Profil
        View rowProfile = view.findViewById(R.id.rowProfile);
        if (rowProfile != null) rowProfile.setOnClickListener(v ->
            Navigation.findNavController(v).navigate(R.id.profileFragment));
    }
}
