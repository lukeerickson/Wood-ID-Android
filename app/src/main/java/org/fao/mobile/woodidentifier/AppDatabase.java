package org.fao.mobile.woodidentifier;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import org.fao.mobile.woodidentifier.dao.InferencesLogDAO;
import org.fao.mobile.woodidentifier.models.InferencesLog;

@Database(entities = {InferencesLog.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract InferencesLogDAO inferencesLogDAO();
}