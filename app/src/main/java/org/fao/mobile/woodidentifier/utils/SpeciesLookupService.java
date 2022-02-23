package org.fao.mobile.woodidentifier.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


//Routines for looking up species information
public class SpeciesLookupService {

    private final String modelRootPath; //contains the root path of the currently selected Model
    private JSONObject jObject;

    public SpeciesLookupService(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String modelPath = prefs.getString(ModelHelper.MODEL_PATH, null);
        assert modelPath != null;
        this.modelRootPath = modelPath;
        try {
            this.jObject = ModelHelper.getSpeciesDatabase(context, modelPath);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    public Species lookupSpeciesInfo(String classLabel) {
        try {
            classLabel = classLabel.toLowerCase().replace(' ', '_');
            JSONObject speciesInfo = jObject.getJSONObject(classLabel);
            Species species = new Species();
            species.scientificName = speciesInfo.getString("scientific_name");
            JSONArray otherNames = speciesInfo.getJSONArray("other_names");
            ArrayList<String> commonNames = new ArrayList<>();
            for (int i = 0; i < otherNames.length(); i++) {
                commonNames.add(otherNames.getString(i));
            }
            species.otherNames = commonNames.toArray(new String[0]);
            if (speciesInfo.has("description")) {
                species.description = speciesInfo.getString("description");
            } else {
                species.description = "";
            }
            if (speciesInfo.has("reference_images")) {
                JSONArray referenceImages = speciesInfo.getJSONArray("reference_images");
                String[] referenceImageArr = new String[referenceImages.length()];
                for (int i = 0; i < referenceImages.length(); i++)
                    referenceImageArr[i] = "file://" + modelRootPath + "/" + referenceImages.getString(i);
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
