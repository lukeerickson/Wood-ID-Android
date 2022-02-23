package org.fao.mobile.woodidentifier;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import com.bumptech.glide.Glide;
import com.codepoetics.protonpack.StreamUtils;

import org.fao.mobile.woodidentifier.models.InferencesLog;
import org.fao.mobile.woodidentifier.utils.ModelHelper;
import org.fao.mobile.woodidentifier.utils.SharedPrefsUtil;
import org.fao.mobile.woodidentifier.utils.Species;
import org.fao.mobile.woodidentifier.utils.Utils;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
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
    private Spinner labelSpinner;
    private ArrayAdapter<String> classes;
    private View topKLabel;
    private TextView captureDateTime;
    private EditText commentField;
    private TextView modelVersion;
    private TextView location;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
        Intent intent = getIntent();
        long uid = intent.getLongExtra("uid", -1);
        this.sampleImageView = findViewById(R.id.imageSample);
        this.filename = findViewById(R.id.label);
        this.captureDateTime = findViewById(R.id.capture_datetime);
        this.classLabel = findViewById(R.id.class_label);
        this.closeButton = findViewById(R.id.close_button);
        this.description = findViewById(R.id.description);
        this.topKcontainer = findViewById(R.id.topKcontainer);
        this.location = findViewById(R.id.location);
        this.labelSpinner = findViewById(R.id.mislabled_picker);
        this.modelVersion = findViewById(R.id.modelVersion);
        this.referenceImageContainer = findViewById(R.id.reference_images_container);
        this.topKLabel = findViewById(R.id.topk_label);
        this.commentField = (EditText)findViewById(R.id.commentField);
        this.application = (WoodIdentifierApplication) getApplication();
        AppDatabase db = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "wood-id").build();
        closeButton.setOnClickListener((v) -> {
            executor.execute(()-> {
                InferencesLog inferenceLog = db.inferencesLogDAO().findByUid(uid);
                inferenceLog.setComment(commentField.getText().toString());
                db.inferencesLogDAO().update(inferenceLog);
                runOnUiThread(()-> {
                    finish();
                });
            });
        });
        if (!SharedPrefsUtil.isDeveloperMode(this)) {
            topKcontainer.setVisibility(View.GONE);
            labelSpinner.setVisibility(View.GONE);
            topKLabel.setVisibility(View.GONE);
        }
        ModelHelper model = ModelHelper.getHelperInstance(this);
        classes = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, model.getClassLabels().toArray(new String[0]));

        labelSpinner.setAdapter(classes);
        labelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                executor.execute(() -> {
                    AppDatabase db = Room.databaseBuilder(getApplicationContext(),
                            AppDatabase.class, "wood-id").build();
                    InferencesLog inferenceLog = db.inferencesLogDAO().findByUid(uid);
                    inferenceLog.expectedLabel = model.getClassLabels().get(position);
                    db.inferencesLogDAO().update(inferenceLog);
                });
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        executor.execute(() -> {
            InferencesLog inferenceLog = db.inferencesLogDAO().findByUid(uid);
            runOnUiThread(() -> {
                Species species = application.getSpeciesLookupService().lookupSpeciesInfo(inferenceLog.classLabel);
                if (inferenceLog.score < SharedPrefsUtil.accuracyThreshold(this)) {
                    classLabel.setText(getString(R.string.unknown));
                    classLabel.setTextColor(getColor(R.color.red));
                } else {
                    classLabel.setText(species.name() + " ( " + species.getScientificName() + ") ");
                    classLabel.setTextColor(getColor(R.color.black));
                }
                filename.setText(inferenceLog.originalFilename);
                description.setText(species.getDescription());
                labelSpinner.setSelection(model.getClassLabels().indexOf(inferenceLog.expectedLabel));
                captureDateTime.setText(Utils.timestampToString(inferenceLog.timestamp));
                commentField.setText(inferenceLog.getComment());
                location.setText(inferenceLog.getLocationName());
                referenceImageContainer.removeAllViews();
                modelVersion.setText(inferenceLog.modelName + "-" + Long.toString(inferenceLog.modelVersion));
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