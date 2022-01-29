package org.fao.mobile.woodidentifier.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import org.fao.mobile.woodidentifier.models.InferencesLog;
import org.fao.mobile.woodidentifier.models.ModelVersion;

import java.util.List;

@Dao
public interface ModelVersionsDAO {
    @Query("SELECT * FROM model_versions ORDER BY timestamp DESC")
    List<ModelVersion> getAll();

    @Insert
    long insert(ModelVersion modelVersion);

    @Delete
    void delete(ModelVersion modelVersion);

    @Update
    void update(ModelVersion modelVersion);

    @Query("SELECT * FROM model_versions WHERE active=1 LIMIT 1")
    ModelVersion getActive();

    @Query("SELECT count(*) FROM model_versions")
    int getCount();

    @Query("SELECT * FROM model_versions WHERE name=:name AND version=:version LIMIT 1")
    ModelVersion find(String name, Long version);

    @Query("UPDATE model_versions SET active=0")
    void deactivateAll();
}
