package org.fao.mobile.woodidentifier.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.fao.mobile.woodidentifier.DetailsActivity;
import org.fao.mobile.woodidentifier.R;
import org.fao.mobile.woodidentifier.models.InferencesLog;
import org.fao.mobile.woodidentifier.utils.Utils;

import java.io.File;
import java.util.List;

public class InferenceLogViewAdapter extends RecyclerView.Adapter<InferenceLogViewAdapter.ViewHolder> implements View.OnClickListener {

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
        view.setOnClickListener(this::onClickMain);
        return new ViewHolder(view);
    }

    private void onClickMain(View view) {
        Intent intent = new Intent(context, DetailsActivity.class);
        InferencesLog inferencesLog = (InferencesLog) view.getTag();
        intent.putExtra("uid", inferencesLog.uid);
        context.startActivity(intent);
    }

    @Override
    public void onBindViewHolder(InferenceLogViewAdapter.ViewHolder holder, int position) {
        InferencesLog inferenceLog = logs.get(position);
        holder.getView().setTag(inferenceLog);
        holder.getDeleteButton().setTag(inferenceLog);
        holder.deleteButton.setOnClickListener(this);
        if (inferenceLog.classLabel == null) {
            holder.getTextView().setText(context.getResources().getText(R.string.identification_in_progress));
            holder.getScoreView().setText("NA");
        } else {
            holder.getTextView().setText(inferenceLog.classLabel + " (" + inferenceLog.classIndex + ")");
            holder.getScoreView().setText(Float.toString(inferenceLog.score));
        }

        holder.getFilename().setText(inferenceLog.originalFilename);
        holder.getTimestamp().setText(Utils.timestampToString(inferenceLog.timestamp));
        Glide.with(context).load(Uri.decode(inferenceLog.imagePath)).into(holder.getWoodImage());
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.deleteLogItem) {
            InferencesLog log = (InferencesLog) v.getTag();
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
            alertDialog.setMessage(R.string.confirm_delete);
            alertDialog.setPositiveButton(R.string.yes, (dialog, which) -> {
                itemListener.onDeleteItem(this, logs.indexOf(log), log);
                logs.remove(log);
            });
            alertDialog.setNegativeButton(R.string.cancel, (dialog, which) -> {
                dialog.dismiss();
            });
            alertDialog.show();
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

        public TextView getTimestamp() {
            return timestamp;
        }

        private final TextView timestamp;

        public View getView() {
            return view;
        }

        private final View view;

        public ImageButton getDeleteButton() {
            return deleteButton;
        }

        private final ImageButton deleteButton;

        public ViewHolder(View view) {
            super(view);
            this.view = view;
            // Define click listener for the ViewHolder's View
            filename = (TextView) view.findViewById(R.id.filename);
            textView = (TextView) view.findViewById(R.id.class_label);
            scoreView = (TextView) view.findViewById(R.id.score);
            woodImage = (ImageView) view.findViewById(R.id.wood_specimen_image);
            deleteButton = (ImageButton) view.findViewById(R.id.deleteLogItem);
            timestamp = (TextView) view.findViewById(R.id.timestamp);
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
