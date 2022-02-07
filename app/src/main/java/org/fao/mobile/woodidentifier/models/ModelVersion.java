package org.fao.mobile.woodidentifier.models;

import android.content.Context;
import android.util.Log;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Room;

import org.fao.mobile.woodidentifier.AppDatabase;
import org.fao.mobile.woodidentifier.utils.ModelHelper;

import java.io.File;

@Entity(tableName = "model_versions")
public class ModelVersion {
    private static final String TAG = ModelVersion.class.getCanonicalName();
    @PrimaryKey(autoGenerate = true)
    public int id;
    @ColumnInfo(name = "version")
    public long version;
    @ColumnInfo(name = "name")
    public String name;
    @ColumnInfo(name = "description")
    public String description;
    @ColumnInfo(name = "path")
    public String path;
    @ColumnInfo(name = "active", defaultValue = "false")
    public boolean active;
    @ColumnInfo(name = "timestamp")
    public long timestamp;

    public void performCleanup() {
        Log.i(TAG, "cleaning up model directory");
        File dir = new File(path);
        dir.delete();
    }

    public void activateModel(Context context) {
        AppDatabase db = Room.databaseBuilder(context.getApplicationContext(),
                AppDatabase.class, "wood-id").build();
        active = true;
        db.modelVersionsDAO().deactivateAll();
        db.modelVersionsDAO().update(this);
        ModelHelper.activateModel(context, this);
    }
}
