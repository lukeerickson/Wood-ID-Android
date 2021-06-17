package org.fao.mobile.woodidentifier.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import org.fao.mobile.woodidentifier.utils.ModelHelper;

import java.util.Date;
import java.util.UUID;

@Entity(tableName = "inferences_log")
public class InferencesLog {
    @PrimaryKey(autoGenerate = true)
    public int uid;

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

    public static InferencesLog fromResult(ModelHelper.Result result) {
        InferencesLog inferencesLog = new InferencesLog();
        inferencesLog.timestamp = System.currentTimeMillis();
        inferencesLog.classIndex = result.getClassIndex();
        inferencesLog.classLabel = result.getClassLabel();
        inferencesLog.score = result.getScore();
        return inferencesLog;
    }
}
