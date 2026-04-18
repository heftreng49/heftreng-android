package com.heftreng.app.ui;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.heftreng.app.R;
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        NavHostFragment navHost=(NavHostFragment)getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if(navHost!=null){
            NavController navController=navHost.getNavController();
            BottomNavigationView bottomNav=findViewById(R.id.bottom_nav);
            if(bottomNav!=null) NavigationUI.setupWithNavController(bottomNav,navController);
        }
    }
}
