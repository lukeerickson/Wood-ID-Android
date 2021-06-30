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
            if (speciesInfo.has("reference_images")) {
                JSONArray referenceImages = speciesInfo.getJSONArray("reference_images");
                String[] referenceImageArr = new String[referenceImages.length()];
                for (int i = 0; i < referenceImages.length(); i++)
                    referenceImageArr[i] = referenceImages.getString(i);
                species.setReferenceImages(referenceImageArr);
            } else {
                species.setReferenceImages(new String[0]);
            }
            return species;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new Species(classLabel, classLabel);
    }
}
