package org.fao.mobile.woodidentifier.callbacks;

import org.fao.mobile.woodidentifier.models.InferencesLog;

public interface DBCallback {
    void onDone(InferencesLog log);
}
