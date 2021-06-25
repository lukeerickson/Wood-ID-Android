package org.fao.mobile.woodidentifier;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import com.bumptech.glide.Glide;

import org.fao.mobile.woodidentifier.models.InferencesLog;
import org.fao.mobile.woodidentifier.utils.Species;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DetailsActivity extends AppCompatActivity {
    private Executor executor = Executors.newSingleThreadExecutor();
    private ImageView sampleImageView;
    private TextView filename;
    private TextView classLabel;
    private Button closeButton;
    private WoodIdentifierApplication application;
    private TextView description;
    private ViewGroup topKcontainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
        Intent intent = getIntent();
        long uid = intent.getLongExtra("uid", -1);
        this.sampleImageView = findViewById(R.id.imageSample);
        this.filename = findViewById(R.id.label);
        this.classLabel = findViewById(R.id.class_label);
        this.closeButton = findViewById(R.id.close_button);
        this.description = findViewById(R.id.description);
        this.topKcontainer = findViewById(R.id.topKcontainer);
        this.application = (WoodIdentifierApplication) getApplication();
        closeButton.setOnClickListener((v) -> {
            finish();
        });

        executor.execute(() -> {
            AppDatabase db = Room.databaseBuilder(getApplicationContext(),
                    AppDatabase.class, "wood-id").build();
            InferencesLog inferenceLog = db.inferencesLogDAO().findByUid(uid);
            runOnUiThread(() -> {
                Species species = application.getSpeciesLookupService().lookupSpeciesInfo(inferenceLog.classLabel);
                classLabel.setText(species.getScientificName() + "(" + inferenceLog.classLabel + ")");
                filename.setText(inferenceLog.originalFilename);
                description.setText(species.getDescription());
                populateTopK(topKcontainer, inferenceLog.top);
                Glide.with(DetailsActivity.this).load(Uri.decode(inferenceLog.imagePath)).into(sampleImageView);
            });
        });
    }

    private void populateTopK(ViewGroup viewGroup, String[] score) {
        topKcontainer.removeAllViews();
        Arrays.stream(score).forEach(name -> {
            View view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.topk_item, viewGroup, false);
            TextView classLabel = view.findViewById(R.id.class_label);
            classLabel.setText(name);
            topKcontainer.addView(view);
        });
    }
}