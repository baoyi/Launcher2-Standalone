/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.callmewill.launcher2.cache;

import java.util.HashMap;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import com.callmewill.launcher2.LauncherApplication;
import com.callmewill.launcher2.entity.ApplicationInfo;
import com.callmewill.launcher2.receiver.LauncherModel;
import com.callmewill.launcher2.utils.Utilities;

/**
 * Cache of application icons.  Icons can be made from any thread.
 * 缓存应用程序的图标，图标可以从任意线程中获取
 */
public class IconCache {
    @SuppressWarnings("unused")
    private static final String TAG = "Launcher.IconCache";

    private static final int INITIAL_ICON_CACHE_CAPACITY = 50;//缓存图标初始容量

    private static class CacheEntry {
        public Bitmap icon;
        public String title;
    }

    private final Bitmap mDefaultIcon;
    private final LauncherApplication mContext;
    private final PackageManager mPackageManager;
    private final HashMap<ComponentName, CacheEntry> mCache =
            new HashMap<ComponentName, CacheEntry>(INITIAL_ICON_CACHE_CAPACITY);
    private int mIconDpi;

    public IconCache(LauncherApplication context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        mContext = context;
        mPackageManager = context.getPackageManager();
        mIconDpi = activityManager.getLauncherLargeIconDensity();//获得首选的图标大小的密度

        // need to set mIconDpi before getting default icon
        mDefaultIcon = makeDefaultIcon();//设置默认图片
    }
    
    /**
     * 获得所有activity默认图标
     * @return
     */
    public Drawable getFullResDefaultActivityIcon() {
        return getFullResIcon(Resources.getSystem(),
                android.R.mipmap.sym_def_app_icon);
    }

    /**
     * 根据资源id获得图标
     * @param resources
     * @param iconId
     * @return
     */
    public Drawable getFullResIcon(Resources resources, int iconId) {
        Drawable d;
        try {
            d = resources.getDrawableForDensity(iconId, mIconDpi);
        } catch (Resources.NotFoundException e) {
            d = null;
        }

        return (d != null) ? d : getFullResDefaultActivityIcon();
    }

    /**
     * 根据包名和合适的密度获得图片
     * @param packageName
     * @param iconId
     * @return
     */
    public Drawable getFullResIcon(String packageName, int iconId) {
        Resources resources;
        try {
            resources = mPackageManager.getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            resources = null;
        }
        if (resources != null) {
            if (iconId != 0) {
                return getFullResIcon(resources, iconId);
            }
        }
        return getFullResDefaultActivityIcon();
    }

    /**
     * 根据ResolveInfo获得图标
     * @param info
     * @return
     */
    public Drawable getFullResIcon(ResolveInfo info) {
        return getFullResIcon(info.activityInfo);
    }

    /**
     * 根据ActivityInfo获得图片
     * @param info
     * @return
     */
    public Drawable getFullResIcon(ActivityInfo info) {

        Resources resources;
        try {
            resources = mPackageManager.getResourcesForApplication(
                    info.applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            resources = null;
        }
        if (resources != null) {
            int iconId = info.getIconResource();
            if (iconId != 0) {
                return getFullResIcon(resources, iconId);
            }
        }
        return getFullResDefaultActivityIcon();
    }

    /**
     * 创建默认icon
     * @return
     */
    private Bitmap makeDefaultIcon() {
        Drawable d = getFullResDefaultActivityIcon();
        Bitmap b = Bitmap.createBitmap(Math.max(d.getIntrinsicWidth(), 1),
                Math.max(d.getIntrinsicHeight(), 1),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, b.getWidth(), b.getHeight());
        d.draw(c);
        c.setBitmap(null);
        return b;
    }

    /**
     * Remove any records for the supplied ComponentName.
     * 根据ComponetName删除任意图片缓存
     */
    public void remove(ComponentName componentName) {
        synchronized (mCache) {
            mCache.remove(componentName);
        }
    }

    /**
     * Empty out the cache.
     * 清楚图片缓存
     */
    public void flush() {
        synchronized (mCache) {
            mCache.clear();
        }
    }

    /**
     * Fill in "application" with the icon and label for "info."
     * 从info和集合中获取application的label和icon
     */
    public void getTitleAndIcon(ApplicationInfo application, ResolveInfo info,
            HashMap<Object, CharSequence> labelCache) {
        synchronized (mCache) {
        	//创建一个图标缓存
            CacheEntry entry = cacheLocked(application.componentName, info, labelCache);
            //赋值程序标题
            application.title = entry.title;
            //赋值程序图标
            application.iconBitmap = entry.icon;
        }
    }

    /**
     * 从intent中获得程序图标 如果无法获得component则返回默认图标
     * @param intent
     * @return
     */
    public Bitmap getIcon(Intent intent) {
        synchronized (mCache) {
            final ResolveInfo resolveInfo = mPackageManager.resolveActivity(intent, 0);
            ComponentName component = intent.getComponent();

            if (resolveInfo == null || component == null) {
                return mDefaultIcon;
            }

            CacheEntry entry = cacheLocked(component, resolveInfo, null);
            return entry.icon;
        }
    }

    /**
     * 通过Component,resolveinfo，标签缓存获得图片
     * @param component
     * @param resolveInfo
     * @param labelCache
     * @return
     */
    public Bitmap getIcon(ComponentName component, ResolveInfo resolveInfo,
            HashMap<Object, CharSequence> labelCache) {
        synchronized (mCache) {
            if (resolveInfo == null || component == null) {
                return null;
            }

            CacheEntry entry = cacheLocked(component, resolveInfo, labelCache);
            return entry.icon;
        }
    }

    public boolean isDefaultIcon(Bitmap icon) {
        return mDefaultIcon == icon;
    }

    /**
     * 创建一个图标缓存
     * @param componentName
     * @param info
     * @param labelCache
     * @return
     */
    private CacheEntry cacheLocked(ComponentName componentName, ResolveInfo info,
            HashMap<Object, CharSequence> labelCache) {
    	//通过componentName在集合中获取图标缓存
        CacheEntry entry = mCache.get(componentName);
        //如果缓存为空，则创建缓存
        if (entry == null) {
            entry = new CacheEntry();

            mCache.put(componentName, entry);
            //获得ComponentName 用它作为Key
            ComponentName key = LauncherModel.getComponentNameFromResolveInfo(info);
            //如果标签缓存不为空，并且集合中有这个键。则从集合中获得应用的标题
            if (labelCache != null && labelCache.containsKey(key)) {
                entry.title = labelCache.get(key).toString();
            } else {
            	//否则 通过通过ResolveInfo获得程序名，作为标题
                entry.title = info.loadLabel(mPackageManager).toString();
                //如果标签集合不为空则将新的标签放入集合
                if (labelCache != null) {
                    labelCache.put(key, entry.title);
                }
            }
            //如果标题为空
            if (entry.title == null) {
            	//则从info中获得标题
                entry.title = info.activityInfo.name;
            }
            //为entry获得图标
            entry.icon = Utilities.createIconBitmap(
                    getFullResIcon(info), mContext);
        }
        return entry;
    }

    /**
     * 从所有缓存图标
     * @return
     */
    public HashMap<ComponentName,Bitmap> getAllIcons() {
        synchronized (mCache) {
            HashMap<ComponentName,Bitmap> set = new HashMap<ComponentName,Bitmap>();
            for (ComponentName cn : mCache.keySet()) {
                final CacheEntry e = mCache.get(cn);
                set.put(cn, e.icon);
            }
            return set;
        }
    }
}
