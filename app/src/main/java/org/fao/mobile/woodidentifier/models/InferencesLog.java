package org.fao.mobile.woodidentifier.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;
import androidx.room.util.StringUtil;

import org.fao.mobile.woodidentifier.utils.ModelHelper;
import org.fao.mobile.woodidentifier.utils.StringUtils;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity(tableName = "inferences_log")
@TypeConverters({org.fao.mobile.woodidentifier.models.converters.TypeConverters.class})
public class InferencesLog {
    @PrimaryKey(autoGenerate = true)
    public long uid;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "classIndex")
    public int classIndex;

    @ColumnInfo(name = "classLabel")
    public String classLabel;

    @ColumnInfo(name = "score")
    public float score;

    @ColumnInfo(name = "imagePath")
    public String imagePath;

    @ColumnInfo(name = "originalFilename")
    public String originalFilename;

    @ColumnInfo(name = "top")
    public String[] top;

    @ColumnInfo(name = "scores")
    public Double[] scores;

    @ColumnInfo(name = "top_k_raw")
    public Integer[] topKRaw;

    @ColumnInfo(name = "longitude")
    public double longitude;

    @ColumnInfo(name = "latitude")
    public double latitude;

    @ColumnInfo(name = "location_accuracy")
    public float locationAccuracy;

    @ColumnInfo(name = "mislabeled")
    public boolean mislabeled;

    @ColumnInfo(name = "expectedLabel")
    public String expectedLabel;

    public static InferencesLog fromResult(ModelHelper.Result result, ModelHelper helper) {
        InferencesLog inferencesLog = new InferencesLog();
        inferencesLog.timestamp = System.currentTimeMillis();
        inferencesLog.classIndex = result.getClassIndex();
        inferencesLog.classLabel = result.getClassLabel();
        inferencesLog.expectedLabel = result.getClassLabel();
        inferencesLog.score = result.getScore();
        inferencesLog.scores = result.getScores();
        inferencesLog.topKRaw = result.getTop();

        List<String> classLabels;
        if (helper !=null) {
            classLabels = helper.getClassLabels();
        } else {
            classLabels = new ArrayList<>();
        }
        List<String> topK = Arrays.stream(result.getTop()).flatMap(x -> Stream.of(classLabels.get(x.intValue()))).collect(Collectors.toList());
        inferencesLog.top = topK.toArray(new String[0]);
        return inferencesLog;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLocationAccuracy(float locationAccuracy) {
        this.locationAccuracy = locationAccuracy;
    }

    public float getLocationAccuracy() {
        return locationAccuracy;
    }

    public void updateResult(ModelHelper.Result result, ModelHelper helper) {
        this.timestamp = System.currentTimeMillis();
        this.classIndex = result.getClassIndex();
        this.classLabel = result.getClassLabel();
        this.score = result.getScore();
        this.scores = result.getScores();
        this.topKRaw = result.getTop();

        List<String> classLabels;
        if (helper !=null) {
            classLabels = helper.getClassLabels();
        } else {
            classLabels = new ArrayList<>();
        }
        List<String> topK = Arrays.stream(result.getTop()).flatMap(x -> Stream.of(classLabels.get(x.intValue()))).collect(Collectors.toList());
        this.top = topK.toArray(new String[0]);
    }
}
