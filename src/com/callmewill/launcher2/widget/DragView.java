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

package com.callmewill.launcher2.widget;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.callmewill.launcher2.Launcher;
import com.callmewill.launcher2.R;
import com.callmewill.launcher2.R.dimen;
import com.callmewill.launcher2.utils.LauncherAnimUtils;

/**
 * 拖动的View
 * @author Administrator
 */
public class DragView extends View {
	/** 拖动的透明度  */
	private static float sDragAlpha = 1f;

	private Bitmap mBitmap;
	private Bitmap mCrossFadeBitmap;
	private Paint mPaint;
	private int mRegistrationX;
	private int mRegistrationY;

	private Point mDragVisualizeOffset = null;
	private Rect mDragRegion = null;
	private DragLayer mDragLayer = null;
	private boolean mHasDrawn = false;
	private float mCrossFadeProgress = 0f;

	ValueAnimator mAnim;
	private float mOffsetX = 0.0f;
	private float mOffsetY = 0.0f;
	private float mInitialScale = 1f;

	/**
	 * Construct the drag view.构建拖动视图
	 * <p>
	 * The registration point is the point inside our view that the touch events
	 * should be centered upon.
	 * 
	 * @param launcher
	 *            The Launcher instance
	 * @param bitmap
	 *            The view that we're dragging around. We scale it up when we
	 *            draw it.
	 * @param registrationX
	 *            The x coordinate of the registration point.
	 * @param registrationY
	 *            The y coordinate of the registration point.
	 */
	public DragView(Launcher launcher, Bitmap bitmap, int registrationX,
			int registrationY, int left, int top, int width, int height,
			final float initialScale) {
		super(launcher);
		mDragLayer = launcher.getDragLayer();// 得到根布局
		mInitialScale = initialScale;// 初始缩放比例

		final Resources res = getResources();
		// 得到当前屏幕下的偏移数值
		final float offsetX = res
				.getDimensionPixelSize(R.dimen.dragViewOffsetX);
		final float offsetY = res
				.getDimensionPixelSize(R.dimen.dragViewOffsetY);
		final float scaleDps = res.getDimensionPixelSize(R.dimen.dragViewScale);
		final float scale = (width + scaleDps) / width;

		// Set the initial scale to avoid any jumps
		// 设置初始规模，以避免任何跳跃操作
		setScaleX(initialScale);// 设置X轴缩放
		setScaleY(initialScale);// 设置Y轴缩放

		// Animate the view into the correct position
		// 设置动画视图到正确的位置
		/*
		 * ValueAnimator 类通过设定动画过程中的int、float或颜色值，来指定动画播放期间的某些类型的动画值。
		 */
		mAnim = LauncherAnimUtils.ofFloat(0.0f, 1.0f);
		mAnim.setDuration(150);

		// 通过监听这个事件在属性的值更新时执行相应的操作
		mAnim.addUpdateListener(new AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator animation) {
				// 这个部分会使图标放大
				final float value = (Float) animation.getAnimatedValue();

				final int deltaX = (int) ((value * offsetX) - mOffsetX);
				final int deltaY = (int) ((value * offsetY) - mOffsetY);

				mOffsetX += deltaX;
				mOffsetY += deltaY;
				setScaleX(initialScale + (value * (scale - initialScale)));
				setScaleY(initialScale + (value * (scale - initialScale)));
				if (sDragAlpha != 1f) {
					setAlpha(sDragAlpha * value + (1f - value));
				}

				if (getParent() == null) {
					animation.cancel();
				} else {
					setTranslationX(getTranslationX() + deltaX);
					setTranslationY(getTranslationY() + deltaY);
				}
			}
		});

		mBitmap = Bitmap.createBitmap(bitmap, left, top, width, height);
		setDragRegion(new Rect(0, 0, width, height));

		// The point in our scaled bitmap that the touch events are located
		// 这个点位于触摸事件中缩放位图的里面。
		mRegistrationX = registrationX;
		mRegistrationY = registrationY;

		// Force a measure, because Workspace uses getMeasuredHeight() before
		// the layout pass
		// 强制计算大小,因为在layout通过之前workspace使用了getMeasuredHeight()

		/*
		 *　一个MeasureSpec封装了父布局传递给子布局的布局要求，每个MeasureSpec代表了一组宽度和高度的要求。
		 * 一个MeasureSpec由大小和模式组成
		 * 。它有三种模式：UNSPECIFIED(未指定),父元素不对子元素施加任何束缚，子元素可以得到任意想要的大小
		 * ；EXACTLY(完全)，父元素决定自元素的确切大小
		 * ，子元素将被限定在给定的边界里而忽略它本身大小；AT_MOST(至多)，子元素至多达到指定大小的值。 　　它常用的三个函数：
		 *　　1.static int getMode(int measureSpec):根据提供的测量值(格式)提取模式(上述三个模式之一)
		 * 2.static int getSize(int
		 * measureSpec):根据提供的测量值(格式)提取大小值(这个大小也就是我们通常所说的大小) 　　3.static int
		 * makeMeasureSpec(int size,int mode):根据提供的大小值和模式创建一个测量值(格式)
		 * 这个类的使用呢，通常在view组件的onMeasure方法里面调用但也有少数例外
		 */

		int ms = View.MeasureSpec.makeMeasureSpec(0,
				View.MeasureSpec.UNSPECIFIED);
		measure(ms, ms);
		mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
	}

	public float getOffsetY() {
		return mOffsetY;
	}

	public int getDragRegionLeft() {
		return mDragRegion.left;
	}

	public int getDragRegionTop() {
		return mDragRegion.top;
	}

	public int getDragRegionWidth() {
		return mDragRegion.width();
	}

	public int getDragRegionHeight() {
		return mDragRegion.height();
	}

	public void setDragVisualizeOffset(Point p) {
		mDragVisualizeOffset = p;
	}

	public Point getDragVisualizeOffset() {
		return mDragVisualizeOffset;
	}

	public void setDragRegion(Rect r) {
		mDragRegion = r;
	}

	public Rect getDragRegion() {
		return mDragRegion;
	}

	public float getInitialScale() {
		return mInitialScale;
	}

	public void updateInitialScaleToCurrentScale() {
		mInitialScale = getScaleX();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(mBitmap.getWidth(), mBitmap.getHeight());
	}

	@Override
	protected void onDraw(Canvas canvas) {
		@SuppressWarnings("all")
		// suppress dead code warning
		final boolean debug = false;
		if (debug) {
			Paint p = new Paint();
			p.setStyle(Paint.Style.FILL);
			p.setColor(0x66ffffff);
			canvas.drawRect(0, 0, getWidth(), getHeight(), p);
		}

		mHasDrawn = true;
		boolean crossFade = mCrossFadeProgress > 0 && mCrossFadeBitmap != null;
		if (crossFade) {
			int alpha = crossFade ? (int) (255 * (1 - mCrossFadeProgress))
					: 255;
			mPaint.setAlpha(alpha);
		}
		canvas.drawBitmap(mBitmap, 0.0f, 0.0f, mPaint);
		if (crossFade) {
			mPaint.setAlpha((int) (255 * mCrossFadeProgress));
			canvas.save();
			float sX = (mBitmap.getWidth() * 1.0f)
					/ mCrossFadeBitmap.getWidth();
			float sY = (mBitmap.getHeight() * 1.0f)
					/ mCrossFadeBitmap.getHeight();
			canvas.scale(sX, sY);
			canvas.drawBitmap(mCrossFadeBitmap, 0.0f, 0.0f, mPaint);
			canvas.restore();
		}
	}

	public void setCrossFadeBitmap(Bitmap crossFadeBitmap) {
		mCrossFadeBitmap = crossFadeBitmap;
	}

	public void crossFade(int duration) {
		ValueAnimator va = LauncherAnimUtils.ofFloat(0f, 1f);
		va.setDuration(duration);
		va.setInterpolator(new DecelerateInterpolator(1.5f));
		va.addUpdateListener(new AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator animation) {
				mCrossFadeProgress = animation.getAnimatedFraction();
			}
		});
		va.start();
	}

	public void setColor(int color) {
		if (mPaint == null) {
			mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
		}
		if (color != 0) {
			mPaint.setColorFilter(new PorterDuffColorFilter(color,
					PorterDuff.Mode.SRC_ATOP));
		} else {
			mPaint.setColorFilter(null);
		}
		invalidate();
	}

	public boolean hasDrawn() {
		return mHasDrawn;
	}

	@Override
	public void setAlpha(float alpha) {
		super.setAlpha(alpha);
		mPaint.setAlpha((int) (255 * alpha));
		invalidate();
	}

	/**
	 * Create a window containing this view and show it.
	 * 
	 * @param windowToken
	 *            obtained from v.getWindowToken() from one of your views
	 * @param touchX
	 *            the x coordinate the user touched in DragLayer coordinates
	 * @param touchY
	 *            the y coordinate the user touched in DragLayer coordinates
	 */
	public void show(int touchX, int touchY) {
		mDragLayer.addView(this);

		// Start the pick-up animation
		DragLayer.LayoutParams lp = new DragLayer.LayoutParams(0, 0);
		lp.width = mBitmap.getWidth();
		lp.height = mBitmap.getHeight();
		lp.customPosition = true;
		setLayoutParams(lp);
		setTranslationX(touchX - mRegistrationX);
		setTranslationY(touchY - mRegistrationY);
		// Post the animation to skip other expensive work happening on the
		// first frame
		post(new Runnable() {
			public void run() {
				mAnim.start();
			}
		});
	}

	public void cancelAnimation() {
		if (mAnim != null && mAnim.isRunning()) {
			mAnim.cancel();
		}
	}

	public void resetLayoutParams() {
		mOffsetX = mOffsetY = 0;
		requestLayout();
	}

	/**
	 * Move the window containing this view.
	 * 
	 * @param touchX
	 *            the x coordinate the user touched in DragLayer coordinates
	 * @param touchY
	 *            the y coordinate the user touched in DragLayer coordinates
	 */
	public void move(int touchX, int touchY) {
		setTranslationX(touchX - mRegistrationX + (int) mOffsetX);
		setTranslationY(touchY - mRegistrationY + (int) mOffsetY);
	}

	public void remove() {
		if (getParent() != null) {
			mDragLayer.removeView(DragView.this);
		}
	}
}
