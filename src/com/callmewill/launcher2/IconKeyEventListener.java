package com.callmewill.launcher2;

import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;

public class IconKeyEventListener implements OnKeyListener {
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return FocusHelper.handleIconKeyEvent(v, keyCode, event);
    }
}
