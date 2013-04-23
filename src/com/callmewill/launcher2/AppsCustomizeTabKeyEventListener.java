package com.callmewill.launcher2;

import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;

/**
 * A keyboard listener we set on the last tab button in AppsCustomize to jump to then
 * market icon and vice versa.
 */
public class AppsCustomizeTabKeyEventListener implements View.OnKeyListener {
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return FocusHelper.handleAppsCustomizeTabKeyEvent(v, keyCode, event);
    }
}
