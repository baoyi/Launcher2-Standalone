/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.callmewill.launcher2.entity;

import com.callmewill.launcher2.provider.LauncherSettings;
import com.callmewill.launcher2.provider.LauncherSettings.Favorites;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Parcelable;

/**
 * We pass this object with a drag from the customization tray
 */
public class PendingAddItemInfo extends ItemInfo {
    /**
     * The component that will be created.
     */
    ComponentName componentName;
}

