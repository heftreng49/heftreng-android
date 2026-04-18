package com.heftreng.app.ui.webview;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.heftreng.app.R;
public class WebFragment extends Fragment {
    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i,@Nullable ViewGroup c,@Nullable Bundle s){
        return i.inflate(R.layout.fragment_web,c,false);
    }
    @Override public void onViewCreated(@NonNull View v,@Nullable Bundle s){
        super.onViewCreated(v,s);
        WebView w=v.findViewById(R.id.webView);
        w.getSettings().setJavaScriptEnabled(true);
        w.getSettings().setDomStorageEnabled(true);
        w.setWebViewClient(new WebViewClient());
        w.loadUrl("https://heft-reng.blogspot.com");
    }
}
