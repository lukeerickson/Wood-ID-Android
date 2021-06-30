package org.fao.mobile.woodidentifier.utils;

public class Species {
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

    public Species(String className, String scientificName) {
        this.className = className;
        this.scientificName = scientificName;
        this.referenceImages = new String[0];
    }

    public void setReferenceImages(String[] referenceImages) {
        this.referenceImages = referenceImages;
    }
}
