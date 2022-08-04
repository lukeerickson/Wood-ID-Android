Wood Identification Mobile Application (Android)
================================================

This mobile application provides a mobile platform for the identifying of wood species using
the built in mobile camera with the Xylophone Camera attachment. A machine learning model
is used to identify the wood species using AI.

Machine learning model is based on:

https://github.com/fpl-xylotron/frontiers-in-plant-science-2020a

Minimum Software/Hardware Requirements
====================

Windows or Linux operating systems that support Android Studio

Below are the requirements:

Android Studio system requirements
64-bit Microsoft® Windows® 8/10/11.
x86_64 CPU architecture; 2nd generation Intel Core or newer, or AMD CPU with support for a Windows Hypervisor.
8 GB RAM or more.
8 GB of available disk space minimum (IDE + Android SDK + Android Emulator)
1280 x 800 minimum screen resolution.

Installing and Building
=======================

Source code can be build using Android Studio (Please refer to the Android Studio User Guides) additional
the PhilWood ID application will require model files to be included in the app/src/main/assets folder
named "model.zip". The "model.zip" file needs to be in the following structure:

/model.zip
    /reference
        |- <species name 1>
        |    \ image1.png
        L- <species name 2>
             \ image2.png
    labels.txt
    model.json
    model.txt
    model.pt
    species_database.json

Note that this zip file structure is built by the Machine Learning Python script that
can be found here:

https://github.com/jedld/fips-wood-id-model/tree/master/model

This zip file contains the Pytorch "Mobile" model as well as various information about
the supported species and "reference" images that will be shown in the detail page.

Building and Training the Pytorch model is beyond the scope of this README however the important thing to
note is that the PyTorch version used in generating the model must be the same as the one
defined in build.gradle, differences can causes crashes.

    
