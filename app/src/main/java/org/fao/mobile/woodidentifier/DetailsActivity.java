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
import com.codepoetics.protonpack.StreamUtils;

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
    private ViewGroup referenceImageContainer;

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
        this.referenceImageContainer = findViewById(R.id.reference_images_container);
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
                referenceImageContainer.removeAllViews();
                Arrays.stream(species.getReferenceImages()).forEachOrdered(imageRef -> {
                    View view = LayoutInflater.from(referenceImageContainer.getContext())
                            .inflate(R.layout.reference_image, referenceImageContainer, false);
                    ImageView referenceImage = view.findViewById(R.id.reference_image);
                    Glide.with(DetailsActivity.this).load(imageRef).into(referenceImage);
                    referenceImageContainer.addView(view);
                });
                populateTopK(topKcontainer, inferenceLog.top, inferenceLog.scores, inferenceLog.topKRaw);
                Glide.with(DetailsActivity.this).load(Uri.decode(inferenceLog.imagePath)).into(sampleImageView);
            });
        });
    }

    private void populateTopK(ViewGroup viewGroup, String[] names, Double[] scores, Integer[] topKRaw) {
        topKcontainer.removeAllViews();

        StreamUtils.zipWithIndex(Arrays.stream(names)).forEachOrdered(namesWithIndex -> {
            Double scoreValue = scores[topKRaw[(int) namesWithIndex.getIndex()].intValue()];
            View view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.topk_item, viewGroup, false);
            TextView classLabel = view.findViewById(R.id.class_label);
            TextView scoreLabel = view.findViewById(R.id.score_value);
            classLabel.setText(namesWithIndex.getValue());
            scoreLabel.setText(String.format("%.4g%n", scoreValue));
            topKcontainer.addView(view);
        });
    }
}