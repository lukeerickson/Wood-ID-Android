package org.fao.mobile.woodidentifier;

import android.app.Application;

import org.fao.mobile.woodidentifier.utils.SpeciesLookupService;

public class WoodIdentifierApplication extends Application {
    public SpeciesLookupService getSpeciesLookupService() {
        return speciesLookupService;
    }

    private SpeciesLookupService speciesLookupService;

    @Override
    public void onCreate() {
        super.onCreate();
        this.speciesLookupService = new SpeciesLookupService(this);
    }
}
