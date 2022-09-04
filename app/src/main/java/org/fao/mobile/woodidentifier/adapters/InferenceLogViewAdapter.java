package org.fao.mobile.woodidentifier.adapters;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.fao.mobile.woodidentifier.DetailsActivity;
import org.fao.mobile.woodidentifier.R;
import org.fao.mobile.woodidentifier.WoodIdentifierApplication;
import org.fao.mobile.woodidentifier.models.InferencesLog;
import org.fao.mobile.woodidentifier.utils.ModelHelper;
import org.fao.mobile.woodidentifier.utils.SharedPrefsUtil;
import org.fao.mobile.woodidentifier.utils.Species;
import org.fao.mobile.woodidentifier.utils.SpeciesLookupService;
import org.fao.mobile.woodidentifier.utils.Utils;

import java.io.File;
import java.text.DecimalFormat;
import java.util.List;

public class InferenceLogViewAdapter extends RecyclerView.Adapter<InferenceLogViewAdapter.ViewHolder> implements View.OnClickListener {

    private static final int OPEN_DETAIL = 5;
    private final ItemListener itemListener;
    private final SpeciesLookupService speciesLookup;
    private final double margin;
    private final FragmentActivity activity;


    public interface ItemListener {
        void onDeleteItem(InferenceLogViewAdapter inferenceLogViewAdapter, int position, InferencesLog loginfo);
    }

    private final List<InferencesLog> logs;
    private final Fragment context;
    private ImageView woodImage;

    public InferenceLogViewAdapter(Fragment context, List<InferencesLog> logs, ItemListener listener) {
        this.context = context;
        this.activity = context.getActivity();
        this.logs = logs;
        this.itemListener = listener;
        this.speciesLookup = ((WoodIdentifierApplication) context.getContext().getApplicationContext()).getSpeciesLookupService();
        this.margin = SharedPrefsUtil.getUncertaintyMargin(context.getContext());
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
        Intent intent = new Intent(context.getContext(), DetailsActivity.class);
        InferencesLog inferencesLog = (InferencesLog) view.getTag();
        intent.putExtra("uid", inferencesLog.uid);
        context.startActivityForResult(intent, OPEN_DETAIL);
    }

    @Override
    public void onBindViewHolder(InferenceLogViewAdapter.ViewHolder holder, int position) {
        InferencesLog inferenceLog = logs.get(position);
        if (position == 0) {
            holder.getView().setBackgroundColor(context.getResources().getColor(R.color.red, context.getContext().getTheme()));
        } else {
            holder.getView().setBackgroundColor(context.getResources().getColor(R.color.teal_700, context.getContext().getTheme()));
        }
        holder.getView().setTag(inferenceLog);
        holder.getDeleteButton().setTag(inferenceLog);
        holder.deleteButton.setOnClickListener(this);
        if (inferenceLog.classLabel == null) {
            holder.getTextView().setText(context.getResources().getText(R.string.identification_in_progress));
            holder.getScoreView().setText("NA");
        } else {
            String label = inferenceLog.classLabel;
            int index = inferenceLog.classIndex;

            if (inferenceLog.expectedLabel!=null && !inferenceLog.expectedLabel.equals(inferenceLog.classLabel)) {
                label = inferenceLog.expectedLabel;
                index = ModelHelper.getHelperInstance(activity).getClassLabels().indexOf(label);
            }

            if (inferenceLog.score < SharedPrefsUtil.accuracyThreshold(activity)) {
                label = "Unknown";
            }
            DecimalFormat df = new DecimalFormat("###.##");
            holder.getTextView().setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            if (SharedPrefsUtil.isDeveloperMode(activity)) {
                if (inferenceLog.confidenceScore() >= getLowerBound() && inferenceLog.confidenceScore() <= getHigherBound()) {
                    String label2 = inferenceLog.top[1];
                    holder.getTextView().setText(label + " / " + label2);
                    holder.getTextView().setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    holder.getScoreView().setText(df.format(inferenceLog.score) + "/" + df.format(inferenceLog.scores[inferenceLog.topKRaw[1]]));
                } else {
                    holder.getTextView().setText(label);
                    holder.getScoreView().setText(df.format(inferenceLog.score));
                }
            } else {

                Species species = speciesLookup.lookupSpeciesInfo(label);
                if (inferenceLog.confidenceScore() >= 45 && inferenceLog.confidenceScore() <= 55) {
                    String label2 = inferenceLog.top[1];
                    Species species2 = speciesLookup.lookupSpeciesInfo(label2);
                    holder.getTextView().setText(species.name() + " or " + species2.name());
                    holder.getScoreView().setText(df.format(inferenceLog.confidenceScore()) + "%");
                } else {
                    holder.getTextView().setText(species.name());

                    holder.getScoreView().setText(df.format(inferenceLog.confidenceScore()) + "%");
                }
            }

        }

        if (inferenceLog.expectedLabel != null &&  !inferenceLog.expectedLabel.equals(inferenceLog.classLabel)) {
            holder.getTextView().setTextColor(activity.getColor(R.color.red));
        } else {
            holder.getTextView().setTextColor(activity.getColor(R.color.black));
        }

        holder.getFilename().setText(inferenceLog.originalFilename);
        holder.getTimestamp().setText(Utils.timestampToString(inferenceLog.timestamp));
        Glide.with(context).load(Uri.decode(inferenceLog.imagePath)).into(holder.getWoodImage());
    }

    private double getHigherBound() {
        return 50 + margin;
    }

    private double getLowerBound() {
        return 50 - margin;
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.deleteLogItem) {
            InferencesLog log = (InferencesLog) v.getTag();
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(activity);
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
