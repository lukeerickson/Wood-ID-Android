package org.fao.mobile.woodidentifier;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.github.dhaval2404.imagepicker.ImagePicker;

import org.fao.mobile.woodidentifier.adapters.InferenceLogViewAdapter;
import org.fao.mobile.woodidentifier.databinding.FragmentFirstBinding;
import org.fao.mobile.woodidentifier.models.InferencesLog;
import org.fao.mobile.woodidentifier.utils.ModelHelper;
import org.fao.mobile.woodidentifier.utils.Utils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FirstFragment extends Fragment implements InferenceLogViewAdapter.ItemListener {

    private FragmentFirstBinding binding;
    private Executor executor = Executors.newSingleThreadExecutor();
    private RecyclerView logList;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        this.logList = (RecyclerView) binding.inferenceLogList;
        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int permission = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE);

                if (permission != PackageManager.PERMISSION_GRANTED) {
                    Utils.verifyStoragePermissions(getActivity());
                } else {
                    ImagePicker.with(FirstFragment.this).galleryOnly().start();
                 }
            }
        });
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refresh();
    }

    private void refresh() {
        executor.execute(() -> {
            AppDatabase db = Room.databaseBuilder(getActivity().getApplicationContext(),
                    AppDatabase.class, "wood-id").build();
            List<InferencesLog> logs = db.inferencesLogDAO().getAll();
            getActivity().runOnUiThread(()-> {
                logList.setAdapter(new InferenceLogViewAdapter(getActivity(), logs, this));
                logList.setLayoutManager(new LinearLayoutManager(getActivity()));
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();

            ModelHelper modelHelper = ModelHelper.getHelperInstance(getActivity());
            try {
                ModelHelper.Result result = modelHelper.runInference(uri.getPath());
                AppDatabase db = Room.databaseBuilder(getActivity().getApplicationContext(),
                        AppDatabase.class, "wood-id").build();
                InferencesLog log = InferencesLog.fromResult(result);
                log.imagePath = uri.getPath();
                saveLog(db, log);
                refresh();
                Toast.makeText(getActivity(), "Image is Likely " + result.getClassIndex() + ": " + result.getClassLabel() + " score: " + result.getScore(), Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else if (resultCode == ImagePicker.RESULT_ERROR) {
            Toast.makeText(getActivity(), ImagePicker.getError(data), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getActivity(), "Task Cancelled", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveLog(AppDatabase db, InferencesLog log) {
        executor.execute(() -> {
            db.inferencesLogDAO().insertAll(log);
            List<InferencesLog> logs = db.inferencesLogDAO().getAll();
            getActivity().runOnUiThread(()-> {
                logList.setAdapter(new InferenceLogViewAdapter(getActivity(), logs, this));
                logList.setLayoutManager(new LinearLayoutManager(getActivity()));
            });
        });
    }

    @Override
    public void onDeleteItem(InferenceLogViewAdapter inferenceLogViewAdapter, int position, InferencesLog loginfo) {
        executor.execute(() -> {
            AppDatabase db = Room.databaseBuilder(getActivity().getApplicationContext(),
                    AppDatabase.class, "wood-id").build();
            db.inferencesLogDAO().delete(loginfo);
            getActivity().runOnUiThread(()-> {
                inferenceLogViewAdapter.notifyItemRemoved(position);
            });
        });
    }
}