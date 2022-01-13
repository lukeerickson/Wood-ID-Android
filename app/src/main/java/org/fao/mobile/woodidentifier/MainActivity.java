package org.fao.mobile.woodidentifier;

import static android.os.Environment.DIRECTORY_DOCUMENTS;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Environment;

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
import org.fao.mobile.woodidentifier.utils.Utils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
        } else if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        } else if (id == R.id.action_clear) {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
            alertDialog.setMessage(R.string.confirm_delete_all);
            alertDialog.setPositiveButton(R.string.yes, (dialog, which) -> {
                executor.execute(() -> {
                    AppDatabase db = Room.databaseBuilder(this.getApplicationContext(),
                            AppDatabase.class, "wood-id").build();
                    db.inferencesLogDAO().deleteAll();
                    runOnUiThread(() -> {
                        viewModel.updateCount(0);
                    });
                });

            });
            alertDialog.setNegativeButton(R.string.cancel, (dialog, which) -> {
                dialog.dismiss();
            });
            alertDialog.show();
        } else if (id == R.id.action_recalibrate) {
            int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

            if (PackageManager.PERMISSION_GRANTED != permission) {
                Utils.verifyCameraPermissions(this);
            } else {
                Intent intent = new Intent(this, RecalibrateCameraActivity.class);
                startActivity(intent);
            }
        } else if (id == R.id.export_csv) {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
            alertDialog.setMessage(R.string.confirm_json_export);
            alertDialog.setPositiveButton(R.string.yes, (dialog, which) -> {
                extractCSV();
            });
            alertDialog.setNegativeButton(R.string.cancel, (dialog, which) -> {
                dialog.dismiss();
            });
            alertDialog.show();
        }

        return super.onOptionsItemSelected(item);
    }

    private void extractCSV() {
        executor.execute(() -> {
            AppDatabase db = Room.databaseBuilder(this.getApplicationContext(),
                    AppDatabase.class, "wood-id").build();
            File documentsDir = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS);
            DateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddhhmmss");
            String fname = "wood_id_export_" + simpleDateFormat.format(new Date()) + ".csv";
            File exportFileTarget = new File(documentsDir, fname);
            try (FileWriter fileWriter = new FileWriter(exportFileTarget)) {
                fileWriter.write("uid,timestamp,class,img,lat,long\n");
                for (InferencesLog log : db.inferencesLogDAO().getAll()) {
                    fileWriter.write(log.uid + "," + log.timestamp + "," + log.classLabel + "," + log.imagePath + "," + log.latitude + "," + log.longitude + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            runOnUiThread(() -> {
                try {
                    Toast.makeText(this, getString(R.string.export_successful, exportFileTarget.getCanonicalPath()), Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        });
    }

    private void extractJson() {
        executor.execute(() -> {
            AppDatabase db = Room.databaseBuilder(this.getApplicationContext(),
                    AppDatabase.class, "wood-id").build();
            JSONArray jsonArray = new JSONArray();
            for (InferencesLog log : db.inferencesLogDAO().getAll()) {
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("uid", log.uid);
                    jsonObject.put("timestamp", log.timestamp);
                    jsonObject.put("class", log.classLabel);
                    jsonObject.put("expectedClass", log.expectedLabel);
                    jsonObject.put("img", log.imagePath);
                    jsonObject.put("topk", log.top);
                    jsonObject.put("topRaw", log.topKRaw);
                    jsonObject.put("score", log.score);
                    jsonObject.put("originalFilename", log.originalFilename);
                    jsonObject.put("lat", log.latitude);
                    jsonObject.put("long", log.longitude);
                    jsonObject.put("location_accuracy", log.locationAccuracy);
                    jsonObject.put("scores", log.scores);
                    jsonObject.put("version", log.modelVersion);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                jsonArray.put(jsonObject);
            }
            File documentsDir = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS);
            DateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddhhmmss");
            String fname = "wood_id_export_" + simpleDateFormat.format(new Date()) + ".json";
            File exportFileTarget = new File(documentsDir, fname);
            try (FileWriter fileWriter = new FileWriter(exportFileTarget)) {
                fileWriter.write(jsonArray.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
            runOnUiThread(() -> {
                try {
                    Toast.makeText(this, getString(R.string.export_successful, exportFileTarget.getCanonicalPath()), Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        });
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