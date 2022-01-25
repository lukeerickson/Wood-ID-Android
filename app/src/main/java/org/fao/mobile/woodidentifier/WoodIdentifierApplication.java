package org.fao.mobile.woodidentifier;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.fao.mobile.woodidentifier.utils.ModelHelper;
import org.fao.mobile.woodidentifier.utils.SpeciesLookupService;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class WoodIdentifierApplication extends Application {
    private long fromDateContext = 0L;
    private long toDateContext = Long.MAX_VALUE;

    public SpeciesLookupService getSpeciesLookupService() {
        return speciesLookupService;
    }

    private SpeciesLookupService speciesLookupService;
    Executor executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        executor.execute(() -> {
            ModelHelper.setupDefaultModel(this, prefs);
            this.speciesLookupService = new SpeciesLookupService(this);
        });
    }

    public void setFromDateContext(long fromDateContext) {
        this.fromDateContext = fromDateContext;
    }

    public long getFromDateContext() {
        return fromDateContext;
    }

    public void setToDateContext(long toDateContext) {
        this.toDateContext = toDateContext;
    }

    public long getToDateContext() {
        return toDateContext;
    }
}
