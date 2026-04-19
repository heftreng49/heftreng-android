package com.heftreng.app.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import com.heftreng.app.R;

public class SettingsFragment extends Fragment {

    private static final String PREFS = "heftreng_prefs";
    private static final String KEY_THEME = "theme";
    private static final String KEY_LANG = "lang";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);

        // Tema switch
        Switch switchTheme = view.findViewById(R.id.switchTheme);
        int currentTheme = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_YES);
        switchTheme.setChecked(currentTheme == AppCompatDelegate.MODE_NIGHT_NO);
        switchTheme.setOnCheckedChangeListener((btn, isChecked) -> {
            int mode = isChecked ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
            prefs.edit().putInt(KEY_THEME, mode).apply();
            AppCompatDelegate.setDefaultNightMode(mode);
        });

        // Dil seçimi
        RadioGroup rgLang = view.findViewById(R.id.rgLanguage);
        String lang = prefs.getString(KEY_LANG, "tr");
        if (lang.equals("ku")) {
            rgLang.check(R.id.rbKurdish);
        } else {
            rgLang.check(R.id.rbTurkish);
        }
        rgLang.setOnCheckedChangeListener((group, checkedId) -> {
            String selected = checkedId == R.id.rbKurdish ? "ku" : "tr";
            prefs.edit().putString(KEY_LANG, selected).apply();
            Toast.makeText(getContext(),
                    selected.equals("ku") ? "Ziman guherî: Kurdî" : "Dil değiştirildi: Türkçe",
                    Toast.LENGTH_SHORT).show();
        });
    }
}
