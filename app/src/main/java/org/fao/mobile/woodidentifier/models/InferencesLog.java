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

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.stream.Collectors;

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
    public Float[] scores;

    public static InferencesLog fromResult(ModelHelper.Result result, List<String> classLabels) {
        InferencesLog inferencesLog = new InferencesLog();
        inferencesLog.timestamp = System.currentTimeMillis();
        inferencesLog.classIndex = result.getClassIndex();
        inferencesLog.classLabel = result.getClassLabel();
        inferencesLog.score = result.getScore();
        inferencesLog.scores = result.getScores();
        List<String> topK = Arrays.stream(result.getTop()).mapToObj(x -> classLabels.get(x)).collect(Collectors.toList());
        inferencesLog.top = topK.toArray(new String[0]);
        return inferencesLog;
    }


}
