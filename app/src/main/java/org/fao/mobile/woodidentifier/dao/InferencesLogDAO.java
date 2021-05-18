package org.fao.mobile.woodidentifier.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import org.fao.mobile.woodidentifier.models.InferencesLog;

import java.util.List;

@Dao
public interface InferencesLogDAO {
    @Query("SELECT * FROM inferences_log")
    List<InferencesLog> getAll();

    @Query("SELECT * FROM inferences_log WHERE uid IN (:userIds)")
    List<InferencesLog> loadAllByIds(int[] userIds);

    @Query("SELECT * FROM inferences_log WHERE uid=:uid")
    InferencesLog findByUid(String uid);

    @Insert
    void insertAll(InferencesLog... logs);

    @Delete
    void delete(InferencesLog log);
}
