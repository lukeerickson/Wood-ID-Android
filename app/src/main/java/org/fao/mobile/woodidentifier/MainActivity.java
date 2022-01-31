package org.fao.mobile.woodidentifier;

import static android.os.Environment.DIRECTORY_DOCUMENTS;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.room.Room;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.fao.mobile.woodidentifier.databinding.ActivityMainBinding;
import org.fao.mobile.woodidentifier.models.InferenceLogViewModel;
import org.fao.mobile.woodidentifier.models.InferencesLog;
import org.fao.mobile.woodidentifier.utils.SharedPrefsUtil;
import org.fao.mobile.woodidentifier.utils.StringUtils;
import org.fao.mobile.woodidentifier.utils.Utils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity {
    private static final int PIN_CODE = 1;
    private static final String TAG = MainActivity.class.getName();
    private static final int BUFFER = 4096;
    Executor executor = Executors.newSingleThreadExecutor();

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private NavController navController;
    private InferenceLogViewModel viewModel;
    private MediaScannerConnection msConn;
    private LinearProgressIndicator exportProgressIndicator;
    private View progressGroup;

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
        this.progressGroup = binding.getRoot().findViewById(R.id.progress_group);
        this.exportProgressIndicator = (LinearProgressIndicator)binding.getRoot().findViewById(R.id.export_progress_indicator);
        if (SharedPrefsUtil.getUserInfo(this) != null) {
            Intent enterPinCode = new Intent(this, EnterPinActivity.class);
            startActivityForResult(enterPinCode, PIN_CODE);
        } else {
            Intent splashIntent = new Intent(this, SplashActivity.class);
            startActivity(splashIntent);
        }
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
                    WoodIdentifierApplication application = (WoodIdentifierApplication) getApplication();

                    application.setFromDateContext(0L);
                    application.setToDateContext(Long.MAX_VALUE);

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
        } else if (id == R.id.manage_model) {
            Intent intent = new Intent(this, ModelManagerActivity.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    private void extractCSV() {
        executor.execute(() -> {
            AppDatabase db = Room.databaseBuilder(this.getApplicationContext(),
                    AppDatabase.class, "wood-id").build();
            File cacheDirectory = getCacheDir();
            DateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
            String archiveName = "wood_id_export_" + simpleDateFormat.format(new Date());

            try {
                ArrayList<Pair<String, String>> inputFiles = new ArrayList<>();
                Path archiveDir = Paths.get(cacheDirectory.getCanonicalPath(), archiveName);
                Files.createDirectory(archiveDir);
                File documentsDir = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS);
                Log.i(TAG, "archive path at " + archiveDir);
                String fname = "wood_id_export_" + simpleDateFormat.format(new Date()) + ".csv";
                File exportFileTarget = new File(archiveDir.toString(), fname);
                inputFiles.add(new Pair<>(fname, exportFileTarget.getCanonicalPath()));
                WoodIdentifierApplication application = (WoodIdentifierApplication) getApplication();
                runOnUiThread(()-> {
                    this.progressGroup.setVisibility(View.VISIBLE);
                    this.exportProgressIndicator.setProgressCompat(0, true);
                });
                try (FileWriter fileWriter = new FileWriter(exportFileTarget)) {
                    fileWriter.write("uid,first_name,last_name,timestamp,class,img,lat,long,location,model_name,version,correction,comment\n");
                    for (InferencesLog log : db.inferencesLogDAO().getByDate(application.getFromDateContext(), application.getToDateContext())) {
                        Log.i(TAG, "adding " + log.imagePath);
                        Date date = new Date(log.timestamp);
                        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                        DateFormat dfname = new SimpleDateFormat("yyyyMMddHHmmss'Z'");

                        String currentLocation = SharedPrefsUtil.getCurrentLocation(this);
                        String correction = log.classLabel.equals(log.expectedLabel) ? "" : log.expectedLabel;
                        String suffix = "_capture.jpg";
                        if (!log.expectedLabel.equals(log.classLabel)) {
                            suffix = "corrected.jpg";
                        }
                        String archiveFileName = log.expectedLabel + "/" + dfname.format(date) + "_" + suffix;
                        inputFiles.add(new Pair<>(archiveFileName, log.imagePath.replace("file://", "")));

                        fileWriter.write(log.uid + "," + df.format(date) + "," + csvEscape(log.classLabel) + "," + archiveFileName + "," +
                                log.latitude + "," + log.longitude + "," + csvEscape(currentLocation) + "," + csvEscape(log.modelName) + "," +
                                log.modelVersion + "," + correction + "," + csvEscape(log.comment) + "\n");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                String targetOutputFileName = documentsDir.getCanonicalPath() + "/" + archiveName + ".zip";
                zip(inputFiles, targetOutputFileName);
                cacheDirectory.delete();
                runOnUiThread(()-> {
                    this.exportProgressIndicator.setProgressCompat(0, false);
                    this.progressGroup.setVisibility(View.GONE);
                });
                msConn = new MediaScannerConnection(this.getApplicationContext(), new MediaScannerConnection.MediaScannerConnectionClient() {
                    @Override
                    public void onMediaScannerConnected() {
                        msConn.scanFile(targetOutputFileName, "application/zip");
                    }

                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        msConn.disconnect();
                    }
                });

                msConn.connect();
                runOnUiThread(() -> {

                    Uri zipFileUri = FileProvider.getUriForFile(
                            MainActivity.this,
                            "org.fao.mobile.woodidentifier.provider", //(use your app signature + ".provider" )
                            new File(targetOutputFileName));
                    Toast.makeText(this, getString(R.string.export_successful, targetOutputFileName), Toast.LENGTH_LONG).show();
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/zip");

                    share.putExtra(Intent.EXTRA_STREAM, zipFileUri);

                    startActivity(Intent.createChooser(share, "Share ZIP file"));
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private String csvEscape(String comment) {
        if (StringUtils.isEmpty(comment)) return "";
        comment = comment.replace("\"", "\"\"");
        if (comment.contains(",")) {
            return "\"" + comment + "\"";
        }
        return comment;
    }

    public void zip(List<Pair<String, String>> _files, String zipFileName) {
        try {
            BufferedInputStream origin = null;
            FileOutputStream dest = new FileOutputStream(zipFileName);
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(
                    dest));
            byte data[] = new byte[BUFFER];


            for (int i = 0; i < _files.size(); i++) {
                final int currentProgress = i;
                runOnUiThread(()-> {
                    this.exportProgressIndicator.setProgressCompat((currentProgress * 100 /_files.size()), true);
                });
                Pair<String, String> zipEntry = _files.get(i);
                Log.i("Compress", "Adding: " + zipEntry.second + " as " + zipEntry.first);
                FileInputStream fi = new FileInputStream(zipEntry.second);
                origin = new BufferedInputStream(fi, BUFFER);

                ZipEntry entry = new ZipEntry(zipEntry.first);
                out.putNextEntry(entry);
                int count;

                while ((count = origin.read(data, 0, BUFFER)) != -1) {
                    out.write(data, 0, count);
                }
                origin.close();
            }

            out.close();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void extractJson() {
        executor.execute(() -> {
            AppDatabase db = Room.databaseBuilder(this.getApplicationContext(),
                    AppDatabase.class, "wood-id").build();
            JSONArray jsonArray = new JSONArray();
            WoodIdentifierApplication application = (WoodIdentifierApplication) getApplication();

            for (InferencesLog log : db.inferencesLogDAO().getByDate(application.getFromDateContext(), application.getToDateContext())) {
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
        if (requestCode == PIN_CODE) {
            if (resultCode != 1) {
                finish();
            }
        }
    }


}