package com.heftreng.app.ui.messages;
import android.os.Bundle;
import android.view.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.heftreng.app.R;
public class MessagesFragment extends Fragment {
    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i,@Nullable ViewGroup c,@Nullable Bundle s){
        return i.inflate(R.layout.fragment_messages,c,false);
    }
}
