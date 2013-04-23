package com.callmewill.launcher2;

import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;

/**
 * A keyboard listener we set on all the workspace icons.
 */
public class FolderKeyEventListener implements View.OnKeyListener {
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return FocusHelper.handleFolderKeyEvent(v, keyCode, event);
    }
}
