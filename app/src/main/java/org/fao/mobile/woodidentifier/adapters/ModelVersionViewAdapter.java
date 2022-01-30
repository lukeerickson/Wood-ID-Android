package org.fao.mobile.woodidentifier.adapters;

import static org.fao.mobile.woodidentifier.utils.ModelHelper.MODEL_NAME;
import static org.fao.mobile.woodidentifier.utils.ModelHelper.MODEL_PATH;
import static org.fao.mobile.woodidentifier.utils.ModelHelper.MODEL_VERSION;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import org.fao.mobile.woodidentifier.AppDatabase;
import org.fao.mobile.woodidentifier.R;
import org.fao.mobile.woodidentifier.models.ModelVersion;
import org.fao.mobile.woodidentifier.utils.ModelHelper;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ModelVersionViewAdapter extends RecyclerView.Adapter<ModelVersionViewAdapter.ViewHolder> implements View.OnClickListener {
    private final Activity context;
    private final List<ModelVersion> versionList;
    private final ItemListener listener;
    Executor executor = Executors.newSingleThreadExecutor();

    public ModelVersionViewAdapter(Activity context, List<ModelVersion> versionList, ItemListener listener) {
        this.context = context;
        this.versionList = versionList;
        this.listener = listener;
    }

    public interface ItemListener {
        void onDeleteItem(ModelVersionViewAdapter modelVersionViewAdapter, int position, ModelVersion loginfo);

        void refreshItems();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.activateButton) {
            executor.execute(()-> {
                ModelVersion modelVersion = (ModelVersion)v.getTag();
                AppDatabase db = Room.databaseBuilder(context.getApplicationContext(),
                        AppDatabase.class, "wood-id").build();
                modelVersion.active = true;
                db.modelVersionsDAO().deactivateAll();
                db.modelVersionsDAO().update(modelVersion);
                ModelHelper.activateModel(context, modelVersion);
                context.runOnUiThread(()->{
                    listener.refreshItems();
                });
            });
        }
    }

    @NonNull
    @Override
    public ModelVersionViewAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.model_version_view, viewGroup, false);
        return new ModelVersionViewAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ModelVersionViewAdapter.ViewHolder holder, int position) {
        ModelVersion modelVersion = versionList.get(position);
        View itemView = holder.getView();

        TextView version = itemView.findViewById(R.id.version);
        TextView name = itemView.findViewById(R.id.name);
        version.setText(Long.toString(modelVersion.version));
        holder.setName(modelVersion.name);
        holder.getActivateButton().setTag(modelVersion);
        holder.getActivateButton().setOnClickListener(this);
        holder.setActive(modelVersion.active);
        if (modelVersion.active) {
            holder.getView().setBackgroundColor(context.getResources().getColor(R.color.un_blue, context.getTheme()));
        } else {
            holder.getView().setBackgroundColor(context.getResources().getColor(R.color.gray_400, context.getTheme()));
        }
    }

    @Override
    public int getItemCount() {
        return versionList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final View view;
        private final TextView name;
        private final Button activebutton;

        public View getView() {
            return view;
        }

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.view = itemView;
            this.name = itemView.findViewById(R.id.name);
            this.activebutton = itemView.findViewById(R.id.activateButton);
        }

        public void setName(String name) {
            this.name.setText(name);
        }

        public Button getActivateButton() {
            return activebutton;
        }

        public void setActive(boolean active) {
            if (active) {
                activebutton.setVisibility(View.GONE);
            } else {
                activebutton.setVisibility(View.VISIBLE);
            }
        }
    }
}
