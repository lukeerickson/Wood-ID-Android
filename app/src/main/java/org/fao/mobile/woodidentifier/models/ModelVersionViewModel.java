package org.fao.mobile.woodidentifier.models;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class ModelVersionViewModel extends ViewModel {
    private final MutableLiveData<Integer> selected = new MutableLiveData<Integer>();

    public void updateCount(Integer count) {
        selected.setValue(count);
    }

    public LiveData<Integer> getCount() {
        return selected;
    }
}

