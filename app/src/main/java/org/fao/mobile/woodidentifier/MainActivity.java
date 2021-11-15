package org.fao.mobile.woodidentifier;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.github.dhaval2404.imagepicker.ImagePicker;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.room.Room;

import org.fao.mobile.woodidentifier.databinding.ActivityMainBinding;
import org.fao.mobile.woodidentifier.models.InferenceLogViewModel;
import org.fao.mobile.woodidentifier.models.InferencesLog;
import org.fao.mobile.woodidentifier.ui.login.LoginActivity;
import org.fao.mobile.woodidentifier.utils.ModelHelper;
import org.fao.mobile.woodidentifier.utils.PhoneAutoConfig;
import org.fao.mobile.woodidentifier.utils.SharedPrefsUtil;
import org.fao.mobile.woodidentifier.utils.Utils;
import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class MainActivity extends AppCompatActivity {
    Executor executor = Executors.newSingleThreadExecutor();

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private NavController navController;
    private InferenceLogViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        this.viewModel = new ViewModelProvider(this).get(InferenceLogViewModel.class);
        this.navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        Intent splashIntent = new Intent(this, SplashActivity.class);
        startActivity(splashIntent);
    }

    private boolean firstRun() {
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_about) {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        }
        else
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }
        else
        if (id== R.id.action_clear) {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
            alertDialog.setMessage(R.string.confirm_delete_all);
            alertDialog.setPositiveButton(R.string.yes, (dialog, which) -> {
                executor.execute(()-> {
                    AppDatabase db = Room.databaseBuilder(this.getApplicationContext(),
                            AppDatabase.class, "wood-id").build();
                    db.inferencesLogDAO().deleteAll();
                    runOnUiThread(()-> {
                        viewModel.updateCount(0);
                    });
                });

            });
            alertDialog.setNegativeButton(R.string.cancel, (dialog, which) -> {
                dialog.dismiss();
            });
            alertDialog.show();
        }
        else
        if (id == R.id.action_recalibrate) {
            int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

            if (PackageManager.PERMISSION_GRANTED != permission) {
                Utils.verifyCameraPermissions(this);
            } else {
                Intent intent = new Intent(this, RecalibrateCameraActivity.class);
                startActivity(intent);
            }
        }
        else
            if (id == R.id.export_csv) {
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
                alertDialog.setMessage(R.string.confirm_csv_export);
                alertDialog.setPositiveButton(R.string.yes, (dialog, which) -> {
                    executor.execute(()-> {
                        AppDatabase db = Room.databaseBuilder(this.getApplicationContext(),
                                AppDatabase.class, "wood-id").build();
                    });

                });
                alertDialog.setNegativeButton(R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                });
                alertDialog.show();
            }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
}