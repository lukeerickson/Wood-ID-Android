package org.fao.mobile.woodidentifier.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "model_versions")
public class ModelVersion {
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
}
