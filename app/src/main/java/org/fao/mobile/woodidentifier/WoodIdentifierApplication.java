package org.fao.mobile.woodidentifier;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.fao.mobile.woodidentifier.utils.ModelHelper;
import org.fao.mobile.woodidentifier.utils.SpeciesLookupService;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class WoodIdentifierApplication extends Application {
    public SpeciesLookupService getSpeciesLookupService() {
        return speciesLookupService;
    }

    private SpeciesLookupService speciesLookupService;
    Executor executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        this.speciesLookupService = new SpeciesLookupService(this);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        executor.execute(() -> {
            ModelHelper.setupDefaultModel(this, prefs);
        });
    }
}
