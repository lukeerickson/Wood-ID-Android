package org.fao.mobile.woodidentifier.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;

import org.fao.mobile.woodidentifier.models.InferencesLog;

import java.util.List;

@Dao
public interface InferencesLogDAO {
    @Query("SELECT * FROM inferences_log ORDER BY timestamp DESC")
    List<InferencesLog> getAll();

    @Query("SELECT * FROM inferences_log WHERE uid IN (:userIds)")
    List<InferencesLog> loadAllByIds(int[] userIds);

    @Query("SELECT * FROM inferences_log WHERE uid=:uid")
    InferencesLog findByUid(int uid);

    @Insert
    void insertAll(InferencesLog... logs);

    @Delete
    void delete(InferencesLog log);

    @Query("SELECT COUNT(*) from inferences_log")
    int count();

    @Query("DELETE FROM inferences_log")
    void deleteAll();
}
