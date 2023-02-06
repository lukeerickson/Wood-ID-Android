package org.fao.mobile.woodidentifier;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DetailsActivity extends AppCompatActivity {
    private Executor executor = Executors.newSingleThreadExecutor();
    private ImageView sampleImageView;
    private TextView filename;
    private TextView classLabel1, classLabel2, classLabel3, classLabel4;
    private Button closeButton, button1, button2, button3, button4;
    private WoodIdentifierApplication application;
    private TextView description;
    //private ViewGroup topKcontainer;
    //private ViewGroup referenceImageContainer;
    //private Spinner labelSpinner;
    private ArrayAdapter<String> classes;
    //private View topKLabel;
    //private TextView captureDateTime;
    private EditText commentField;
    //private TextView modelVersion;
    //private TextView location;
    private boolean correctionApplied = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
        Intent intent = getIntent();
        // id of #1 wood species
        long uid = intent.getLongExtra("uid", -1);
        String label1 = intent.getStringExtra("label1");
        String label2 = intent.getStringExtra("label2");
        String label3 = intent.getStringExtra("label3");
        //long score1 = intent.getLongExtra("score1", -1);
        //long uid2 = intent.getLongExtra("uid2", -1);
        this.sampleImageView = findViewById(R.id.imageSample);
        this.filename = findViewById(R.id.label);
        //this.captureDateTime = findViewById(R.id.capture_datetime);
        this.classLabel1 = findViewById(R.id.class_label1);
        this.classLabel2 = findViewById(R.id.class_label2);
        this.classLabel3 = findViewById(R.id.class_label3);
        this.classLabel4 = findViewById(R.id.class_label4);
        this.closeButton = findViewById(R.id.close_button);
        this.button1 = findViewById(R.id.button1);
        this.button2 = findViewById(R.id.button2);
        this.button3 = findViewById(R.id.button3);
        this.button4 = findViewById(R.id.button4);
        //this.description = findViewById(R.id.description);
        //this.topKcontainer = findViewById(R.id.topKcontainer);
        //this.location = findViewById(R.id.location);
        //this.labelSpinner = findViewById(R.id.mislabled_picker);
        //this.modelVersion = findViewById(R.id.modelVersion);
        //this.referenceImageContainer = findViewById(R.id.reference_images_container);
        //this.topKLabel = findViewById(R.id.topk_label);
        //this.commentField = (EditText) findViewById(R.id.commentField);
        this.application = (WoodIdentifierApplication) getApplication();
        AppDatabase db = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "wood-id").build();
        closeButton.setOnClickListener((v) -> {
            executor.execute(() -> {
                //InferencesLog inferenceLog = db.inferencesLogDAO().findByUid(uid);
                //inferenceLog.setComment(commentField.getText().toString());
                //db.inferencesLogDAO().update(inferenceLog);
                runOnUiThread(() -> {
                    Intent resultIntent = new Intent();

                    if (correctionApplied) {
                        setResult(1, resultIntent);
                    } else {
                        setResult(Activity.RESULT_OK, resultIntent);
                    }
                    finish();
                });
            });
        });
        //if (!SharedPrefsUtil.isDeveloperMode(this)) {
            //topKcontainer.setVisibility(View.GONE);
            //labelSpinner.setVisibility(View.GONE);
            //topKLabel.setVisibility(View.GONE);
        //}
        ModelHelper model = ModelHelper.getHelperInstance(this);
        classes = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, model.getClassLabels().toArray(new String[0]));

        /*labelSpinner.setAdapter(classes);
        labelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {


            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                correctionApplied = true;
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

         */
        executor.execute(() -> {
            // display name of species
            InferencesLog inferenceLog = db.inferencesLogDAO().findByUid(uid);
            //InferencesLog inferenceLog2 = db.inferencesLogDAO().findByUid(uid2);
            //Log.i("DetailsActivity", "UID: " + uid);
            //Log.i("DetailsActivity", "UID2: " + uid2);
            runOnUiThread(() -> {
                //Species species1 = application.getSpeciesLookupService().lookupSpeciesInfo(inferenceLog1.classLabel);
                //Species species2 = application.getSpeciesLookupService().lookupSpeciesInfo(inferenceLog2.classLabel);

                // set names of class labels
                // this code will eventually be put into a method
                if (inferenceLog.score < SharedPrefsUtil.accuracyThreshold(this)) {
                    classLabel1.setText(getString(R.string.unknown));
                    classLabel1.setTextColor(getColor(R.color.red));
                    classLabel2.setText("");
                    classLabel3.setText("");
                    classLabel4.setText("");
                    button1.setVisibility(View.INVISIBLE);
                } else {
                    //classLabel1.setText(species1.name() + " ( " + species1.getScientificName() + ") ");
                    DecimalFormat df = new DecimalFormat("###.##");
                    Double[] confidenceScores = inferenceLog.confidenceScores();
                    classLabel1.setText(label1 + ": " + df.format(confidenceScores[0]) + "%");
                    classLabel1.setTextColor(getColor(R.color.black));

                    ViewGroup.LayoutParams params = button1.getLayoutParams();
                    params.width = (int) (confidenceScores[0] * button1.getWidth() / 100);
                    button1.setLayoutParams(params);

                    /*
                    Log.i("Banana", "Confidence Scores[0]: " + confidenceScores[0].intValue());

                    Log.i("Banana", "Button Width: " + button1.getWidth());
                    Log.i("Banana", "Width: " + params.width);
                    */
                    //button1.setWidth((confidenceScores[0]).intValue() * button1.getWidth() / 100);

                    //Log.i("DetailsActivity", "Ego");
                    classLabel2.setText(label2 + ": " + df.format(confidenceScores[1]) + "%");
                    classLabel2.setTextColor(getColor(R.color.black));

                    params = button2.getLayoutParams();
                    params.width = (int) (confidenceScores[1] * button2.getWidth() / 100);
                    button2.setLayoutParams(params);

                    classLabel3.setText(label3 + ": " + df.format(confidenceScores[2]) + "%");
                    classLabel3.setTextColor(getColor(R.color.black));

                    params = button3.getLayoutParams();
                    params.width = (int) (confidenceScores[2] * button3.getWidth() / 100);
                    button2.setLayoutParams(params);

                    classLabel4.setText("other");
                    classLabel4.setTextColor(getColor(R.color.black));
                }
                //filename.setText(inferenceLog1.originalFilename);
                //description.setText(species.getDescription());
                //labelSpinner.setSelection(model.getClassLabels().indexOf(inferenceLog.expectedLabel));
                //captureDateTime.setText(Utils.timestampToString(inferenceLog.timestamp));
                //commentField.setText(inferenceLog.getComment());
                //location.setText(inferenceLog.getLocationName());
                //referenceImageContainer.removeAllViews();
                //modelVersion.setText(inferenceLog.modelName + "-" + Long.toString(inferenceLog.modelVersion));
                //Arrays.stream(species.getReferenceImages()).forEachOrdered(imageRef -> {

                    //View view = LayoutInflater.from(referenceImageContainer.getContext())
                            //.inflate(R.layout.reference_image, referenceImageContainer, false);
                    //ImageView referenceImage = view.findViewById(R.id.reference_image);
                    //Glide.with(DetailsActivity.this).load(imageRef).into(referenceImage);
                    //referenceImageContainer.addView(view);
                //});
                //populateTopK(topKcontainer, inferenceLog.top, inferenceLog.scores, inferenceLog.topKRaw);
                Glide.with(DetailsActivity.this).load(Uri.decode(inferenceLog.imagePath)).into(sampleImageView);
            });
        });
    }
/*
    private void populateTopK(ViewGroup viewGroup, String[] names, Double[] scores, Integer[] topKRaw) {
        topKcontainer.removeAllViews();

        StreamUtils.zipWithIndex(Arrays.stream(names)).forEachOrdered(namesWithIndex -> {
            Double scoreValue = scores[topKRaw[(int) namesWithIndex.getIndex()].intValue()];
            View view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.topk_item, viewGroup, false);
            TextView classLabel = view.findViewById(R.id.class_label1);
            TextView scoreLabel = view.findViewById(R.id.score_value);
            classLabel.setText(namesWithIndex.getValue());
            scoreLabel.setText(String.format("%.4g%n", scoreValue));
            //topKcontainer.addView(view);
        });
    }
*/
    @Override
    public void onBackPressed() {
        Intent resultIntent = new Intent();
        if (correctionApplied) {
            setResult(1, resultIntent);
        } else {
            setResult(Activity.RESULT_OK, resultIntent);
        }
        finish();
    }
}