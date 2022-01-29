package org.fao.mobile.woodidentifier;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import org.apache.commons.io.FileUtils;
import org.fao.mobile.woodidentifier.adapters.ModelVersionViewAdapter;
import org.fao.mobile.woodidentifier.models.InferenceLogViewModel;
import org.fao.mobile.woodidentifier.models.ModelVersion;
import org.fao.mobile.woodidentifier.models.ModelVersionViewModel;
import org.fao.mobile.woodidentifier.utils.ModelHelper;
import org.fao.mobile.woodidentifier.utils.SharedPrefsUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ModelManagerActivity extends AppCompatActivity implements  ModelVersionViewAdapter.ItemListener, View.OnClickListener {
    private static final String TAG = ModelManagerActivity.class.getName();
    private static final int PICKFILE_REQUEST_CODE = 1;
    private Executor executor = Executors.newFixedThreadPool(2);
    private RecyclerView modelList;
    private Button installModelButton;
    private ModelVersionViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_manager);
        this.modelList = (RecyclerView)findViewById(R.id.model_manager_view);
        modelList.setLayoutManager(new LinearLayoutManager(this));
        this.installModelButton = findViewById(R.id.install_model_button);
        installModelButton.setOnClickListener(this);
        this.viewModel = new ViewModelProvider(this).get(ModelVersionViewModel.class);
        viewModel.getCount().observe(this, (count) -> {
            refresh();
        });
        refresh();
    }

    private void refresh() {
        executor.execute(this::refreshList);
    }

    @Override
    public void onClick(View v) {
        String[] mimeTypes =
                {"application/zip"};

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT); // or ACTION_OPEN_DOCUMENT
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        startActivityForResult(intent, PICKFILE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode ==  PICKFILE_REQUEST_CODE) {
            installModelButton.setEnabled(false);
            installModelButton.setText(R.string.installing_model);
            executor.execute(() -> {
                Uri uri = data.getData();
                String filename = getFileName(uri);
                try (InputStream inputStream = getContentResolver().openInputStream(uri);) {
                    File localCopyPath = new File(getFilesDir(), filename);
                    FileUtils.copyToFile(inputStream, localCopyPath);
                    ModelHelper.registerModel(ModelManagerActivity.this, localCopyPath.getCanonicalPath(), false);
                    runOnUiThread(() -> {
                        installModelButton.setText(R.string.install_model);
                        installModelButton.setEnabled(true);
                    });
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

        }
    }

    @SuppressLint("Range")
    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void refreshList() {
        AppDatabase db = Room.databaseBuilder(this.getApplicationContext(),
                AppDatabase.class, "wood-id").build();
        List<ModelVersion> modelVersions = db.modelVersionsDAO().getAll();
        runOnUiThread(() -> {
            Log.i(TAG, "total models " + modelVersions.size());
            modelList.setAdapter(new ModelVersionViewAdapter(this, modelVersions, this));
        });
    }

    @Override
    public void onDeleteItem(ModelVersionViewAdapter modelVersionViewAdapter, int position, ModelVersion modelVersion) {
        executor.execute(() -> {
            AppDatabase db = Room.databaseBuilder(getApplicationContext(),
                    AppDatabase.class, "wood-id").build();
            db.modelVersionsDAO().delete(modelVersion);
            runOnUiThread(() -> {
                modelVersionViewAdapter.notifyItemRemoved(position);
            });
        });
    }

    @Override
    public void refreshItems() {
        refresh();
    }
}