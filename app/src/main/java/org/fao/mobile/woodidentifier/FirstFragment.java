package org.fao.mobile.woodidentifier;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.github.dhaval2404.imagepicker.ImagePicker;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.datepicker.MaterialDatePicker;

import org.apache.commons.io.FileUtils;
import org.fao.mobile.woodidentifier.adapters.InferenceLogViewAdapter;
import org.fao.mobile.woodidentifier.callbacks.DBCallback;
import org.fao.mobile.woodidentifier.databinding.FragmentFirstBinding;
import org.fao.mobile.woodidentifier.models.InferenceLogViewModel;
import org.fao.mobile.woodidentifier.models.InferencesLog;
import org.fao.mobile.woodidentifier.utils.ModelHelper;
import org.fao.mobile.woodidentifier.utils.SharedPrefsUtil;
import org.fao.mobile.woodidentifier.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FirstFragment extends Fragment implements InferenceLogViewAdapter.ItemListener, View.OnClickListener {

    private static final int CAPTURE_IMAGE = 101;
    private static final String TAG = FirstFragment.class.getCanonicalName();
    private static final String EXIF_TAG_CLASS = "ML_CLASS";
    private static final String EXIF_TOP_K = "ML_TOP_K";
    private FragmentFirstBinding binding;
    private Executor executor = Executors.newFixedThreadPool(2);
    private RecyclerView logList;
    private InferenceLogViewModel viewModel;


    private View performFilterButton;
    private TextView dateFromField;
    private TextView dateToField;
    private View clearFilter;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);

        this.performFilterButton = binding.getRoot().findViewById(R.id.performFilterButton);
        this.dateFromField = binding.getRoot().findViewById(R.id.dateFromField);
        this.dateToField = binding.getRoot().findViewById(R.id.dateToField);
        this.clearFilter = binding.getRoot().findViewById(R.id.clearFilterButton);

        DateFormat dfname = new SimpleDateFormat("MM/dd/yyyy");
        WoodIdentifierApplication app = (WoodIdentifierApplication)getActivity().getApplication();
        clearFilter.setVisibility(View.INVISIBLE);

        if (app.getFromDateContext() != 0L) {
            dateFromField.setText(dfname.format(new Date(app.getFromDateContext())));
            clearFilter.setVisibility(View.VISIBLE);
        }

        if (app.getToDateContext() != Long.MAX_VALUE) {
            dateToField.setText(dfname.format(new Date(app.getToDateContext())));
            clearFilter.setVisibility(View.VISIBLE);
        }

        this.dateFromField.setOnClickListener(this::onClick);
        this.dateToField.setOnClickListener(this::onClick);
        this.performFilterButton.setOnClickListener(this::onClick);
        this.clearFilter.setOnClickListener(this::onClick);

        this.logList = binding.inferenceLogList;
        this.viewModel = new ViewModelProvider(getActivity()).get(InferenceLogViewModel.class);
        viewModel.getCount().observe(getViewLifecycleOwner(), (count) -> {
            refresh();
        });

        logList.setLayoutManager(new LinearLayoutManager(getActivity()));
        binding.galleryPick.setOnClickListener(this::onClick);
        binding.setLocationButton.setOnClickListener(this::onClick);
        binding.setLocationApplyButton.setOnClickListener(this::onClick);
        binding.setLocationCancelButton.setOnClickListener(this::onClick);
        if (checkCameraHardware(getActivity())) {
            binding.fabCamera.setVisibility(View.VISIBLE);
            binding.fabCamera.setOnClickListener(this::onClick);
        }
        if (SharedPrefsUtil.isDeveloperMode(getActivity())) {
            binding.galleryPick.setVisibility(View.VISIBLE);
        } else {
            binding.galleryPick.setVisibility(View.GONE);
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
            WoodIdentifierApplication app = (WoodIdentifierApplication)getActivity().getApplication();
            List<InferencesLog> logs = db.inferencesLogDAO().getByDate(app.getFromDateContext(), app.getToDateContext());
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
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            ModelHelper modelHelper = ModelHelper.getHelperInstance(getActivity());
            boolean locationTagging = prefs.getBoolean(SharedPrefsUtil.LOCATION_TAGGING, false);
            AppDatabase db = Room.databaseBuilder(getActivity().getApplicationContext(),
                    AppDatabase.class, "wood-id").build();
            File localCopyPath = null;
            String identificationId = UUID.randomUUID().toString();
            try (InputStream inputStream = getActivity().getContentResolver().openInputStream(uri);) {
                localCopyPath = new File(getActivity().getFilesDir(), identificationId + ".jpg");
                FileUtils.copyToFile(inputStream, localCopyPath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            File finalLocalCopyPath = localCopyPath;

            newLog(db, new DBCallback() {
                        @Override
                        public void onDone(InferencesLog log) {
                            refresh();

                            if (!locationTagging || (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
                                Log.w(TAG, "No location permission");
                                identifySpecies(identificationId, finalLocalCopyPath, log, modelHelper, null);
                            } else {
                                Log.d(TAG, "Getting last known location");
                                FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(getActivity());
                                LocationRequest locationRequest = LocationRequest.create();
                                locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

                                fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, null).addOnSuccessListener((location) -> {
                                    if (location != null) {
                                        Log.d(TAG, "location = " + location.getLatitude() + "," + location.getLongitude());
                                        identifySpecies(identificationId, finalLocalCopyPath, log, modelHelper, location);
                                    } else {
                                        Log.w(TAG, "Requested but failed to obtain a location");
                                        identifySpecies(identificationId, finalLocalCopyPath, log, modelHelper, null);
                                    }
                                });
                            }
                        }

                        @Override
                        public void beforeSave(InferencesLog initialInferenceLog) {
                            initialInferenceLog.imagePath = Uri.fromFile(finalLocalCopyPath).toString();
                            initialInferenceLog.originalFilename = getFileName(uri);
                        }
                    }
            );
        } else if (resultCode == ImagePicker.RESULT_ERROR) {
            Toast.makeText(getActivity(), ImagePicker.getError(data), Toast.LENGTH_SHORT).show();
        }
    }

    private void identifySpecies(String name, File localCopyPath, InferencesLog log, ModelHelper modelHelper, Location location) {
        executor.execute(() -> {
            AppDatabase db = Room.databaseBuilder(getActivity().getApplicationContext(),
                    AppDatabase.class, "wood-id").build();
            ModelHelper.Result result = null;
            try (InputStream is = new FileInputStream(localCopyPath)) {
                result = modelHelper == null ? ModelHelper.Result.emptyResult() : modelHelper.runInference(name, is);
            } catch (IOException e) {
                e.printStackTrace();
            }

            Log.i(TAG, "topk " + Utils.showArray(result.getTop()));
            Log.i(TAG, "scores " + Utils.showArray(result.getScores()));

            log.updateResult(result, modelHelper);
            log.locationName = binding.locationMarkerField.getText().toString();

            if (location != null) {
                log.setLongitude(location.getLongitude());
                log.setLatitude(location.getLatitude());
                log.setLocationAccuracy(location.getAccuracy());
            }

            updateLog(db, log, new DBCallback() {
                @Override
                public void onDone(InferencesLog savedLog) {
                    refresh();
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    if (prefs.getBoolean("show_details_after_capture", false)) {
                        Intent intent = new Intent(getActivity(), DetailsActivity.class);
                        intent.putExtra("uid", savedLog.uid);
                        Log.i(TAG, "uid = " + savedLog.uid);
                        getActivity().startActivity(intent);
                    }
                }

                @Override
                public void beforeSave(InferencesLog initialInferenceLog) {
                }
            });
        });
    }

    private void newLog(AppDatabase db, DBCallback callback) {
        InferencesLog initialInferenceLog = new InferencesLog();
        initialInferenceLog.timestamp = System.currentTimeMillis();
        executor.execute(() -> {
            callback.beforeSave(initialInferenceLog);
            initialInferenceLog.uid = db.inferencesLogDAO().insert(initialInferenceLog);
            if (callback != null) {
                callback.onDone(initialInferenceLog);
            }
        });
    }

    private void updateLog(AppDatabase db, InferencesLog log, DBCallback callback) {
        executor.execute(() -> {
            db.inferencesLogDAO().update(log);
            Log.i(TAG, " uid updated " + log.uid);
            WoodIdentifierApplication app = (WoodIdentifierApplication)getActivity().getApplication();
            List<InferencesLog> logs = db.inferencesLogDAO().getByDate(app.getFromDateContext(), app.getToDateContext());
            getActivity().runOnUiThread(() -> {
                logList.setAdapter(new InferenceLogViewAdapter(getActivity(), logs, this));
                if (callback != null) {
                    callback.onDone(log);
                }
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

    public void onClick(View view) {
        DateFormat dfname = new SimpleDateFormat("MM/dd/yyyy");
        WoodIdentifierApplication app = (WoodIdentifierApplication)getActivity().getApplication();
        switch (view.getId()) {
            case R.id.gallery_pick:
                int permission = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE);

                if (PackageManager.PERMISSION_GRANTED != permission) {
                    Utils.verifyStoragePermissions(getActivity());
                } else {
                    ImagePicker.with(FirstFragment.this).galleryOnly().start();
                }

                break;
            case R.id.fab_camera:
                permission = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA);

                if (PackageManager.PERMISSION_GRANTED != permission) {
                    Utils.verifyCameraPermissions(getActivity());
                } else {
                    Intent intent = new Intent(getActivity(), ImageCaptureActivity2.class);
                    startActivityForResult(intent, CAPTURE_IMAGE);
                }
                break;
            case R.id.set_location_button:
                binding.locationMarkerField.setVisibility(View.VISIBLE);
                binding.locationMarker.setVisibility(View.GONE);
                binding.setLocationButton.setVisibility(View.GONE);
                binding.editButtonsGroup.setVisibility(View.VISIBLE);
                binding.locationMarkerField.requestFocus();
                break;
            case R.id.set_location_cancel_button:
                binding.locationMarkerField.setVisibility(View.GONE);
                binding.locationMarker.setVisibility(View.VISIBLE);
                binding.setLocationButton.setVisibility(View.VISIBLE);
                binding.editButtonsGroup.setVisibility(View.GONE);
                break;
            case R.id.set_location_apply_button:
                String locationValue = binding.locationMarkerField.getText().toString();
                SharedPrefsUtil.setCurrentLocation(getActivity(), locationValue);
                binding.locationMarker.setText(locationValue);
                binding.locationMarkerField.setVisibility(View.GONE);
                binding.locationMarker.setVisibility(View.VISIBLE);
                binding.setLocationButton.setVisibility(View.VISIBLE);
                binding.editButtonsGroup.setVisibility(View.GONE);
                break;
            case R.id.dateToField:
            case R.id.dateFromField:
                MaterialDatePicker<Pair<Long,Long>> datePickerFrom = MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText("Select From Date")
                        .setSelection(new Pair<Long, Long>(MaterialDatePicker.thisMonthInUtcMilliseconds(), MaterialDatePicker.todayInUtcMilliseconds()))
                        .build();
                datePickerFrom.show(getParentFragmentManager(), "date_picker_from");
                datePickerFrom.addOnPositiveButtonClickListener(selection -> {
                    this.dateToField.setText(dfname.format(selection.second));
                    this.dateFromField.setText(dfname.format(selection.first));
                    applyDateFilter(app);
                });
                break;
            case R.id.performFilterButton:
                applyDateFilter(app);
                break;
            case R.id.clearFilterButton:
                this.dateToField.setText("");
                this.dateFromField.setText("");
                app.setFromDateContext(0);
                app.setToDateContext(Long.MAX_VALUE);
                clearFilter.setVisibility(View.INVISIBLE);
                refresh();
                break;
        }

    }

    private void applyDateFilter(WoodIdentifierApplication app) {
        DateFormat filterFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");


        long fromDate = app.getFromDateContext();
        long toDate = app.getToDateContext();

        if (!this.dateFromField.getText().toString().trim().isEmpty()) {
            try {
                fromDate = filterFormat.parse(this.dateFromField.getText().toString().trim() + " 00:00:00").getTime();
            } catch (ParseException e) {
                this.dateFromField.requestFocus();
                this.dateFromField.setError("Invalid date format should be MM/DD/YYYY");
                e.printStackTrace();
                return;
            }
        } else {
            fromDate = 0L;
        }

        if (!this.dateToField.getText().toString().trim().isEmpty()) {
            try {
                toDate = filterFormat.parse(this.dateToField.getText().toString().trim() + " 23:59:59").getTime();
            } catch (ParseException e) {
                this.dateToField.requestFocus();
                this.dateToField.setError("Invalid date format should be MM/DD/YYYY");
                e.printStackTrace();
                return;
            }
        } else {
            toDate = Long.MAX_VALUE;
        }

        app.setFromDateContext(fromDate);
        app.setToDateContext(toDate);
        clearFilter.setVisibility(View.VISIBLE);
        Toast.makeText(getActivity(),R.string.applying_filter, Toast.LENGTH_LONG).show();
        refresh();
    }
}