package com.heftreng.app.ui.kurdi;

import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.heftreng.app.R;

public class KurdiFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_kurdi, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        WebView webView = view.findViewById(R.id.webViewKurdi);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("https://heft-reng.blogspot.com/p/kurdi-ferbibe.html");
    }
}
