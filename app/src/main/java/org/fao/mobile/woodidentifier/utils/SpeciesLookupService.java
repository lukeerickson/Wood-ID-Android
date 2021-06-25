package org.fao.mobile.woodidentifier.utils;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class SpeciesLookupService {
    private static final String SPECIES_DATABASE = "species_database.json";

    private JSONObject jObject;

    public SpeciesLookupService(Context context) {
        String assetPath = Utils.assetFilePath(context,
                SPECIES_DATABASE);
        assert assetPath != null;
        try {
            this.jObject = new JSONObject(Utils.readFileToStringSimple(new File(assetPath)));
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    public Species lookupSpeciesInfo(String classLabel) {
        try {
            JSONObject speciesInfo = jObject.getJSONObject(classLabel);
            Species species = new Species();
            species.scientificName = speciesInfo.getString("scientific_name");
            species.description = speciesInfo.getString("description");
            return species;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new Species(classLabel, classLabel);
    }
}
