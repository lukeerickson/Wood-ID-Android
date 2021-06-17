package org.fao.mobile.woodidentifier.adapters;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.fao.mobile.woodidentifier.R;
import org.fao.mobile.woodidentifier.models.InferencesLog;

import java.io.File;
import java.util.List;

public class InferenceLogViewAdapter  extends RecyclerView.Adapter<InferenceLogViewAdapter.ViewHolder> implements View.OnClickListener {

    private final ItemListener itemListener;

    public interface ItemListener {
        void onDeleteItem(InferenceLogViewAdapter inferenceLogViewAdapter, int position, InferencesLog loginfo);
    }

    private final List<InferencesLog> logs;
    private final Context context;
    private ImageView woodImage;

    public InferenceLogViewAdapter(Context context, List<InferencesLog> logs, ItemListener listener) {
        this.context = context;
        this.logs = logs;
        this.itemListener = listener;
    }
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.inference_log_item, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(InferenceLogViewAdapter.ViewHolder holder, int position) {
        InferencesLog inferenceLog = logs.get(position);
        holder.deleteButton.setOnClickListener(this);
        holder.deleteButton.setTag(inferenceLog);
        holder.getTextView().setText(inferenceLog.classLabel + " (" + inferenceLog.classIndex + ")");
        holder.getScoreView().setText(Float.toString(inferenceLog.score));
        holder.getFilename().setText(inferenceLog.originalFilename);
        Glide.with(context).load(Uri.decode(inferenceLog.imagePath)).into(holder.getWoodImage());
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.deleteLogItem) {
            InferencesLog log = (InferencesLog)v.getTag();
            itemListener.onDeleteItem(this, logs.indexOf(log), log);
            logs.remove(log);
        }
    }

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;
        private final ImageView woodImage;
        private final TextView scoreView;
        private final TextView filename;

        public ImageButton getDeleteButton() {
            return deleteButton;
        }

        private final ImageButton deleteButton;

        public ViewHolder(View view) {
            super(view);
            // Define click listener for the ViewHolder's View
            filename = (TextView) view.findViewById(R.id.filename);
            textView = (TextView) view.findViewById(R.id.class_label);
            scoreView = (TextView) view.findViewById(R.id.score);
            woodImage = (ImageView)view.findViewById(R.id.wood_specimen_image);
            deleteButton = (ImageButton) view.findViewById(R.id.deleteLogItem);
        }

        public TextView getTextView() {
            return textView;
        }

        public ImageView getWoodImage() {
            return woodImage;
        }

        public TextView getScoreView() {
            return scoreView;
        }

        public TextView getFilename() {
            return filename;
        }
    }

}
