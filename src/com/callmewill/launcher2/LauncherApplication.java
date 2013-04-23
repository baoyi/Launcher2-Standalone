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

package com.callmewill.launcher2;

import android.app.Application;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.Handler;

import com.callmewill.launcher2.R;
import com.callmewill.launcher2.cache.IconCache;
import com.callmewill.launcher2.provider.LauncherProvider;
import com.callmewill.launcher2.provider.LauncherSettings;
import com.callmewill.launcher2.receiver.LauncherModel;

import java.lang.ref.WeakReference;

public class LauncherApplication extends Application {
	public LauncherModel mModel;
	public IconCache mIconCache;
	private static boolean sIsScreenLarge;
	private static float sScreenDensity;
	private static int sLongPressTimeout = 300;
	private static final String sSharedPreferencesKey = "com.callmewill.launcher2.prefs";
	WeakReference<LauncherProvider> mLauncherProvider;

	@Override
	public void onCreate() {
		super.onCreate();

		// 在创建icon cache之前，我们需要判断屏幕的大小和屏幕的像素密度，以便创建合适大小的icon
		// set sIsScreenXLarge and sScreenDensity *before* creating icon cache
		// 判断屏幕大小
		sIsScreenLarge = getResources().getBoolean(R.bool.is_large_screen);
		// 获得屏幕分辨率
		sScreenDensity = getResources().getDisplayMetrics().density;

		mIconCache = new IconCache(this);// 来设置了应用程序的图标的cache
		/*
		 * LauncherModel主要用于加载桌面的图标、插件和文件夹，
		 * 同时LaucherModel是一个广播接收器，在程序包发生改变、区域、或者配置文件发生改变时，
		 * 都会发送广播给LaucherModel，LaucherModel会根据不同的广播来做相应加载操作， 此部分会在后面做详细介绍。
		 */
		mModel = new LauncherModel(this, mIconCache);

		// Register intent receivers 注册广播
		IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);// 应用添加
		filter.addAction(Intent.ACTION_PACKAGE_REMOVED);// 应用删除
		filter.addAction(Intent.ACTION_PACKAGE_CHANGED);// 应用被改变
		filter.addDataScheme("package");// 隐式事件
		registerReceiver(mModel, filter);
		filter = new IntentFilter();
		filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);// 提示应用安装在手机内部
		filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);// 安装在SDCard
		filter.addAction(Intent.ACTION_LOCALE_CHANGED);// 当系统语言发生改变时
		filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);// 当系统语言发生改变时
		registerReceiver(mModel, filter);
		filter = new IntentFilter();
		filter.addAction(SearchManager.INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED);// 搜索管理相关的变换
		registerReceiver(mModel, filter);
		filter = new IntentFilter();
		filter.addAction(SearchManager.INTENT_ACTION_SEARCHABLES_CHANGED);// 自身定义的消息
		registerReceiver(mModel, filter);

		// Register for changes to the favorites
		// 注册ContentObserver，监听LauncherSettings.Favorites.CONTENT_URI数据的变化
		/*
		 * 技术点 如何检测数据库的变化？
		 * 
		 * ContentObserver正可用于这项工作，它对你所感兴趣的URI（数据库地址）进行检测，在重写的onChange函数中进行处理（
		 * 最多用于更新UI）。
		 * 
		 * 当对数据库进行增删查等操作后，可以根据我们自己设定的flag来判断是否需要通知Observer来对我们做出的修改发生响应（
		 * 这里的响应不是指数据库的具体操作响应
		 * ，而很可能是UI上的表现），如果需要则调用getContext().getContentResolver
		 * ().notifyChange(uri, null);
		 * 
		 * 这里的ContentResolver为应用程序提供了访问数据库模型的实例。
		 * 
		 * 注册Observer的操作看代码：
		 * 
		 * ContentResolver resolver = getContentResolver();
		 * //获取“当前”数据库实例，Provider中 定义了它的操作如insert, delete, update...
		 * resolver.registerContentObserver
		 * (LauncherSettings.Favorites.CONTENT_URI,true, mObserver);
		 */

		ContentResolver resolver = getContentResolver();
		resolver.registerContentObserver(
				LauncherSettings.Favorites.CONTENT_URI, true,
				mFavoritesObserver);
	}

	/**
	 * There's no guarantee that this function is ever called.
	 */
	@Override
	public void onTerminate() {
		super.onTerminate();

		unregisterReceiver(mModel);

		ContentResolver resolver = getContentResolver();
		resolver.unregisterContentObserver(mFavoritesObserver);
	}

	/**
	 * Receives notifications whenever the user favorites have changed.
	 */
	private final ContentObserver mFavoritesObserver = new ContentObserver(
			new Handler()) {
		@Override
		public void onChange(boolean selfChange) {
			// If the database has ever changed, then we really need to force a
			// reload of the
			// workspace on the next load
			mModel.resetLoadedState(false, true);
			mModel.startLoaderFromBackground();
		}
	};

	LauncherModel setLauncher(Launcher launcher) {
		mModel.initialize(launcher);
		return mModel;
	}

	public IconCache getIconCache() {
		return mIconCache;
	}

	public LauncherModel getModel() {
		return mModel;
	}

	public void setLauncherProvider(LauncherProvider provider) {
		mLauncherProvider = new WeakReference<LauncherProvider>(provider);
	}

	public LauncherProvider getLauncherProvider() {
		return mLauncherProvider.get();
	}

	public static String getSharedPreferencesKey() {
		return sSharedPreferencesKey;
	}

	public static boolean isScreenLarge() {
		return sIsScreenLarge;
	}

	public static boolean isScreenLandscape(Context context) {
		return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
	}

	public static float getScreenDensity() {
		return sScreenDensity;
	}

	public static int getLongPressTimeout() {
		return sLongPressTimeout;
	}
}
