package org.fao.mobile.woodidentifier;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import org.fao.mobile.woodidentifier.dao.InferencesLogDAO;
import org.fao.mobile.woodidentifier.dao.ModelVersionsDAO;
import org.fao.mobile.woodidentifier.models.InferencesLog;
import org.fao.mobile.woodidentifier.models.ModelVersion;

@Database(entities = {InferencesLog.class, ModelVersion.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract InferencesLogDAO inferencesLogDAO();
    public abstract ModelVersionsDAO modelVersionsDAO();
}