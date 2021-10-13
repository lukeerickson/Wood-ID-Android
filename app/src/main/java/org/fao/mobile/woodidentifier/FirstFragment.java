package org.fao.mobile.woodidentifier;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.github.dhaval2404.imagepicker.ImagePicker;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;

import org.apache.commons.io.FileUtils;
import org.fao.mobile.woodidentifier.adapters.InferenceLogViewAdapter;
import org.fao.mobile.woodidentifier.callbacks.DBCallback;
import org.fao.mobile.woodidentifier.databinding.FragmentFirstBinding;
import org.fao.mobile.woodidentifier.models.InferenceLogViewModel;
import org.fao.mobile.woodidentifier.models.InferencesLog;
import org.fao.mobile.woodidentifier.utils.ModelHelper;
import org.fao.mobile.woodidentifier.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FirstFragment extends Fragment implements InferenceLogViewAdapter.ItemListener {

    private static final int CAPTURE_IMAGE = 101;
    private static final String TAG = FirstFragment.class.getCanonicalName();
    private static final String EXIF_TAG_CLASS = "ML_CLASS";
    private static final String EXIF_TOP_K = "ML_TOP_K";
    private FragmentFirstBinding binding;
    private Executor executor = Executors.newSingleThreadExecutor();
    private RecyclerView logList;
    private InferenceLogViewModel viewModel;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        this.logList = binding.inferenceLogList;
        this.viewModel = new ViewModelProvider(getActivity()).get(InferenceLogViewModel.class);
        viewModel.getCount().observe(getViewLifecycleOwner(), (count) -> {
            refresh();
        });

        logList.setLayoutManager(new LinearLayoutManager(getActivity()));
        binding.fab.setOnClickListener(this::onClick);
        if (checkCameraHardware(getActivity())) {
            binding.fabCamera.setVisibility(View.VISIBLE);
            binding.fabCamera.setOnClickListener(this::onClick);
        }
        final RelativeLayout root = binding.getRoot();
        return root;
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refresh();
    }

    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    private void refresh() {
        executor.execute(() -> {
            AppDatabase db = Room.databaseBuilder(getActivity().getApplicationContext(),
                    AppDatabase.class, "wood-id").build();
            List<InferencesLog> logs = db.inferencesLogDAO().getAll();
            getActivity().runOnUiThread(() -> {
                logList.setAdapter(new InferenceLogViewAdapter(getActivity(), logs, this));
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getActivity().getContentResolver().query(uri, null, null, null, null);
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "XXXXXX on Activity result");
        if (resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();

            ModelHelper modelHelper = ModelHelper.getHelperInstance(getActivity());
            FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(getActivity());

            if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "No location permission");
                identifySpecies(uri, modelHelper, null);
            } else {
                Log.d(TAG, "Getting last known location");
                LocationRequest locationRequest = LocationRequest.create();
                locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

                fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, null).addOnSuccessListener((location) -> {
                    if (location != null) {
                        Log.d(TAG, "location = " + location.getLatitude() + "," + location.getLongitude());
                        identifySpecies(uri, modelHelper, location);
                    } else {
                        Log.w(TAG, "Requested but failed to obtain a location");
                        identifySpecies(uri, modelHelper, null);
                    }
                });
            }
        } else if (resultCode == ImagePicker.RESULT_ERROR) {
            Toast.makeText(getActivity(), ImagePicker.getError(data), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getActivity(), "Task Cancelled", Toast.LENGTH_SHORT).show();
        }
    }

    private void identifySpecies(Uri uri, ModelHelper modelHelper, Location location) {
        executor.execute(() -> {
            try (InputStream inputStream = getActivity().getContentResolver().openInputStream(uri);) {
                File localCopyPath = new File(getActivity().getFilesDir(), UUID.randomUUID().toString() + ".jpg");
                FileUtils.copyToFile(inputStream, localCopyPath);

                try (InputStream is = new FileInputStream(localCopyPath)) {
                    ModelHelper.Result result = modelHelper == null ? ModelHelper.Result.emptyResult() : modelHelper.runInference(is);
                    Log.i(TAG, "topk " + Utils.showArray(result.getTop()));
                    Log.i(TAG, "scores " + Utils.showArray(result.getScores()));
                    AppDatabase db = Room.databaseBuilder(getActivity().getApplicationContext(),
                            AppDatabase.class, "wood-id").build();
                    InferencesLog log = InferencesLog.fromResult(result, modelHelper);

                    if (location != null) {
                        log.setLongitude(location.getLongitude());
                        log.setLatitude(location.getLatitude());
                        log.setLocationAccuracy(location.getAccuracy());
                    }

                    log.imagePath = Uri.fromFile(localCopyPath).toString();
                    log.originalFilename = getFileName(uri);
                    saveLog(db, log, (inferencesLog) -> {
                        refresh();
                        Intent intent = new Intent(getActivity(), DetailsActivity.class);
                        intent.putExtra("uid", inferencesLog.uid);
                        Log.i(TAG, "uid = " + inferencesLog.uid);
                        getActivity().startActivity(intent);
                    });
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void saveLog(AppDatabase db, InferencesLog log, DBCallback callback) {
        executor.execute(() -> {
            log.uid = db.inferencesLogDAO().insert(log);
            Log.i(TAG, " uid saved " + log.uid);
            List<InferencesLog> logs = db.inferencesLogDAO().getAll();
            getActivity().runOnUiThread(() -> {
                logList.setAdapter(new InferenceLogViewAdapter(getActivity(), logs, this));
                callback.onDone(log);
            });
        });
    }

    @Override
    public void onDeleteItem(InferenceLogViewAdapter inferenceLogViewAdapter, int position, InferencesLog loginfo) {
        executor.execute(() -> {
            AppDatabase db = Room.databaseBuilder(getActivity().getApplicationContext(),
                    AppDatabase.class, "wood-id").build();
            db.inferencesLogDAO().delete(loginfo);
            getActivity().runOnUiThread(() -> {
                inferenceLogViewAdapter.notifyItemRemoved(position);
            });
        });
    }

    private void onClick(View view) {
        int location_permission = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION);
        switch (view.getId()) {
            case R.id.fab:
                int permission = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE);

                if (PackageManager.PERMISSION_GRANTED != permission && PackageManager.PERMISSION_GRANTED != location_permission) {
                    Utils.verifyStoragePermissions(getActivity());
                } else {
                    ImagePicker.with(FirstFragment.this).galleryOnly().start();
                }

                break;
            case R.id.fab_camera:
                permission = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA);

                if (PackageManager.PERMISSION_GRANTED != permission && PackageManager.PERMISSION_GRANTED != location_permission) {
                    Utils.verifyCameraPermissions(getActivity());
                } else {
                    Intent intent = new Intent(getActivity(), ImageCaptureActivity2.class);
                    startActivityForResult(intent, CAPTURE_IMAGE);
                }
                break;
        }

    }
}