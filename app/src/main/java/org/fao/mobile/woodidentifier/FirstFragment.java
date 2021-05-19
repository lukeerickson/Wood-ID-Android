package org.fao.mobile.woodidentifier;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import org.fao.mobile.woodidentifier.adapters.InferenceLogViewAdapter;
import org.fao.mobile.woodidentifier.databinding.FragmentFirstBinding;
import org.fao.mobile.woodidentifier.models.InferencesLog;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private Executor executor = Executors.newSingleThreadExecutor();

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView logList = (RecyclerView) view.findViewById(R.id.inference_log_list);

        executor.execute(() -> {
            AppDatabase db = Room.databaseBuilder(getActivity().getApplicationContext(),
                    AppDatabase.class, "wood-id").build();
            List<InferencesLog> logs = db.inferencesLogDAO().getAll();
            getActivity().runOnUiThread(()-> {
                logList.setAdapter(new InferenceLogViewAdapter(logs));
                logList.setLayoutManager(new LinearLayoutManager(getActivity()));
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}