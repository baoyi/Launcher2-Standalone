package com.callmewill.launcher2.entity;

import android.content.pm.ActivityInfo;

public class PendingAddShortcutInfo  extends PendingAddItemInfo {

    ActivityInfo shortcutActivityInfo;

    public PendingAddShortcutInfo(ActivityInfo activityInfo) {
        shortcutActivityInfo = activityInfo;
    }

    @Override
    public String toString() {
        return "Shortcut: " + shortcutActivityInfo.packageName;
    }
}