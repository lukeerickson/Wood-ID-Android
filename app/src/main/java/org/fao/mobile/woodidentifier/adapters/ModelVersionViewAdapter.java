package org.fao.mobile.woodidentifier.adapters;

import static org.fao.mobile.woodidentifier.utils.ModelHelper.MODEL_NAME;
import static org.fao.mobile.woodidentifier.utils.ModelHelper.MODEL_PATH;
import static org.fao.mobile.woodidentifier.utils.ModelHelper.MODEL_VERSION;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import org.fao.mobile.woodidentifier.AppDatabase;
import org.fao.mobile.woodidentifier.R;
import org.fao.mobile.woodidentifier.WoodIdentifierApplication;
import org.fao.mobile.woodidentifier.models.InferencesLog;
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
                modelVersion.activateModel(context);
                ((WoodIdentifierApplication) context.getApplication()).getSpeciesLookupService().refresh(context);
                context.runOnUiThread(()->{
                    listener.refreshItems();
                });
            });
        } else
            if (v.getId() == R.id.deleteModelButton) {
                ModelVersion modelVersion = (ModelVersion) v.getTag();
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
                alertDialog.setMessage(R.string.confirm_delete);
                alertDialog.setPositiveButton(R.string.yes, (dialog, which) -> {
                    listener.onDeleteItem(this, versionList.indexOf(modelVersion), modelVersion);
                    versionList.remove(modelVersion);
                    executor.execute(()-> {
                        modelVersion.performCleanup();
                    });
                });
                alertDialog.setNegativeButton(R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                });
                alertDialog.show();
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
        holder.getDeleteButton().setOnClickListener(this);
        holder.getDeleteButton().setTag(modelVersion);
        holder.setDescription(modelVersion.description);
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
        private final AppCompatImageButton deleteButton;
        private final TextView description;
        private final View activeText;

        public View getView() {
            return view;
        }

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.view = itemView;
            this.name = itemView.findViewById(R.id.name);
            this.description = itemView.findViewById(R.id.description);
            this.activebutton = itemView.findViewById(R.id.activateButton);
            this.deleteButton = itemView.findViewById(R.id.deleteModelButton);
            this.activeText = itemView.findViewById(R.id.activeText);
        }

        public void setName(String name) {
            this.name.setText(name);
        }

        public Button getActivateButton() {
            return activebutton;
        }

        public AppCompatImageButton getDeleteButton() { return deleteButton; }

        public void setDescription(String description) {
            this.description.setText(description);
        }

        public void setActive(boolean active) {
            if (active) {
                activebutton.setVisibility(View.GONE);
                deleteButton.setVisibility(View.GONE);
                activeText.setVisibility(View.VISIBLE);
            } else {
                activebutton.setVisibility(View.VISIBLE);
                deleteButton.setVisibility(View.VISIBLE);
                activeText.setVisibility(View.GONE);
            }
        }
    }
}
