package org.fao.mobile.woodidentifier.utils;

public class Species {
    public String[] otherNames;
    String className;

    public String getClassName() {
        return className;
    }

    public String getScientificName() {
        return scientificName;
    }

    public String getDescription() {
        return description;
    }

    public String[] getReferenceImages() {
        return referenceImages;
    }

    String scientificName;
    String description;
    private String[] referenceImages;

    public Species() {
    }

    public String name() {
        if (otherNames != null && otherNames.length > 0) {
            return otherNames[0];
        }

        return scientificName;
    }

    public Species(String className, String scientificName) {
        this.className = className;
        this.scientificName = scientificName;
        this.referenceImages = new String[0];
    }

    public void setReferenceImages(String[] referenceImages) {
        this.referenceImages = referenceImages;
    }
}
