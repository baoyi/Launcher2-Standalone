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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;

import com.callmewill.launcher2.DropTarget;
import com.callmewill.launcher2.InterruptibleInOutAnimator;
import com.callmewill.launcher2.Launcher;
import com.callmewill.launcher2.R;
import com.callmewill.launcher2.DropTarget.DragEnforcer;
import com.callmewill.launcher2.R.color;
import com.callmewill.launcher2.R.dimen;
import com.callmewill.launcher2.R.drawable;
import com.callmewill.launcher2.R.integer;
import com.callmewill.launcher2.R.styleable;
import com.callmewill.launcher2.entity.ItemInfo;
import com.callmewill.launcher2.entity.LauncherAppWidgetInfo;
import com.callmewill.launcher2.entity.PendingAddWidgetInfo;
import com.callmewill.launcher2.receiver.LauncherModel;
import com.callmewill.launcher2.utils.LauncherAnimUtils;
import com.callmewill.launcher2.widget.FolderIcon.FolderRingAnimator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Stack;

public class CellLayout extends ViewGroup {
	static final String TAG = "CellLayout";

	private Launcher mLauncher;
	private int mCellWidth;
	private int mCellHeight;

	/**
	 * 格子宽度数量
	 */
	private int mCountX;
	/**
	 * 格子高度数量
	 */
	private int mCountY;

	private int mOriginalWidthGap;
	private int mOriginalHeightGap;
	private int mWidthGap;
	private int mHeightGap;
	private int mMaxGap;
	private boolean mScrollingTransformsDirty = false;

	private final Rect mRect = new Rect();
	private final CellInfo mCellInfo = new CellInfo();

	// These are temporary variables to prevent having to allocate a new object
	// just to
	// return an (x, y) value from helper functions. Do NOT use them to maintain
	// other state.
	private final int[] mTmpXY = new int[2];
	private final int[] mTmpPoint = new int[2];
	int[] mTempLocation = new int[2];

	boolean[][] mOccupied;
	boolean[][] mTmpOccupied;
	private boolean mLastDownOnOccupiedCell = false;

	private OnTouchListener mInterceptTouchListener;

	private ArrayList<FolderRingAnimator> mFolderOuterRings = new ArrayList<FolderRingAnimator>();
	private int[] mFolderLeaveBehindCell = { -1, -1 };

	private int mForegroundAlpha = 0;
	private float mBackgroundAlpha;
	private float mBackgroundAlphaMultiplier = 1.0f;

	private Drawable mNormalBackground;
	private Drawable mActiveGlowBackground;
	private Drawable mOverScrollForegroundDrawable;
	private Drawable mOverScrollLeft;
	private Drawable mOverScrollRight;
	private Rect mBackgroundRect;
	private Rect mForegroundRect;
	private int mForegroundPadding;

	// If we're actively dragging something over this screen, mIsDragOverlapping
	// is true
	// 如果我们主动的在这个屏幕上拖动东西， mIsDragOverlapping=true
	private boolean mIsDragOverlapping = false;
	private final Point mDragCenter = new Point();

	// These arrays are used to implement the drag visualization on x-large
	// screens.
	// 这些数组用于实现在x-large屏幕上拖动的实现。
	// They are used as circular arrays, indexed by mDragOutlineCurrent.
	// 它们被用于圆形阵列，按mDragOutlineCurrent索引
	private Rect[] mDragOutlines = new Rect[4];
	private float[] mDragOutlineAlphas = new float[mDragOutlines.length];
	private InterruptibleInOutAnimator[] mDragOutlineAnims = new InterruptibleInOutAnimator[mDragOutlines.length];

	// Used as an index into the above 3 arrays; indicates which is the most
	// current value.
	// 用于上面3个数组的索引，这是当前值
	private int mDragOutlineCurrent = 0;
	private final Paint mDragOutlinePaint = new Paint();

	private BubbleTextView mPressedOrFocusedIcon;

	private HashMap<CellLayout.LayoutParams, Animator> mReorderAnimators = new HashMap<CellLayout.LayoutParams, Animator>();
	private HashMap<View, ReorderHintAnimation> mShakeAnimators = new HashMap<View, ReorderHintAnimation>();

	private boolean mItemPlacementDirty = false;

	// When a drag operation is in progress, holds the nearest cell to the touch
	// point
	// 当拖动操作进行时，触摸点保存最近的格子
	private final int[] mDragCell = new int[2];

	private boolean mDragging = false;

	private TimeInterpolator mEaseOutInterpolator;
	private ShortcutAndWidgetContainer mShortcutsAndWidgets;

	private boolean mIsHotseat = false;
	private float mHotseatScale = 1f;

	public static final int MODE_DRAG_OVER = 0;
	public static final int MODE_ON_DROP = 1;
	public static final int MODE_ON_DROP_EXTERNAL = 2;
	public static final int MODE_ACCEPT_DROP = 3;
	private static final boolean DESTRUCTIVE_REORDER = false;
	private static final boolean DEBUG_VISUALIZE_OCCUPIED = false;

	static final int LANDSCAPE = 0;
	static final int PORTRAIT = 1;

	private static final float REORDER_HINT_MAGNITUDE = 0.12f;
	private static final int REORDER_ANIMATION_DURATION = 150;
	private float mReorderHintAnimationMagnitude;

	private ArrayList<View> mIntersectingViews = new ArrayList<View>();
	private Rect mOccupiedRect = new Rect();
	private int[] mDirectionVector = new int[2];
	int[] mPreviousReorderDirection = new int[2];
	private static final int INVALID_DIRECTION = -100;
	private DropTarget.DragEnforcer mDragEnforcer;

	private final static PorterDuffXfermode sAddBlendMode = new PorterDuffXfermode(
			PorterDuff.Mode.ADD);
	private final static Paint sPaint = new Paint();

	public CellLayout(Context context) {
		this(context, null);
	}

	public CellLayout(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public CellLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mDragEnforcer = new DropTarget.DragEnforcer(context);

		// A ViewGroup usually does not draw, but CellLayout needs to draw a
		// rectangle to show
		// the user where a dragged item will land when dropped.

		setWillNotDraw(false);// 如果这个视图没有做任何对自己的绘制，设置这个标志以允许进一步优化。
		mLauncher = (Launcher) context;

		// 从配置文件中获得参数
		TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.CellLayout, defStyle, 0);

		// 获得格子宽高
		mCellWidth = a.getDimensionPixelSize(R.styleable.CellLayout_cellWidth,
				10);
		mCellHeight = a.getDimensionPixelSize(
				R.styleable.CellLayout_cellHeight, 10);
		mWidthGap = mOriginalWidthGap = a.getDimensionPixelSize(
				R.styleable.CellLayout_widthGap, 0);
		mHeightGap = mOriginalHeightGap = a.getDimensionPixelSize(
				R.styleable.CellLayout_heightGap, 0);
		mMaxGap = a.getDimensionPixelSize(R.styleable.CellLayout_maxGap, 0);
		// 得到格子的宽高数量
		mCountX = LauncherModel.getCellCountX();
		mCountY = LauncherModel.getCellCountY();

		mOccupied = new boolean[mCountX][mCountY];
		mTmpOccupied = new boolean[mCountX][mCountY];
		mPreviousReorderDirection[0] = INVALID_DIRECTION;
		mPreviousReorderDirection[1] = INVALID_DIRECTION;

		a.recycle();

		// 关闭绘制缓存
		setAlwaysDrawnWithCacheEnabled(false);

		final Resources res = getResources();
		// 获得热键比例
		mHotseatScale = (res
				.getInteger(R.integer.hotseat_item_scale_percentage) / 100f);

		mNormalBackground = res
				.getDrawable(R.drawable.homescreen_blue_normal_holo);
		mActiveGlowBackground = res
				.getDrawable(R.drawable.homescreen_blue_strong_holo);

		mOverScrollLeft = res.getDrawable(R.drawable.overscroll_glow_left);
		mOverScrollRight = res.getDrawable(R.drawable.overscroll_glow_right);
		mForegroundPadding = res
				.getDimensionPixelSize(R.dimen.workspace_overscroll_drawable_padding);

		mReorderHintAnimationMagnitude = (REORDER_HINT_MAGNITUDE * res
				.getDimensionPixelSize(R.dimen.app_icon_size));

		mNormalBackground.setFilterBitmap(true);
		mActiveGlowBackground.setFilterBitmap(true);

		// Initialize the data structures used for the drag visualization.
		// 初始化加速器(DecelerateInterpolator 动画开始时比较快，然后逐渐减速。)
		mEaseOutInterpolator = new DecelerateInterpolator(2.5f); // Quint ease
																	// out

		mDragCell[0] = mDragCell[1] = -1;
		for (int i = 0; i < mDragOutlines.length; i++) {
			mDragOutlines[i] = new Rect(-1, -1, -1, -1);
		}

		// When dragging things around the home screens, we show a green outline
		// of
		// where the item will land. The outlines gradually fade out, leaving a
		// trail
		// behind the drag path.
		// Set up all the animations that are used to implement this fading.
		// 当主屏幕拖动周围事物时，我们显示一个绿色的轮廓在该项目将落下的地方。
		// 轮廓渐渐淡出,留下一个拖动路径.
		// 设置所有的动画,来实现这个褪色
		final int duration = res
				.getInteger(R.integer.config_dragOutlineFadeTime);
		final float fromAlphaValue = 0;
		final float toAlphaValue = (float) res
				.getInteger(R.integer.config_dragOutlineMaxAlpha);

		Arrays.fill(mDragOutlineAlphas, fromAlphaValue);

		for (int i = 0; i < mDragOutlineAnims.length; i++) {
			final InterruptibleInOutAnimator anim = new InterruptibleInOutAnimator(
					duration, fromAlphaValue, toAlphaValue);
			anim.getAnimator().setInterpolator(mEaseOutInterpolator);
			final int thisIndex = i;
			anim.getAnimator().addUpdateListener(new AnimatorUpdateListener() {
				public void onAnimationUpdate(ValueAnimator animation) {
					final Bitmap outline = (Bitmap) anim.getTag();

					// If an animation is started and then stopped very quickly,
					// we can still
					// get spurious updates we've cleared the tag. Guard against
					// this.
					/*
					 * 如果动画启动后又突然停止，我们仍可以的到一个虚假的更新。 我们已经清除标记，防范
					 */
					if (outline == null) {
						@SuppressWarnings("all")
						// suppress dead code warning
						final boolean debug = true;
						if (debug) {
							Object val = animation.getAnimatedValue();
							Log.d(TAG, "anim " + thisIndex + " update: " + val
									+ ", isStopped " + anim.isStopped());
						}
						// Try to prevent it from continuing to run
						animation.cancel();
					} else {
						mDragOutlineAlphas[thisIndex] = (Float) animation
								.getAnimatedValue();
						CellLayout.this.invalidate(mDragOutlines[thisIndex]);
					}
				}
			});
			// The animation holds a reference to the drag outline bitmap as
			// long is it's
			// running. This way the bitmap can be GCed when the animations are
			// complete.
			anim.getAnimator().addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					if ((Float) ((ValueAnimator) animation).getAnimatedValue() == 0f) {
						anim.setTag(null);
					}
				}
			});
			mDragOutlineAnims[i] = anim;
		}

		mBackgroundRect = new Rect();
		mForegroundRect = new Rect();

		mShortcutsAndWidgets = new ShortcutAndWidgetContainer(context);
		mShortcutsAndWidgets.setCellDimensions(mCellWidth, mCellHeight,
				mWidthGap, mHeightGap);
		addView(mShortcutsAndWidgets);
	}

	static int widthInPortrait(Resources r, int numCells) {
		// We use this method from Workspace to figure out how many rows/columns
		// Launcher should
		// have. We ignore the left/right padding on CellLayout because it turns
		// out in our design
		// the padding extends outside the visible screen size, but it looked
		// fine anyway.
		int cellWidth = r.getDimensionPixelSize(R.dimen.workspace_cell_width);
		int minGap = Math.min(
				r.getDimensionPixelSize(R.dimen.workspace_width_gap),
				r.getDimensionPixelSize(R.dimen.workspace_height_gap));

		return minGap * (numCells - 1) + cellWidth * numCells;
	}

	static int heightInLandscape(Resources r, int numCells) {
		// We use this method from Workspace to figure out how many rows/columns
		// Launcher should
		// have. We ignore the left/right padding on CellLayout because it turns
		// out in our design
		// the padding extends outside the visible screen size, but it looked
		// fine anyway.
		int cellHeight = r.getDimensionPixelSize(R.dimen.workspace_cell_height);
		int minGap = Math.min(
				r.getDimensionPixelSize(R.dimen.workspace_width_gap),
				r.getDimensionPixelSize(R.dimen.workspace_height_gap));

		return minGap * (numCells - 1) + cellHeight * numCells;
	}

	public void enableHardwareLayers() {
		mShortcutsAndWidgets.setLayerType(LAYER_TYPE_HARDWARE, sPaint);
	}

	public void disableHardwareLayers() {
		mShortcutsAndWidgets.setLayerType(LAYER_TYPE_NONE, sPaint);
	}

	public void buildHardwareLayer() {
		mShortcutsAndWidgets.buildLayer();
	}

	public float getChildrenScale() {
		return mIsHotseat ? mHotseatScale : 1.0f;
	}

	public void setGridSize(int x, int y) {
		mCountX = x;
		mCountY = y;
		mOccupied = new boolean[mCountX][mCountY];
		mTmpOccupied = new boolean[mCountX][mCountY];
		mTempRectStack.clear();
		requestLayout();
	}

	private void invalidateBubbleTextView(BubbleTextView icon) {
		final int padding = icon.getPressedOrFocusedBackgroundPadding();
		invalidate(icon.getLeft() + getPaddingLeft() - padding, icon.getTop()
				+ getPaddingTop() - padding, icon.getRight() + getPaddingLeft()
				+ padding, icon.getBottom() + getPaddingTop() + padding);
	}

	void setOverScrollAmount(float r, boolean left) {
		if (left && mOverScrollForegroundDrawable != mOverScrollLeft) {
			mOverScrollForegroundDrawable = mOverScrollLeft;
		} else if (!left && mOverScrollForegroundDrawable != mOverScrollRight) {
			mOverScrollForegroundDrawable = mOverScrollRight;
		}

		mForegroundAlpha = (int) Math.round((r * 255));
		mOverScrollForegroundDrawable.setAlpha(mForegroundAlpha);
		invalidate();
	}

	void setPressedOrFocusedIcon(BubbleTextView icon) {
		// We draw the pressed or focused BubbleTextView's background in
		// CellLayout because it
		// requires an expanded clip rect (due to the glow's blur radius)
		BubbleTextView oldIcon = mPressedOrFocusedIcon;
		mPressedOrFocusedIcon = icon;
		if (oldIcon != null) {
			invalidateBubbleTextView(oldIcon);
		}
		if (mPressedOrFocusedIcon != null) {
			invalidateBubbleTextView(mPressedOrFocusedIcon);
		}
	}

	void setIsDragOverlapping(boolean isDragOverlapping) {
		if (mIsDragOverlapping != isDragOverlapping) {
			mIsDragOverlapping = isDragOverlapping;
			invalidate();
		}
	}

	boolean getIsDragOverlapping() {
		return mIsDragOverlapping;
	}

	protected void setOverscrollTransformsDirty(boolean dirty) {
		mScrollingTransformsDirty = dirty;
	}

	protected void resetOverscrollTransforms() {
		if (mScrollingTransformsDirty) {
			setOverscrollTransformsDirty(false);
			setTranslationX(0);
			setRotationY(0);
			// It doesn't matter if we pass true or false here, the important
			// thing is that we
			// pass 0, which results in the overscroll drawable not being drawn
			// any more.
			setOverScrollAmount(0, false);
			setPivotX(getMeasuredWidth() / 2);
			setPivotY(getMeasuredHeight() / 2);
		}
	}

	public void scaleRect(Rect r, float scale) {
		if (scale != 1.0f) {
			r.left = (int) (r.left * scale + 0.5f);
			r.top = (int) (r.top * scale + 0.5f);
			r.right = (int) (r.right * scale + 0.5f);
			r.bottom = (int) (r.bottom * scale + 0.5f);
		}
	}

	Rect temp = new Rect();

	void scaleRectAboutCenter(Rect in, Rect out, float scale) {
		int cx = in.centerX();
		int cy = in.centerY();
		out.set(in);
		out.offset(-cx, -cy);
		scaleRect(out, scale);
		out.offset(cx, cy);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		// When we're large, we are either drawn in a "hover" state (ie when
		// dragging an item to
		// a neighboring page) or with just a normal background (if
		// backgroundAlpha > 0.0f)
		// When we're small, we are either drawn normally or in the
		// "accepts drops" state (during
		// a drag). However, we also drag the mini hover background *over* one
		// of those two
		// backgrounds
		if (mBackgroundAlpha > 0.0f) {
			Drawable bg;

			if (mIsDragOverlapping) {
				// In the mini case, we draw the active_glow bg *over* the
				// active background
				bg = mActiveGlowBackground;
			} else {
				bg = mNormalBackground;
			}

			bg.setAlpha((int) (mBackgroundAlpha * mBackgroundAlphaMultiplier * 255));
			bg.setBounds(mBackgroundRect);
			bg.draw(canvas);
		}

		final Paint paint = mDragOutlinePaint;
		for (int i = 0; i < mDragOutlines.length; i++) {
			final float alpha = mDragOutlineAlphas[i];
			if (alpha > 0) {
				final Rect r = mDragOutlines[i];
				scaleRectAboutCenter(r, temp, getChildrenScale());
				final Bitmap b = (Bitmap) mDragOutlineAnims[i].getTag();
				paint.setAlpha((int) (alpha + .5f));
				canvas.drawBitmap(b, null, temp, paint);
			}
		}

		// We draw the pressed or focused BubbleTextView's background in
		// CellLayout because it
		// requires an expanded clip rect (due to the glow's blur radius)
		if (mPressedOrFocusedIcon != null) {
			final int padding = mPressedOrFocusedIcon
					.getPressedOrFocusedBackgroundPadding();
			final Bitmap b = mPressedOrFocusedIcon
					.getPressedOrFocusedBackground();
			if (b != null) {
				canvas.drawBitmap(b, mPressedOrFocusedIcon.getLeft()
						+ getPaddingLeft() - padding,
						mPressedOrFocusedIcon.getTop() + getPaddingTop()
								- padding, null);
			}
		}

		if (DEBUG_VISUALIZE_OCCUPIED) {
			int[] pt = new int[2];
			ColorDrawable cd = new ColorDrawable(Color.RED);
			cd.setBounds(0, 0, mCellWidth, mCellHeight);
			for (int i = 0; i < mCountX; i++) {
				for (int j = 0; j < mCountY; j++) {
					if (mOccupied[i][j]) {
						cellToPoint(i, j, pt);
						canvas.save();
						canvas.translate(pt[0], pt[1]);
						cd.draw(canvas);
						canvas.restore();
					}
				}
			}
		}

		int previewOffset = FolderRingAnimator.sPreviewSize;

		// The folder outer / inner ring image(s)
		for (int i = 0; i < mFolderOuterRings.size(); i++) {
			FolderRingAnimator fra = mFolderOuterRings.get(i);

			// Draw outer ring
			Drawable d = FolderRingAnimator.sSharedOuterRingDrawable;
			int width = (int) fra.getOuterRingSize();
			int height = width;
			cellToPoint(fra.mCellX, fra.mCellY, mTempLocation);

			int centerX = mTempLocation[0] + mCellWidth / 2;
			int centerY = mTempLocation[1] + previewOffset / 2;

			canvas.save();
			canvas.translate(centerX - width / 2, centerY - height / 2);
			d.setBounds(0, 0, width, height);
			d.draw(canvas);
			canvas.restore();

			// Draw inner ring
			d = FolderRingAnimator.sSharedInnerRingDrawable;
			width = (int) fra.getInnerRingSize();
			height = width;
			cellToPoint(fra.mCellX, fra.mCellY, mTempLocation);

			centerX = mTempLocation[0] + mCellWidth / 2;
			centerY = mTempLocation[1] + previewOffset / 2;
			canvas.save();
			canvas.translate(centerX - width / 2, centerY - width / 2);
			d.setBounds(0, 0, width, height);
			d.draw(canvas);
			canvas.restore();
		}

		if (mFolderLeaveBehindCell[0] >= 0 && mFolderLeaveBehindCell[1] >= 0) {
			Drawable d = FolderIcon.sSharedFolderLeaveBehind;
			int width = d.getIntrinsicWidth();
			int height = d.getIntrinsicHeight();

			cellToPoint(mFolderLeaveBehindCell[0], mFolderLeaveBehindCell[1],
					mTempLocation);
			int centerX = mTempLocation[0] + mCellWidth / 2;
			int centerY = mTempLocation[1] + previewOffset / 2;

			canvas.save();
			canvas.translate(centerX - width / 2, centerY - width / 2);
			d.setBounds(0, 0, width, height);
			d.draw(canvas);
			canvas.restore();
		}
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);
		if (mForegroundAlpha > 0) {
			mOverScrollForegroundDrawable.setBounds(mForegroundRect);
			Paint p = ((NinePatchDrawable) mOverScrollForegroundDrawable)
					.getPaint();
			p.setXfermode(sAddBlendMode);
			mOverScrollForegroundDrawable.draw(canvas);
			p.setXfermode(null);
		}
	}

	public void showFolderAccept(FolderRingAnimator fra) {
		mFolderOuterRings.add(fra);
	}

	public void hideFolderAccept(FolderRingAnimator fra) {
		if (mFolderOuterRings.contains(fra)) {
			mFolderOuterRings.remove(fra);
		}
		invalidate();
	}

	public void setFolderLeaveBehindCell(int x, int y) {
		mFolderLeaveBehindCell[0] = x;
		mFolderLeaveBehindCell[1] = y;
		invalidate();
	}

	public void clearFolderLeaveBehind() {
		mFolderLeaveBehindCell[0] = -1;
		mFolderLeaveBehindCell[1] = -1;
		invalidate();
	}

	@Override
	public boolean shouldDelayChildPressedState() {
		return false;
	}

	public void restoreInstanceState(SparseArray<Parcelable> states) {
		dispatchRestoreInstanceState(states);
	}

	@Override
	public void cancelLongPress() {
		super.cancelLongPress();

		// Cancel long press for all children
		final int count = getChildCount();
		for (int i = 0; i < count; i++) {
			final View child = getChildAt(i);
			child.cancelLongPress();
		}
	}

	public void setOnInterceptTouchListener(View.OnTouchListener listener) {
		mInterceptTouchListener = listener;
	}

	public int getCountX() {
		return mCountX;
	}

	public int getCountY() {
		return mCountY;
	}

	public void setIsHotseat(boolean isHotseat) {
		mIsHotseat = isHotseat;
	}

	public boolean addViewToCellLayout(View child, int index, int childId,
			LayoutParams params, boolean markCells) {
		final LayoutParams lp = params;

		// Hotseat icons - remove text
		if (child instanceof BubbleTextView) {
			BubbleTextView bubbleChild = (BubbleTextView) child;

			Resources res = getResources();
			if (mIsHotseat) {
				bubbleChild.setTextColor(res
						.getColor(android.R.color.transparent));
			} else {
				bubbleChild.setTextColor(res
						.getColor(R.color.workspace_icon_text_color));
			}
		}

		child.setScaleX(getChildrenScale());
		child.setScaleY(getChildrenScale());

		// Generate an id for each view, this assumes we have at most 256x256
		// cells
		// per workspace screen
		if (lp.cellX >= 0 && lp.cellX <= mCountX - 1 && lp.cellY >= 0
				&& lp.cellY <= mCountY - 1) {
			// If the horizontal or vertical span is set to -1, it is taken to
			// mean that it spans the extent of the CellLayout
			if (lp.cellHSpan < 0)
				lp.cellHSpan = mCountX;
			if (lp.cellVSpan < 0)
				lp.cellVSpan = mCountY;

			child.setId(childId);

			mShortcutsAndWidgets.addView(child, index, lp);

			if (markCells)
				markCellsAsOccupiedForView(child);

			return true;
		}
		return false;
	}

	@Override
	public void removeAllViews() {
		clearOccupiedCells();
		mShortcutsAndWidgets.removeAllViews();
	}

	@Override
	public void removeAllViewsInLayout() {
		if (mShortcutsAndWidgets.getChildCount() > 0) {
			clearOccupiedCells();
			mShortcutsAndWidgets.removeAllViewsInLayout();
		}
	}

	public void removeViewWithoutMarkingCells(View view) {
		mShortcutsAndWidgets.removeView(view);
	}

	@Override
	public void removeView(View view) {
		markCellsAsUnoccupiedForView(view);
		mShortcutsAndWidgets.removeView(view);
	}

	@Override
	public void removeViewAt(int index) {
		markCellsAsUnoccupiedForView(mShortcutsAndWidgets.getChildAt(index));
		mShortcutsAndWidgets.removeViewAt(index);
	}

	@Override
	public void removeViewInLayout(View view) {
		markCellsAsUnoccupiedForView(view);
		mShortcutsAndWidgets.removeViewInLayout(view);
	}

	@Override
	public void removeViews(int start, int count) {
		for (int i = start; i < start + count; i++) {
			markCellsAsUnoccupiedForView(mShortcutsAndWidgets.getChildAt(i));
		}
		mShortcutsAndWidgets.removeViews(start, count);
	}

	@Override
	public void removeViewsInLayout(int start, int count) {
		for (int i = start; i < start + count; i++) {
			markCellsAsUnoccupiedForView(mShortcutsAndWidgets.getChildAt(i));
		}
		mShortcutsAndWidgets.removeViewsInLayout(start, count);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		mCellInfo.screen = ((ViewGroup) getParent()).indexOfChild(this);
	}

	public void setTagToCellInfoForPoint(int touchX, int touchY) {
		final CellInfo cellInfo = mCellInfo;
		Rect frame = mRect;
		final int x = touchX + getScrollX();
		final int y = touchY + getScrollY();
		final int count = mShortcutsAndWidgets.getChildCount();

		boolean found = false;
		for (int i = count - 1; i >= 0; i--) {
			final View child = mShortcutsAndWidgets.getChildAt(i);
			final LayoutParams lp = (LayoutParams) child.getLayoutParams();

			if ((child.getVisibility() == VISIBLE || child.getAnimation() != null)
					&& lp.isLockedToGrid) {
				child.getHitRect(frame);

				float scale = child.getScaleX();
				frame = new Rect(child.getLeft(), child.getTop(),
						child.getRight(), child.getBottom());
				// The child hit rect is relative to the CellLayoutChildren
				// parent, so we need to
				// offset that by this CellLayout's padding to test an (x,y)
				// point that is relative
				// to this view.
				frame.offset(getPaddingLeft(), getPaddingTop());
				frame.inset((int) (frame.width() * (1f - scale) / 2),
						(int) (frame.height() * (1f - scale) / 2));

				if (frame.contains(x, y)) {
					cellInfo.cell = child;
					cellInfo.cellX = lp.cellX;
					cellInfo.cellY = lp.cellY;
					cellInfo.spanX = lp.cellHSpan;
					cellInfo.spanY = lp.cellVSpan;
					found = true;
					break;
				}
			}
		}

		mLastDownOnOccupiedCell = found;

		if (!found) {
			final int cellXY[] = mTmpXY;
			pointToCellExact(x, y, cellXY);

			cellInfo.cell = null;
			cellInfo.cellX = cellXY[0];
			cellInfo.cellY = cellXY[1];
			cellInfo.spanX = 1;
			cellInfo.spanY = 1;
		}
		setTag(cellInfo);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		// First we clear the tag to ensure that on every touch down we start
		// with a fresh slate,
		// even in the case where we return early. Not clearing here was causing
		// bugs whereby on
		// long-press we'd end up picking up an item from a previous drag
		// operation.
		final int action = ev.getAction();

		if (action == MotionEvent.ACTION_DOWN) {
			clearTagCellInfo();
		}

		if (mInterceptTouchListener != null
				&& mInterceptTouchListener.onTouch(this, ev)) {
			return true;
		}

		if (action == MotionEvent.ACTION_DOWN) {
			setTagToCellInfoForPoint((int) ev.getX(), (int) ev.getY());
		}

		return false;
	}

	private void clearTagCellInfo() {
		final CellInfo cellInfo = mCellInfo;
		cellInfo.cell = null;
		cellInfo.cellX = -1;
		cellInfo.cellY = -1;
		cellInfo.spanX = 0;
		cellInfo.spanY = 0;
		setTag(cellInfo);
	}

	public CellInfo getTag() {
		return (CellInfo) super.getTag();
	}

	/**
	 * Given a point, return the cell that strictly encloses that point
	 * 
	 * @param x
	 *            X coordinate of the point
	 * @param y
	 *            Y coordinate of the point
	 * @param result
	 *            Array of 2 ints to hold the x and y coordinate of the cell
	 */
	void pointToCellExact(int x, int y, int[] result) {
		final int hStartPadding = getPaddingLeft();
		final int vStartPadding = getPaddingTop();

		result[0] = (x - hStartPadding) / (mCellWidth + mWidthGap);
		result[1] = (y - vStartPadding) / (mCellHeight + mHeightGap);

		final int xAxis = mCountX;
		final int yAxis = mCountY;

		if (result[0] < 0)
			result[0] = 0;
		if (result[0] >= xAxis)
			result[0] = xAxis - 1;
		if (result[1] < 0)
			result[1] = 0;
		if (result[1] >= yAxis)
			result[1] = yAxis - 1;
	}

	/**
	 * Given a point, return the cell that most closely encloses that point
	 * 
	 * @param x
	 *            X coordinate of the point
	 * @param y
	 *            Y coordinate of the point
	 * @param result
	 *            Array of 2 ints to hold the x and y coordinate of the cell
	 */
	void pointToCellRounded(int x, int y, int[] result) {
		pointToCellExact(x + (mCellWidth / 2), y + (mCellHeight / 2), result);
	}

	/**
	 * Given a cell coordinate, return the point that represents the upper left
	 * corner of that cell
	 * 
	 * @param cellX
	 *            X coordinate of the cell
	 * @param cellY
	 *            Y coordinate of the cell
	 * 
	 * @param result
	 *            Array of 2 ints to hold the x and y coordinate of the point
	 */
	void cellToPoint(int cellX, int cellY, int[] result) {
		final int hStartPadding = getPaddingLeft();
		final int vStartPadding = getPaddingTop();

		result[0] = hStartPadding + cellX * (mCellWidth + mWidthGap);
		result[1] = vStartPadding + cellY * (mCellHeight + mHeightGap);
	}

	/**
	 * 给定的单元格的坐标，返回的点，它表示的单元格的中心。 Given a cell coordinate, return the point that
	 * represents the center of the cell
	 * 
	 * @param cellX
	 *            X coordinate of the cell
	 * @param cellY
	 *            Y coordinate of the cell
	 * 
	 * @param result
	 *            Array of 2 ints to hold the x and y coordinate of the point
	 */
	void cellToCenterPoint(int cellX, int cellY, int[] result) {
		regionToCenterPoint(cellX, cellY, 1, 1, result);
	}

	/**
	 * 给定的单元格的坐标和跨度，返回的点它表示这个区域的中心 Given a cell coordinate and span return the
	 * point that represents the center of the regio
	 * 
	 * @param cellX
	 *            X coordinate of the cell
	 * @param cellY
	 *            Y coordinate of the cell
	 * 
	 * @param result
	 *            Array of 2 ints to hold the x and y coordinate of the point
	 */
	void regionToCenterPoint(int cellX, int cellY, int spanX, int spanY,
			int[] result) {
		final int hStartPadding = getPaddingLeft();
		final int vStartPadding = getPaddingTop();
		result[0] = hStartPadding + cellX * (mCellWidth + mWidthGap)
				+ (spanX * mCellWidth + (spanX - 1) * mWidthGap) / 2;
		result[1] = vStartPadding + cellY * (mCellHeight + mHeightGap)
				+ (spanY * mCellHeight + (spanY - 1) * mHeightGap) / 2;
	}

	/**
	 * Given a cell coordinate and span fills out a corresponding pixel rect
	 * 给定单元格坐标和跨度，填充对应的矩形
	 * 
	 * @param cellX
	 *            X coordinate of the cell
	 * @param cellY
	 *            Y coordinate of the cell
	 * @param result
	 *            Rect in which to write the result
	 */
	void regionToRect(int cellX, int cellY, int spanX, int spanY, Rect result) {
		//得到对子布局的间距
		final int hStartPadding = getPaddingLeft();
		final int vStartPadding = getPaddingTop();
		//计算左上角的点
		final int left = hStartPadding + cellX * (mCellWidth + mWidthGap);
		final int top = vStartPadding + cellY * (mCellHeight + mHeightGap);
		result.set(left, top, left
				+ (spanX * mCellWidth + (spanX - 1) * mWidthGap), top
				+ (spanY * mCellHeight + (spanY - 1) * mHeightGap));
	}

	public float getDistanceFromCell(float x, float y, int[] cell) {
		cellToCenterPoint(cell[0], cell[1], mTmpPoint);
		float distance = (float) Math.sqrt(Math.pow(x - mTmpPoint[0], 2)
				+ Math.pow(y - mTmpPoint[1], 2));
		return distance;
	}

	int getCellWidth() {
		return mCellWidth;
	}

	int getCellHeight() {
		return mCellHeight;
	}

	int getWidthGap() {
		return mWidthGap;
	}

	int getHeightGap() {
		return mHeightGap;
	}

	Rect getContentRect(Rect r) {
		if (r == null) {
			r = new Rect();
		}
		int left = getPaddingLeft();
		int top = getPaddingTop();
		int right = left + getWidth() - getPaddingLeft() - getPaddingRight();
		int bottom = top + getHeight() - getPaddingTop() - getPaddingBottom();
		r.set(left, top, right, bottom);
		return r;
	}

	static void getMetrics(Rect metrics, Resources res, int measureWidth,
			int measureHeight, int countX, int countY, int orientation) {
		int numWidthGaps = countX - 1;
		int numHeightGaps = countY - 1;

		int widthGap;
		int heightGap;
		int cellWidth;
		int cellHeight;
		int paddingLeft;
		int paddingRight;
		int paddingTop;
		int paddingBottom;

		int maxGap = res.getDimensionPixelSize(R.dimen.workspace_max_gap);
		if (orientation == LANDSCAPE) {
			cellWidth = res
					.getDimensionPixelSize(R.dimen.workspace_cell_width_land);
			cellHeight = res
					.getDimensionPixelSize(R.dimen.workspace_cell_height_land);
			widthGap = res
					.getDimensionPixelSize(R.dimen.workspace_width_gap_land);
			heightGap = res
					.getDimensionPixelSize(R.dimen.workspace_height_gap_land);
			paddingLeft = res
					.getDimensionPixelSize(R.dimen.cell_layout_left_padding_land);
			paddingRight = res
					.getDimensionPixelSize(R.dimen.cell_layout_right_padding_land);
			paddingTop = res
					.getDimensionPixelSize(R.dimen.cell_layout_top_padding_land);
			paddingBottom = res
					.getDimensionPixelSize(R.dimen.cell_layout_bottom_padding_land);
		} else {
			// PORTRAIT
			cellWidth = res
					.getDimensionPixelSize(R.dimen.workspace_cell_width_port);
			cellHeight = res
					.getDimensionPixelSize(R.dimen.workspace_cell_height_port);
			widthGap = res
					.getDimensionPixelSize(R.dimen.workspace_width_gap_port);
			heightGap = res
					.getDimensionPixelSize(R.dimen.workspace_height_gap_port);
			paddingLeft = res
					.getDimensionPixelSize(R.dimen.cell_layout_left_padding_port);
			paddingRight = res
					.getDimensionPixelSize(R.dimen.cell_layout_right_padding_port);
			paddingTop = res
					.getDimensionPixelSize(R.dimen.cell_layout_top_padding_port);
			paddingBottom = res
					.getDimensionPixelSize(R.dimen.cell_layout_bottom_padding_port);
		}

		if (widthGap < 0 || heightGap < 0) {
			int hSpace = measureWidth - paddingLeft - paddingRight;
			int vSpace = measureHeight - paddingTop - paddingBottom;
			int hFreeSpace = hSpace - (countX * cellWidth);
			int vFreeSpace = vSpace - (countY * cellHeight);
			widthGap = Math.min(maxGap,
					numWidthGaps > 0 ? (hFreeSpace / numWidthGaps) : 0);
			heightGap = Math.min(maxGap,
					numHeightGaps > 0 ? (vFreeSpace / numHeightGaps) : 0);
		}
		metrics.set(cellWidth, cellHeight, widthGap, heightGap);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
		int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);

		int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
		int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);

		if (widthSpecMode == MeasureSpec.UNSPECIFIED
				|| heightSpecMode == MeasureSpec.UNSPECIFIED) {
			throw new RuntimeException(
					"CellLayout cannot have UNSPECIFIED dimensions");
		}

		int numWidthGaps = mCountX - 1;
		int numHeightGaps = mCountY - 1;

		if (mOriginalWidthGap < 0 || mOriginalHeightGap < 0) {
			int hSpace = widthSpecSize - getPaddingLeft() - getPaddingRight();
			int vSpace = heightSpecSize - getPaddingTop() - getPaddingBottom();
			int hFreeSpace = hSpace - (mCountX * mCellWidth);
			int vFreeSpace = vSpace - (mCountY * mCellHeight);
			mWidthGap = Math.min(mMaxGap,
					numWidthGaps > 0 ? (hFreeSpace / numWidthGaps) : 0);
			mHeightGap = Math.min(mMaxGap,
					numHeightGaps > 0 ? (vFreeSpace / numHeightGaps) : 0);
			mShortcutsAndWidgets.setCellDimensions(mCellWidth, mCellHeight,
					mWidthGap, mHeightGap);
		} else {
			mWidthGap = mOriginalWidthGap;
			mHeightGap = mOriginalHeightGap;
		}

		// Initial values correspond to widthSpecMode == MeasureSpec.EXACTLY
		int newWidth = widthSpecSize;
		int newHeight = heightSpecSize;
		if (widthSpecMode == MeasureSpec.AT_MOST) {
			newWidth = getPaddingLeft() + getPaddingRight()
					+ (mCountX * mCellWidth) + ((mCountX - 1) * mWidthGap);
			newHeight = getPaddingTop() + getPaddingBottom()
					+ (mCountY * mCellHeight) + ((mCountY - 1) * mHeightGap);
			setMeasuredDimension(newWidth, newHeight);
		}

		int count = getChildCount();
		// 计算每个子空间所需要的空间
		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);
			int childWidthMeasureSpec = MeasureSpec
					.makeMeasureSpec(newWidth - getPaddingLeft()
							- getPaddingRight(), MeasureSpec.EXACTLY);
			int childheightMeasureSpec = MeasureSpec
					.makeMeasureSpec(newHeight - getPaddingTop()
							- getPaddingBottom(), MeasureSpec.EXACTLY);
			child.measure(childWidthMeasureSpec, childheightMeasureSpec);
		}
		setMeasuredDimension(newWidth, newHeight);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		int count = getChildCount();
		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);
			child.layout(getPaddingLeft(), getPaddingTop(), r - l
					- getPaddingRight(), b - t - getPaddingBottom());
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		mBackgroundRect.set(0, 0, w, h);
		mForegroundRect.set(mForegroundPadding, mForegroundPadding, w
				- mForegroundPadding, h - mForegroundPadding);
	}

	@Override
	protected void setChildrenDrawingCacheEnabled(boolean enabled) {
		mShortcutsAndWidgets.setChildrenDrawingCacheEnabled(enabled);
	}

	@Override
	protected void setChildrenDrawnWithCacheEnabled(boolean enabled) {
		mShortcutsAndWidgets.setChildrenDrawnWithCacheEnabled(enabled);
	}

	public float getBackgroundAlpha() {
		return mBackgroundAlpha;
	}

	public void setBackgroundAlphaMultiplier(float multiplier) {
		if (mBackgroundAlphaMultiplier != multiplier) {
			mBackgroundAlphaMultiplier = multiplier;
			invalidate();
		}
	}

	public float getBackgroundAlphaMultiplier() {
		return mBackgroundAlphaMultiplier;
	}

	public void setBackgroundAlpha(float alpha) {
		if (mBackgroundAlpha != alpha) {
			mBackgroundAlpha = alpha;
			invalidate();
		}
	}

	public void setShortcutAndWidgetAlpha(float alpha) {
		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			getChildAt(i).setAlpha(alpha);
		}
	}

	public ShortcutAndWidgetContainer getShortcutsAndWidgets() {
		if (getChildCount() > 0) {
			return (ShortcutAndWidgetContainer) getChildAt(0);
		}
		return null;
	}

	public View getChildAt(int x, int y) {
		return mShortcutsAndWidgets.getChildAt(x, y);
	}

	public boolean animateChildToPosition(final View child, int cellX,
			int cellY, int duration, int delay, boolean permanent,
			boolean adjustOccupied) {
		ShortcutAndWidgetContainer clc = getShortcutsAndWidgets();
		boolean[][] occupied = mOccupied;
		if (!permanent) {
			occupied = mTmpOccupied;
		}

		if (clc.indexOfChild(child) != -1) {
			final LayoutParams lp = (LayoutParams) child.getLayoutParams();
			final ItemInfo info = (ItemInfo) child.getTag();

			// We cancel any existing animations
			if (mReorderAnimators.containsKey(lp)) {
				mReorderAnimators.get(lp).cancel();
				mReorderAnimators.remove(lp);
			}

			final int oldX = lp.x;
			final int oldY = lp.y;
			if (adjustOccupied) {
				occupied[lp.cellX][lp.cellY] = false;
				occupied[cellX][cellY] = true;
			}
			lp.isLockedToGrid = true;
			if (permanent) {
				lp.cellX = info.cellX = cellX;
				lp.cellY = info.cellY = cellY;
			} else {
				lp.tmpCellX = cellX;
				lp.tmpCellY = cellY;
			}
			clc.setupLp(lp);
			lp.isLockedToGrid = false;
			final int newX = lp.x;
			final int newY = lp.y;

			lp.x = oldX;
			lp.y = oldY;

			// Exit early if we're not actually moving the view
			if (oldX == newX && oldY == newY) {
				lp.isLockedToGrid = true;
				return true;
			}

			ValueAnimator va = LauncherAnimUtils.ofFloat(0f, 1f);
			va.setDuration(duration);
			mReorderAnimators.put(lp, va);

			va.addUpdateListener(new AnimatorUpdateListener() {
				@Override
				public void onAnimationUpdate(ValueAnimator animation) {
					float r = ((Float) animation.getAnimatedValue())
							.floatValue();
					lp.x = (int) ((1 - r) * oldX + r * newX);
					lp.y = (int) ((1 - r) * oldY + r * newY);
					child.requestLayout();
				}
			});
			va.addListener(new AnimatorListenerAdapter() {
				boolean cancelled = false;

				public void onAnimationEnd(Animator animation) {
					// If the animation was cancelled, it means that another
					// animation
					// has interrupted this one, and we don't want to lock the
					// item into
					// place just yet.
					if (!cancelled) {
						lp.isLockedToGrid = true;
						child.requestLayout();
					}
					if (mReorderAnimators.containsKey(lp)) {
						mReorderAnimators.remove(lp);
					}
				}

				public void onAnimationCancel(Animator animation) {
					cancelled = true;
				}
			});
			va.setStartDelay(delay);
			va.start();
			return true;
		}
		return false;
	}

	/**
	 * Estimate where the top left cell of the dragged item will land if it is
	 * dropped.
	 * 
	 * @param originX
	 *            The X value of the top left corner of the item
	 * @param originY
	 *            The Y value of the top left corner of the item
	 * @param spanX
	 *            The number of horizontal cells that the item spans
	 * @param spanY
	 *            The number of vertical cells that the item spans
	 * @param result
	 *            The estimated drop cell X and Y.
	 */
	void estimateDropCell(int originX, int originY, int spanX, int spanY,
			int[] result) {
		final int countX = mCountX;
		final int countY = mCountY;

		// pointToCellRounded takes the top left of a cell but will pad that
		// with
		// cellWidth/2 and cellHeight/2 when finding the matching cell
		pointToCellRounded(originX, originY, result);

		// If the item isn't fully on this screen, snap to the edges
		int rightOverhang = result[0] + spanX - countX;
		if (rightOverhang > 0) {
			result[0] -= rightOverhang; // Snap to right
		}
		result[0] = Math.max(0, result[0]); // Snap to left
		int bottomOverhang = result[1] + spanY - countY;
		if (bottomOverhang > 0) {
			result[1] -= bottomOverhang; // Snap to bottom
		}
		result[1] = Math.max(0, result[1]); // Snap to top
	}

	void visualizeDropLocation(View v, Bitmap dragOutline, int originX,
			int originY, int cellX, int cellY, int spanX, int spanY,
			boolean resize, Point dragOffset, Rect dragRegion) {
		final int oldDragCellX = mDragCell[0];
		final int oldDragCellY = mDragCell[1];

		if (v != null && dragOffset == null) {
			mDragCenter.set(originX + (v.getWidth() / 2),
					originY + (v.getHeight() / 2));
		} else {
			mDragCenter.set(originX, originY);
		}

		if (dragOutline == null && v == null) {
			return;
		}

		if (cellX != oldDragCellX || cellY != oldDragCellY) {
			mDragCell[0] = cellX;
			mDragCell[1] = cellY;
			// Find the top left corner of the rect the object will occupy
			final int[] topLeft = mTmpPoint;
			cellToPoint(cellX, cellY, topLeft);

			int left = topLeft[0];
			int top = topLeft[1];

			if (v != null && dragOffset == null) {
				// When drawing the drag outline, it did not account for margin
				// offsets
				// added by the view's parent.
				MarginLayoutParams lp = (MarginLayoutParams) v
						.getLayoutParams();
				left += lp.leftMargin;
				top += lp.topMargin;

				// Offsets due to the size difference between the View and the
				// dragOutline.
				// There is a size difference to account for the outer blur,
				// which may lie
				// outside the bounds of the view.
				top += (v.getHeight() - dragOutline.getHeight()) / 2;
				// We center about the x axis
				left += ((mCellWidth * spanX) + ((spanX - 1) * mWidthGap) - dragOutline
						.getWidth()) / 2;
			} else {
				if (dragOffset != null && dragRegion != null) {
					// Center the drag region *horizontally* in the cell and
					// apply a drag
					// outline offset
					left += dragOffset.x
							+ ((mCellWidth * spanX) + ((spanX - 1) * mWidthGap) - dragRegion
									.width()) / 2;
					top += dragOffset.y;
				} else {
					// Center the drag outline in the cell
					left += ((mCellWidth * spanX) + ((spanX - 1) * mWidthGap) - dragOutline
							.getWidth()) / 2;
					top += ((mCellHeight * spanY) + ((spanY - 1) * mHeightGap) - dragOutline
							.getHeight()) / 2;
				}
			}
			final int oldIndex = mDragOutlineCurrent;
			mDragOutlineAnims[oldIndex].animateOut();
			mDragOutlineCurrent = (oldIndex + 1) % mDragOutlines.length;
			Rect r = mDragOutlines[mDragOutlineCurrent];
			r.set(left, top, left + dragOutline.getWidth(),
					top + dragOutline.getHeight());
			if (resize) {
				cellToRect(cellX, cellY, spanX, spanY, r);
			}

			mDragOutlineAnims[mDragOutlineCurrent].setTag(dragOutline);
			mDragOutlineAnims[mDragOutlineCurrent].animateIn();
		}
	}

	public void clearDragOutlines() {
		final int oldIndex = mDragOutlineCurrent;
		mDragOutlineAnims[oldIndex].animateOut();
		mDragCell[0] = mDragCell[1] = -1;
	}

	/**
	 * Find a vacant area that will fit the given bounds nearest the requested
	 * cell location. Uses Euclidean distance to score multiple vacant areas.
	 * 
	 * @param pixelX
	 *            The X location at which you want to search for a vacant area.
	 * @param pixelY
	 *            The Y location at which you want to search for a vacant area.
	 * @param spanX
	 *            Horizontal span of the object.
	 * @param spanY
	 *            Vertical span of the object.
	 * @param result
	 *            Array in which to place the result, or null (in which case a
	 *            new array will be allocated)
	 * @return The X, Y cell of a vacant area that can contain this object,
	 *         nearest the requested location.
	 */
	public int[] findNearestVacantArea(int pixelX, int pixelY, int spanX,
			int spanY, int[] result) {
		return findNearestVacantArea(pixelX, pixelY, spanX, spanY, null, result);
	}

	/**
	 * Find a vacant area that will fit the given bounds nearest the requested
	 * cell location. Uses Euclidean distance to score multiple vacant areas.
	 * 
	 * @param pixelX
	 *            The X location at which you want to search for a vacant area.
	 * @param pixelY
	 *            The Y location at which you want to search for a vacant area.
	 * @param minSpanX
	 *            The minimum horizontal span required
	 * @param minSpanY
	 *            The minimum vertical span required
	 * @param spanX
	 *            Horizontal span of the object.
	 * @param spanY
	 *            Vertical span of the object.
	 * @param result
	 *            Array in which to place the result, or null (in which case a
	 *            new array will be allocated)
	 * @return The X, Y cell of a vacant area that can contain this object,
	 *         nearest the requested location.
	 */
	public int[] findNearestVacantArea(int pixelX, int pixelY, int minSpanX,
			int minSpanY, int spanX, int spanY, int[] result, int[] resultSpan) {
		return findNearestVacantArea(pixelX, pixelY, minSpanX, minSpanY, spanX,
				spanY, null, result, resultSpan);
	}

	/**
	 * Find a vacant area that will fit the given bounds nearest the requested
	 * cell location. Uses Euclidean distance to score multiple vacant areas.
	 * 
	 * @param pixelX
	 *            The X location at which you want to search for a vacant area.
	 * @param pixelY
	 *            The Y location at which you want to search for a vacant area.
	 * @param spanX
	 *            Horizontal span of the object.
	 * @param spanY
	 *            Vertical span of the object.
	 * @param ignoreOccupied
	 *            If true, the result can be an occupied cell
	 * @param result
	 *            Array in which to place the result, or null (in which case a
	 *            new array will be allocated)
	 * @return The X, Y cell of a vacant area that can contain this object,
	 *         nearest the requested location.
	 */
	int[] findNearestArea(int pixelX, int pixelY, int spanX, int spanY,
			View ignoreView, boolean ignoreOccupied, int[] result) {
		return findNearestArea(pixelX, pixelY, spanX, spanY, spanX, spanY,
				ignoreView, ignoreOccupied, result, null, mOccupied);
	}

	private final Stack<Rect> mTempRectStack = new Stack<Rect>();

	private void lazyInitTempRectStack() {
		if (mTempRectStack.isEmpty()) {
			for (int i = 0; i < mCountX * mCountY; i++) {
				mTempRectStack.push(new Rect());
			}
		}
	}

	private void recycleTempRects(Stack<Rect> used) {
		while (!used.isEmpty()) {
			mTempRectStack.push(used.pop());
		}
	}

	/**
	 * 查找一个空置面积最接近要求的单元格位置 Find a vacant area that will fit the given bounds
	 * nearest the requested cell location. Uses Euclidean distance to score
	 * multiple vacant areas.
	 * 
	 * 
	 * @param pixelX
	 *            The X location at which you want to search for a vacant area.
	 * @param pixelY
	 *            The Y location at which you want to search for a vacant area.
	 * @param minSpanX
	 *            The minimum horizontal span required
	 * @param minSpanY
	 *            The minimum vertical span required
	 * @param spanX
	 *            Horizontal span of the object.
	 * @param spanY
	 *            Vertical span of the object.
	 * @param ignoreOccupied
	 *            If true, the result can be an occupied cell
	 *            如果为真，其结果可能是一个已被占用的格子
	 * @param result
	 *            Array in which to place the result, or null (in which case a
	 *            new array will be allocated) 将结果放置在数组中。如果为null 这种情况下讲分配一个新的数组
	 * @param resultSpan
	 *            将结果跨度放进数组中
	 * @return The X, Y cell of a vacant area that can contain this object,
	 *         nearest the requested location. 这个返回的XY
	 *         是空闲的格子。他是可以包含此对象的区域。最接近所请求的位置。
	 */
	int[] findNearestArea(int pixelX, int pixelY, int minSpanX, int minSpanY,
			int spanX, int spanY, View ignoreView, boolean ignoreOccupied,
			int[] result, int[] resultSpan, boolean[][] occupied) {
		lazyInitTempRectStack();
		// mark space take by ignoreView as available (method checks if
		// ignoreView is null)
		// 标记 ignoreView可用的空间（方法检查ignoreView是否为空)
		markCellsAsUnoccupiedForView(ignoreView, occupied);

		// For items with a spanX / spanY > 1, the passed in point (pixelX,
		// pixelY) corresponds
		// to the center of the item, but we are searching based on the top-left
		// cell, so
		// we translate the point over to correspond to the top-left.
		// 个人理解下面这两行代码的作用为：将坐标点转换为单元格左上角的顶部
		pixelX -= (mCellWidth + mWidthGap) * (spanX - 1) / 2f;
		pixelY -= (mCellHeight + mHeightGap) * (spanY - 1) / 2f;

		// Keep track of best-scoring drop area
		// 追踪最佳拖放区域
		final int[] bestXY = result != null ? result : new int[2];
		double bestDistance = Double.MAX_VALUE;
		final Rect bestRect = new Rect(-1, -1, -1, -1);
		final Stack<Rect> validRegions = new Stack<Rect>();

		final int countX = mCountX;
		final int countY = mCountY;

		// 如果控件宽高小于等于0 返回-1矩形
		if (minSpanX <= 0 || minSpanY <= 0 || spanX <= 0 || spanY <= 0
				|| spanX < minSpanX || spanY < minSpanY) {
			return bestXY;
		}

		// 开始循环遍历
		// y轴
		for (int y = 0; y < countY - (minSpanY - 1); y++) {
			inner:
			// x轴
			for (int x = 0; x < countX - (minSpanX - 1); x++) {
				int ySize = -1;
				int xSize = -1;
				if (ignoreOccupied) {
					// First, let's see if this thing fits anywhere
					// 如果这个东西适合任何位置
					for (int i = 0; i < minSpanX; i++) {
						for (int j = 0; j < minSpanY; j++) {
							// 根据目标的大小,判断固定范围内是否
							if (occupied[x + i][y + j]) {
								Log.i("ddv", "occupied[x + i][y + j]:"
										+ occupied[x + i][y + j]);
								Log.i("ddv", "[x + i]" + (x + i) + "[y + j]:"
										+ (y + j));
								// 如果此格被占用则返回到x轴的循环
								continue inner;
							}
						}
					}
					// 将空间最小宽高赋给xSize，ySize
					xSize = minSpanX;
					ySize = minSpanY;

					// We know that the item will fit at _some_ acceptable size,
					// now let's see
					// how big we can make it. We'll alternate between
					// incrementing x and y spans
					// until we hit a limit.
					boolean incX = true;
					boolean hitMaxX = xSize >= spanX;
					boolean hitMaxY = ySize >= spanY;
					while (!(hitMaxX && hitMaxY)) {
						if (incX && !hitMaxX) {
							for (int j = 0; j < ySize; j++) {
								if (x + xSize > countX - 1
										|| occupied[x + xSize][y + j]) {
									// We can't move out horizontally
									hitMaxX = true;
								}
							}
							if (!hitMaxX) {
								xSize++;
							}
						} else if (!hitMaxY) {
							for (int i = 0; i < xSize; i++) {
								if (y + ySize > countY - 1
										|| occupied[x + i][y + ySize]) {
									// We can't move out vertically
									hitMaxY = true;
								}
							}
							if (!hitMaxY) {
								ySize++;
							}
						}
						hitMaxX |= xSize >= spanX;
						hitMaxY |= ySize >= spanY;
						incX = !incX;
					}
					incX = true;
					hitMaxX = xSize >= spanX;
					hitMaxY = ySize >= spanY;
				}
				final int[] cellXY = mTmpXY;
				cellToCenterPoint(x, y, cellXY);// 此方法执行之后mTmpXY数据为选择区域的中心点

				// We verify that the current rect is not a sub-rect of any of
				// our previous
				// candidates. In this case, the current rect is disqualified in
				// favour of the
				// containing rect.
				/*
				 * 我们验证当前矩形，它不是我们以前任意的子矩形。 在这种情况下，当前的矩形会被取消为推荐的格子
				 */
				// 从堆中取出一个矩形
				Rect currentRect = mTempRectStack.pop();
				// 设置此矩形的参数
				currentRect.set(x, y, x + xSize, y + ySize);
				boolean contained = false;
				for (Rect r : validRegions) {
					if (r.contains(currentRect)) {
						contained = true;
						break;
					}
				}
				validRegions.push(currentRect);
				double distance = Math.sqrt(Math.pow(cellXY[0] - pixelX, 2)
						+ Math.pow(cellXY[1] - pixelY, 2));

				if ((distance <= bestDistance && !contained)
						|| currentRect.contains(bestRect)) {
					bestDistance = distance;
					bestXY[0] = x;
					bestXY[1] = y;
					if (resultSpan != null) {
						resultSpan[0] = xSize;
						resultSpan[1] = ySize;
					}
					bestRect.set(currentRect);
				}
			}
		}
		// re-mark space taken by ignoreView as occupied
		markCellsAsOccupiedForView(ignoreView, occupied);

		// Return -1, -1 if no suitable location found
		if (bestDistance == Double.MAX_VALUE) {
			bestXY[0] = -1;
			bestXY[1] = -1;
		}
		recycleTempRects(validRegions);
		return bestXY;
	}

	/**
	 * Find a vacant area that will fit the given bounds nearest the requested
	 * cell location, and will also weigh in a suggested direction vector of the
	 * desired location. This method computers distance based on unit grid
	 * distances, not pixel distances.
	 * 
	 * @param cellX
	 *            The X cell nearest to which you want to search for a vacant
	 *            area.
	 * @param cellY
	 *            The Y cell nearest which you want to search for a vacant area.
	 * @param spanX
	 *            Horizontal span of the object.
	 * @param spanY
	 *            Vertical span of the object.
	 * @param direction
	 *            The favored direction in which the views should move from x, y
	 * @param exactDirectionOnly
	 *            If this parameter is true, then only solutions where the
	 *            direction matches exactly. Otherwise we find the best matching
	 *            direction.
	 * @param occoupied
	 *            The array which represents which cells in the CellLayout are
	 *            occupied
	 * @param blockOccupied
	 *            The array which represents which cells in the specified block
	 *            (cellX, cellY, spanX, spanY) are occupied. This is used when
	 *            try to move a group of views.
	 * @param result
	 *            Array in which to place the result, or null (in which case a
	 *            new array will be allocated)
	 * @return The X, Y cell of a vacant area that can contain this object,
	 *         nearest the requested location.
	 */
	private int[] findNearestArea(int cellX, int cellY, int spanX, int spanY,
			int[] direction, boolean[][] occupied, boolean blockOccupied[][],
			int[] result) {
		// Keep track of best-scoring drop area
		final int[] bestXY = result != null ? result : new int[2];
		float bestDistance = Float.MAX_VALUE;
		int bestDirectionScore = Integer.MIN_VALUE;

		final int countX = mCountX;
		final int countY = mCountY;

		for (int y = 0; y < countY - (spanY - 1); y++) {
			inner: for (int x = 0; x < countX - (spanX - 1); x++) {
				// First, let's see if this thing fits anywhere
				for (int i = 0; i < spanX; i++) {
					for (int j = 0; j < spanY; j++) {
						if (occupied[x + i][y + j]
								&& (blockOccupied == null || blockOccupied[i][j])) {
							continue inner;
						}
					}
				}

				float distance = (float) Math.sqrt((x - cellX) * (x - cellX)
						+ (y - cellY) * (y - cellY));
				int[] curDirection = mTmpPoint;
				computeDirectionVector(x - cellX, y - cellY, curDirection);
				// The direction score is just the dot product of the two
				// candidate direction
				// and that passed in.
				int curDirectionScore = direction[0] * curDirection[0]
						+ direction[1] * curDirection[1];
				boolean exactDirectionOnly = false;
				boolean directionMatches = direction[0] == curDirection[0]
						&& direction[0] == curDirection[0];
				if ((directionMatches || !exactDirectionOnly)
						&& Float.compare(distance, bestDistance) < 0
						|| (Float.compare(distance, bestDistance) == 0 && curDirectionScore > bestDirectionScore)) {
					bestDistance = distance;
					bestDirectionScore = curDirectionScore;
					bestXY[0] = x;
					bestXY[1] = y;
				}
			}
		}

		// Return -1, -1 if no suitable location found
		if (bestDistance == Float.MAX_VALUE) {
			bestXY[0] = -1;
			bestXY[1] = -1;
		}
		return bestXY;
	}

	private int[] findNearestAreaInDirection(int cellX, int cellY, int spanX,
			int spanY, int[] direction, boolean[][] occupied,
			boolean blockOccupied[][], int[] result) {
		// Keep track of best-scoring drop area
		final int[] bestXY = result != null ? result : new int[2];
		bestXY[0] = -1;
		bestXY[1] = -1;
		float bestDistance = Float.MAX_VALUE;

		// We use this to march in a single direction
		if ((direction[0] != 0 && direction[1] != 0)
				|| (direction[0] == 0 && direction[1] == 0)) {
			return bestXY;
		}

		// This will only incrememnet one of x or y based on the assertion above
		int x = cellX + direction[0];
		int y = cellY + direction[1];
		while (x >= 0 && x + spanX <= mCountX && y >= 0 && y + spanY <= mCountY) {
			boolean fail = false;
			for (int i = 0; i < spanX; i++) {
				for (int j = 0; j < spanY; j++) {
					if (occupied[x + i][y + j]
							&& (blockOccupied == null || blockOccupied[i][j])) {
						fail = true;
					}
				}
			}
			if (!fail) {
				float distance = (float) Math.sqrt((x - cellX) * (x - cellX)
						+ (y - cellY) * (y - cellY));
				if (Float.compare(distance, bestDistance) < 0) {
					bestDistance = distance;
					bestXY[0] = x;
					bestXY[1] = y;
				}
			}
			x += direction[0];
			y += direction[1];
		}
		return bestXY;
	}

	private boolean addViewToTempLocation(View v,
			Rect rectOccupiedByPotentialDrop, int[] direction,
			ItemConfiguration currentState) {
		CellAndSpan c = currentState.map.get(v);
		boolean success = false;
		markCellsForView(c.x, c.y, c.spanX, c.spanY, mTmpOccupied, false);
		markCellsForRect(rectOccupiedByPotentialDrop, mTmpOccupied, true);

		findNearestArea(c.x, c.y, c.spanX, c.spanY, direction, mTmpOccupied,
				null, mTempLocation);

		if (mTempLocation[0] >= 0 && mTempLocation[1] >= 0) {
			c.x = mTempLocation[0];
			c.y = mTempLocation[1];
			success = true;

		}
		markCellsForView(c.x, c.y, c.spanX, c.spanY, mTmpOccupied, true);
		return success;
	}

	// This method looks in the specified direction to see if there are
	// additional views adjacent
	// to the current set of views. If there are, then these views are added to
	// the current
	// set of views. This is performed iteratively, giving a cascading push
	// behaviour.
	private boolean addViewInDirection(ArrayList<View> views,
			Rect boundingRect, int[] direction, boolean[][] occupied,
			View dragView, ItemConfiguration currentState) {
		boolean found = false;

		int childCount = mShortcutsAndWidgets.getChildCount();
		Rect r0 = new Rect(boundingRect);
		Rect r1 = new Rect();

		// First, we consider the rect of the views that we are trying to
		// translate
		int deltaX = 0;
		int deltaY = 0;
		if (direction[1] < 0) {
			r0.set(r0.left, r0.top - 1, r0.right, r0.bottom - 1);
			deltaY = -1;
		} else if (direction[1] > 0) {
			r0.set(r0.left, r0.top + 1, r0.right, r0.bottom + 1);
			deltaY = 1;
		} else if (direction[0] < 0) {
			r0.set(r0.left - 1, r0.top, r0.right - 1, r0.bottom);
			deltaX = -1;
		} else if (direction[0] > 0) {
			r0.set(r0.left + 1, r0.top, r0.right + 1, r0.bottom);
			deltaX = 1;
		}

		// Now we see which views, if any, are being overlapped by shifting the
		// current group
		// of views in the desired direction.
		for (int i = 0; i < childCount; i++) {
			// We don't need to worry about views already in our group, or the
			// current drag view.
			View child = mShortcutsAndWidgets.getChildAt(i);
			if (views.contains(child) || child == dragView)
				continue;
			CellAndSpan c = currentState.map.get(child);

			LayoutParams lp = (LayoutParams) child.getLayoutParams();
			r1.set(c.x, c.y, c.x + c.spanX, c.y + c.spanY);
			if (Rect.intersects(r0, r1)) {
				if (!lp.canReorder) {
					return false;
				}
				// First we verify that the view in question is at the border of
				// the extents
				// of the block of items we are pushing
				if ((direction[0] < 0 && c.x == r0.left)
						|| (direction[0] > 0 && c.x == r0.right - 1)
						|| (direction[1] < 0 && c.y == r0.top)
						|| (direction[1] > 0 && c.y == r0.bottom - 1)) {
					boolean pushed = false;
					// Since the bounding rect is a coarse description of the
					// region (there can
					// be holes at the edge of the block), we need to check to
					// verify that a solid
					// piece is intersecting. This ensures that interlocking is
					// possible.
					for (int x = c.x; x < c.x + c.spanX; x++) {
						for (int y = c.y; y < c.y + c.spanY; y++) {
							if (occupied[x - deltaX][y - deltaY]) {
								pushed = true;
								break;
							}
							if (pushed)
								break;
						}
					}
					if (pushed) {
						views.add(child);
						boundingRect.union(c.x, c.y, c.x + c.spanX, c.y
								+ c.spanY);
						found = true;
					}
				}
			}
		}
		return found;
	}

	private void completeSetOfViewsToMove(ArrayList<View> views,
			Rect boundingRect, int[] direction, boolean[][] occupied,
			View dragView, ItemConfiguration currentState) {
		Rect r0 = new Rect(boundingRect);
		int minRuns = 0;

		// The first thing we do is to reduce the bounding rect to first or last
		// row or column,
		// depending on the direction. Then, we add any necessary views that are
		// already contained
		// by the bounding rect, but aren't in the list of intersecting views,
		// and will be pushed
		// by something already in the intersecting views.
		if (direction[1] < 0) {
			r0.set(r0.left, r0.bottom - 1, r0.right, r0.bottom);
		} else if (direction[1] > 0) {
			r0.set(r0.left, r0.top, r0.right, r0.top + 1);
		} else if (direction[0] < 0) {
			r0.set(r0.right - 1, r0.top, r0.right, r0.bottom);
		} else if (direction[0] > 0) {
			r0.set(r0.left, r0.top, r0.left + 1, r0.bottom);
		}

		minRuns = Math.max(Math.abs(boundingRect.width() - r0.width()),
				Math.abs(boundingRect.height() - r0.height())) + 1;

		// Here the first number of runs (minRuns) accounts for the the comment
		// above, and
		// further runs execute based on whether the intersecting views /
		// bounding rect need
		// to be expanded to include other views that will be pushed.
		while (addViewInDirection(views, r0, direction, mTmpOccupied, dragView,
				currentState) || minRuns > 0) {
			minRuns--;
		}
		boundingRect.union(r0);
	}

	private boolean addViewsToTempLocation(ArrayList<View> views,
			Rect rectOccupiedByPotentialDrop, int[] direction, boolean push,
			View dragView, ItemConfiguration currentState) {
		if (views.size() == 0)
			return true;

		boolean success = false;
		Rect boundingRect = null;
		// We construct a rect which represents the entire group of views passed
		// in
		for (View v : views) {
			CellAndSpan c = currentState.map.get(v);
			if (boundingRect == null) {
				boundingRect = new Rect(c.x, c.y, c.x + c.spanX, c.y + c.spanY);
			} else {
				boundingRect.union(c.x, c.y, c.x + c.spanX, c.y + c.spanY);
			}
		}

		@SuppressWarnings("unchecked")
		ArrayList<View> dup = (ArrayList<View>) views.clone();
		if (push) {
			completeSetOfViewsToMove(dup, boundingRect, direction,
					mTmpOccupied, dragView, currentState);
		}

		// Mark the occupied state as false for the group of views we want to
		// move.
		for (View v : dup) {
			CellAndSpan c = currentState.map.get(v);
			markCellsForView(c.x, c.y, c.spanX, c.spanY, mTmpOccupied, false);
		}

		boolean[][] blockOccupied = new boolean[boundingRect.width()][boundingRect
				.height()];
		int top = boundingRect.top;
		int left = boundingRect.left;
		// We mark more precisely which parts of the bounding rect are truly
		// occupied, allowing
		// for interlocking.
		for (View v : dup) {
			CellAndSpan c = currentState.map.get(v);
			markCellsForView(c.x - left, c.y - top, c.spanX, c.spanY,
					blockOccupied, true);
		}

		markCellsForRect(rectOccupiedByPotentialDrop, mTmpOccupied, true);

		if (push) {
			findNearestAreaInDirection(boundingRect.left, boundingRect.top,
					boundingRect.width(), boundingRect.height(), direction,
					mTmpOccupied, blockOccupied, mTempLocation);
		} else {
			findNearestArea(boundingRect.left, boundingRect.top,
					boundingRect.width(), boundingRect.height(), direction,
					mTmpOccupied, blockOccupied, mTempLocation);
		}

		// If we successfuly found a location by pushing the block of views, we
		// commit it
		if (mTempLocation[0] >= 0 && mTempLocation[1] >= 0) {
			int deltaX = mTempLocation[0] - boundingRect.left;
			int deltaY = mTempLocation[1] - boundingRect.top;
			for (View v : dup) {
				CellAndSpan c = currentState.map.get(v);
				c.x += deltaX;
				c.y += deltaY;
			}
			success = true;
		}

		// In either case, we set the occupied array as marked for the location
		// of the views
		for (View v : dup) {
			CellAndSpan c = currentState.map.get(v);
			markCellsForView(c.x, c.y, c.spanX, c.spanY, mTmpOccupied, true);
		}
		return success;
	}

	private void markCellsForRect(Rect r, boolean[][] occupied, boolean value) {
		markCellsForView(r.left, r.top, r.width(), r.height(), occupied, value);
	}

	// This method tries to find a reordering solution which satisfies the push
	// mechanic by trying
	// to push items in each of the cardinal directions, in an order based on
	// the direction vector
	// passed.
	private boolean attemptPushInDirection(ArrayList<View> intersectingViews,
			Rect occupied, int[] direction, View ignoreView,
			ItemConfiguration solution) {
		if ((Math.abs(direction[0]) + Math.abs(direction[1])) > 1) {
			// If the direction vector has two non-zero components, we try
			// pushing
			// separately in each of the components.
			int temp = direction[1];
			direction[1] = 0;
			if (addViewsToTempLocation(intersectingViews, occupied, direction,
					true, ignoreView, solution)) {
				return true;
			}
			direction[1] = temp;
			temp = direction[0];
			direction[0] = 0;
			if (addViewsToTempLocation(intersectingViews, occupied, direction,
					true, ignoreView, solution)) {
				return true;
			}
			// Revert the direction
			direction[0] = temp;

			// Now we try pushing in each component of the opposite direction
			direction[0] *= -1;
			direction[1] *= -1;
			temp = direction[1];
			direction[1] = 0;
			if (addViewsToTempLocation(intersectingViews, occupied, direction,
					true, ignoreView, solution)) {
				return true;
			}

			direction[1] = temp;
			temp = direction[0];
			direction[0] = 0;
			if (addViewsToTempLocation(intersectingViews, occupied, direction,
					true, ignoreView, solution)) {
				return true;
			}
			// revert the direction
			direction[0] = temp;
			direction[0] *= -1;
			direction[1] *= -1;

		} else {
			// If the direction vector has a single non-zero component, we push
			// first in the
			// direction of the vector
			if (addViewsToTempLocation(intersectingViews, occupied, direction,
					true, ignoreView, solution)) {
				return true;
			}

			// Then we try the opposite direction
			direction[0] *= -1;
			direction[1] *= -1;
			if (addViewsToTempLocation(intersectingViews, occupied, direction,
					true, ignoreView, solution)) {
				return true;
			}
			// Switch the direction back
			direction[0] *= -1;
			direction[1] *= -1;

			// If we have failed to find a push solution with the above, then we
			// try
			// to find a solution by pushing along the perpendicular axis.

			// Swap the components
			int temp = direction[1];
			direction[1] = direction[0];
			direction[0] = temp;
			if (addViewsToTempLocation(intersectingViews, occupied, direction,
					true, ignoreView, solution)) {
				return true;
			}

			// Then we try the opposite direction
			direction[0] *= -1;
			direction[1] *= -1;
			if (addViewsToTempLocation(intersectingViews, occupied, direction,
					true, ignoreView, solution)) {
				return true;
			}
			// Switch the direction back
			direction[0] *= -1;
			direction[1] *= -1;

			// Swap the components back
			temp = direction[1];
			direction[1] = direction[0];
			direction[0] = temp;
		}
		return false;
	}

	private boolean rearrangementExists(int cellX, int cellY, int spanX,
			int spanY, int[] direction, View ignoreView,
			ItemConfiguration solution) {
		// Return early if get invalid cell positions
		if (cellX < 0 || cellY < 0)
			return false;

		mIntersectingViews.clear();
		mOccupiedRect.set(cellX, cellY, cellX + spanX, cellY + spanY);

		// Mark the desired location of the view currently being dragged.
		if (ignoreView != null) {
			CellAndSpan c = solution.map.get(ignoreView);
			if (c != null) {
				c.x = cellX;
				c.y = cellY;
			}
		}
		Rect r0 = new Rect(cellX, cellY, cellX + spanX, cellY + spanY);
		Rect r1 = new Rect();
		for (View child : solution.map.keySet()) {
			if (child == ignoreView)
				continue;
			CellAndSpan c = solution.map.get(child);
			LayoutParams lp = (LayoutParams) child.getLayoutParams();
			r1.set(c.x, c.y, c.x + c.spanX, c.y + c.spanY);
			if (Rect.intersects(r0, r1)) {
				if (!lp.canReorder) {
					return false;
				}
				mIntersectingViews.add(child);
			}
		}

		// First we try to find a solution which respects the push mechanic.
		// That is,
		// we try to find a solution such that no displaced item travels through
		// another item
		// without also displacing that item.
		if (attemptPushInDirection(mIntersectingViews, mOccupiedRect,
				direction, ignoreView, solution)) {
			return true;
		}

		// Next we try moving the views as a block, but without requiring the
		// push mechanic.
		if (addViewsToTempLocation(mIntersectingViews, mOccupiedRect,
				direction, false, ignoreView, solution)) {
			return true;
		}

		// Ok, they couldn't move as a block, let's move them individually
		for (View v : mIntersectingViews) {
			if (!addViewToTempLocation(v, mOccupiedRect, direction, solution)) {
				return false;
			}
		}
		return true;
	}

	/*
	 * Returns a pair (x, y), where x,y are in {-1, 0, 1} corresponding to
	 * vector between the provided point and the provided cell
	 */
	private void computeDirectionVector(float deltaX, float deltaY, int[] result) {
		double angle = Math.atan(((float) deltaY) / deltaX);

		result[0] = 0;
		result[1] = 0;
		if (Math.abs(Math.cos(angle)) > 0.5f) {
			result[0] = (int) Math.signum(deltaX);
		}
		if (Math.abs(Math.sin(angle)) > 0.5f) {
			result[1] = (int) Math.signum(deltaY);
		}
	}

	private void copyOccupiedArray(boolean[][] occupied) {
		for (int i = 0; i < mCountX; i++) {
			for (int j = 0; j < mCountY; j++) {
				occupied[i][j] = mOccupied[i][j];
			}
		}
	}

	ItemConfiguration simpleSwap(int pixelX, int pixelY, int minSpanX,
			int minSpanY, int spanX, int spanY, int[] direction, View dragView,
			boolean decX, ItemConfiguration solution) {
		// Copy the current state into the solution. This solution will be
		// manipulated as necessary.
		copyCurrentStateToSolution(solution, false);
		// Copy the current occupied array into the temporary occupied array.
		// This array will be
		// manipulated as necessary to find a solution.
		copyOccupiedArray(mTmpOccupied);

		// We find the nearest cell into which we would place the dragged item,
		// assuming there's
		// nothing in its way.
		int result[] = new int[2];
		result = findNearestArea(pixelX, pixelY, spanX, spanY, result);

		boolean success = false;
		// First we try the exact nearest position of the item being dragged,
		// we will then want to try to move this around to other neighbouring
		// positions
		success = rearrangementExists(result[0], result[1], spanX, spanY,
				direction, dragView, solution);

		if (!success) {
			// We try shrinking the widget down to size in an alternating
			// pattern, shrink 1 in
			// x, then 1 in y etc.
			if (spanX > minSpanX && (minSpanY == spanY || decX)) {
				return simpleSwap(pixelX, pixelY, minSpanX, minSpanY,
						spanX - 1, spanY, direction, dragView, false, solution);
			} else if (spanY > minSpanY) {
				return simpleSwap(pixelX, pixelY, minSpanX, minSpanY, spanX,
						spanY - 1, direction, dragView, true, solution);
			}
			solution.isSolution = false;
		} else {
			solution.isSolution = true;
			solution.dragViewX = result[0];
			solution.dragViewY = result[1];
			solution.dragViewSpanX = spanX;
			solution.dragViewSpanY = spanY;
		}
		return solution;
	}

	private void copyCurrentStateToSolution(ItemConfiguration solution,
			boolean temp) {
		int childCount = mShortcutsAndWidgets.getChildCount();
		for (int i = 0; i < childCount; i++) {
			View child = mShortcutsAndWidgets.getChildAt(i);
			LayoutParams lp = (LayoutParams) child.getLayoutParams();
			CellAndSpan c;
			if (temp) {
				c = new CellAndSpan(lp.tmpCellX, lp.tmpCellY, lp.cellHSpan,
						lp.cellVSpan);
			} else {
				c = new CellAndSpan(lp.cellX, lp.cellY, lp.cellHSpan,
						lp.cellVSpan);
			}
			solution.map.put(child, c);
		}
	}

	private void copySolutionToTempState(ItemConfiguration solution,
			View dragView) {
		for (int i = 0; i < mCountX; i++) {
			for (int j = 0; j < mCountY; j++) {
				mTmpOccupied[i][j] = false;
			}
		}

		int childCount = mShortcutsAndWidgets.getChildCount();
		for (int i = 0; i < childCount; i++) {
			View child = mShortcutsAndWidgets.getChildAt(i);
			if (child == dragView)
				continue;
			LayoutParams lp = (LayoutParams) child.getLayoutParams();
			CellAndSpan c = solution.map.get(child);
			if (c != null) {
				lp.tmpCellX = c.x;
				lp.tmpCellY = c.y;
				lp.cellHSpan = c.spanX;
				lp.cellVSpan = c.spanY;
				markCellsForView(c.x, c.y, c.spanX, c.spanY, mTmpOccupied, true);
			}
		}
		markCellsForView(solution.dragViewX, solution.dragViewY,
				solution.dragViewSpanX, solution.dragViewSpanY, mTmpOccupied,
				true);
	}

	private void animateItemsToSolution(ItemConfiguration solution,
			View dragView, boolean commitDragView) {

		boolean[][] occupied = DESTRUCTIVE_REORDER ? mOccupied : mTmpOccupied;
		for (int i = 0; i < mCountX; i++) {
			for (int j = 0; j < mCountY; j++) {
				occupied[i][j] = false;
			}
		}

		int childCount = mShortcutsAndWidgets.getChildCount();
		for (int i = 0; i < childCount; i++) {
			View child = mShortcutsAndWidgets.getChildAt(i);
			if (child == dragView)
				continue;
			CellAndSpan c = solution.map.get(child);
			if (c != null) {
				animateChildToPosition(child, c.x, c.y,
						REORDER_ANIMATION_DURATION, 0, DESTRUCTIVE_REORDER,
						false);
				markCellsForView(c.x, c.y, c.spanX, c.spanY, occupied, true);
			}
		}
		if (commitDragView) {
			markCellsForView(solution.dragViewX, solution.dragViewY,
					solution.dragViewSpanX, solution.dragViewSpanY, occupied,
					true);
		}
	}

	// This method starts or changes the reorder hint animations
	private void beginOrAdjustHintAnimations(ItemConfiguration solution,
			View dragView, int delay) {
		int childCount = mShortcutsAndWidgets.getChildCount();
		for (int i = 0; i < childCount; i++) {
			View child = mShortcutsAndWidgets.getChildAt(i);
			if (child == dragView)
				continue;
			CellAndSpan c = solution.map.get(child);
			LayoutParams lp = (LayoutParams) child.getLayoutParams();
			if (c != null) {
				ReorderHintAnimation rha = new ReorderHintAnimation(child,
						lp.cellX, lp.cellY, c.x, c.y, c.spanX, c.spanY);
				rha.animate();
			}
		}
	}

	// Class which represents the reorder hint animations. These animations show
	// that an item is
	// in a temporary state, and hint at where the item will return to.
	class ReorderHintAnimation {
		View child;
		float finalDeltaX;
		float finalDeltaY;
		float initDeltaX;
		float initDeltaY;
		float finalScale;
		float initScale;
		private static final int DURATION = 300;
		Animator a;

		public ReorderHintAnimation(View child, int cellX0, int cellY0,
				int cellX1, int cellY1, int spanX, int spanY) {
			regionToCenterPoint(cellX0, cellY0, spanX, spanY, mTmpPoint);
			final int x0 = mTmpPoint[0];
			final int y0 = mTmpPoint[1];
			regionToCenterPoint(cellX1, cellY1, spanX, spanY, mTmpPoint);
			final int x1 = mTmpPoint[0];
			final int y1 = mTmpPoint[1];
			final int dX = x1 - x0;
			final int dY = y1 - y0;
			finalDeltaX = 0;
			finalDeltaY = 0;
			if (dX == dY && dX == 0) {
			} else {
				if (dY == 0) {
					finalDeltaX = -Math.signum(dX)
							* mReorderHintAnimationMagnitude;
				} else if (dX == 0) {
					finalDeltaY = -Math.signum(dY)
							* mReorderHintAnimationMagnitude;
				} else {
					double angle = Math.atan((float) (dY) / dX);
					finalDeltaX = (int) (-Math.signum(dX) * Math.abs(Math
							.cos(angle) * mReorderHintAnimationMagnitude));
					finalDeltaY = (int) (-Math.signum(dY) * Math.abs(Math
							.sin(angle) * mReorderHintAnimationMagnitude));
				}
			}
			initDeltaX = child.getTranslationX();
			initDeltaY = child.getTranslationY();
			finalScale = getChildrenScale() - 4.0f / child.getWidth();
			initScale = child.getScaleX();
			this.child = child;
		}

		void animate() {
			if (mShakeAnimators.containsKey(child)) {
				ReorderHintAnimation oldAnimation = mShakeAnimators.get(child);
				oldAnimation.cancel();
				mShakeAnimators.remove(child);
				if (finalDeltaX == 0 && finalDeltaY == 0) {
					completeAnimationImmediately();
					return;
				}
			}
			if (finalDeltaX == 0 && finalDeltaY == 0) {
				return;
			}
			ValueAnimator va = LauncherAnimUtils.ofFloat(0f, 1f);
			a = va;
			va.setRepeatMode(ValueAnimator.REVERSE);
			va.setRepeatCount(ValueAnimator.INFINITE);
			va.setDuration(DURATION);
			va.setStartDelay((int) (Math.random() * 60));
			va.addUpdateListener(new AnimatorUpdateListener() {
				@Override
				public void onAnimationUpdate(ValueAnimator animation) {
					float r = ((Float) animation.getAnimatedValue())
							.floatValue();
					float x = r * finalDeltaX + (1 - r) * initDeltaX;
					float y = r * finalDeltaY + (1 - r) * initDeltaY;
					child.setTranslationX(x);
					child.setTranslationY(y);
					float s = r * finalScale + (1 - r) * initScale;
					child.setScaleX(s);
					child.setScaleY(s);
				}
			});
			va.addListener(new AnimatorListenerAdapter() {
				public void onAnimationRepeat(Animator animation) {
					// We make sure to end only after a full period
					initDeltaX = 0;
					initDeltaY = 0;
					initScale = getChildrenScale();
				}
			});
			mShakeAnimators.put(child, this);
			va.start();
		}

		private void cancel() {
			if (a != null) {
				a.cancel();
			}
		}

		private void completeAnimationImmediately() {
			if (a != null) {
				a.cancel();
			}

			AnimatorSet s = LauncherAnimUtils.createAnimatorSet();
			a = s;
			s.playTogether(LauncherAnimUtils.ofFloat(child, "scaleX",
					getChildrenScale()), LauncherAnimUtils.ofFloat(child,
					"scaleY", getChildrenScale()), LauncherAnimUtils.ofFloat(
					child, "translationX", 0f), LauncherAnimUtils.ofFloat(
					child, "translationY", 0f));
			s.setDuration(REORDER_ANIMATION_DURATION);
			s.setInterpolator(new android.view.animation.DecelerateInterpolator(
					1.5f));
			s.start();
		}
	}

	private void completeAndClearReorderHintAnimations() {
		for (ReorderHintAnimation a : mShakeAnimators.values()) {
			a.completeAnimationImmediately();
		}
		mShakeAnimators.clear();
	}

	private void commitTempPlacement() {
		for (int i = 0; i < mCountX; i++) {
			for (int j = 0; j < mCountY; j++) {
				mOccupied[i][j] = mTmpOccupied[i][j];
			}
		}
		int childCount = mShortcutsAndWidgets.getChildCount();
		for (int i = 0; i < childCount; i++) {
			View child = mShortcutsAndWidgets.getChildAt(i);
			LayoutParams lp = (LayoutParams) child.getLayoutParams();
			ItemInfo info = (ItemInfo) child.getTag();
			// We do a null check here because the item info can be null in the
			// case of the
			// AllApps button in the hotseat.
			if (info != null) {
				if (info.cellX != lp.tmpCellX || info.cellY != lp.tmpCellY
						|| info.spanX != lp.cellHSpan
						|| info.spanY != lp.cellVSpan) {
					info.requiresDbUpdate = true;
				}
				info.cellX = lp.cellX = lp.tmpCellX;
				info.cellY = lp.cellY = lp.tmpCellY;
				info.spanX = lp.cellHSpan;
				info.spanY = lp.cellVSpan;
			}
		}
		mLauncher.getWorkspace().updateItemLocationsInDatabase(this);
	}

	public void setUseTempCoords(boolean useTempCoords) {
		int childCount = mShortcutsAndWidgets.getChildCount();
		for (int i = 0; i < childCount; i++) {
			LayoutParams lp = (LayoutParams) mShortcutsAndWidgets.getChildAt(i)
					.getLayoutParams();
			lp.useTmpCoords = useTempCoords;
		}
	}

	ItemConfiguration findConfigurationNoShuffle(int pixelX, int pixelY,
			int minSpanX, int minSpanY, int spanX, int spanY, View dragView,
			ItemConfiguration solution) {
		int[] result = new int[2];
		int[] resultSpan = new int[2];
		findNearestVacantArea(pixelX, pixelY, minSpanX, minSpanY, spanX, spanY,
				null, result, resultSpan);
		if (result[0] >= 0 && result[1] >= 0) {
			copyCurrentStateToSolution(solution, false);
			solution.dragViewX = result[0];
			solution.dragViewY = result[1];
			solution.dragViewSpanX = resultSpan[0];
			solution.dragViewSpanY = resultSpan[1];
			solution.isSolution = true;
		} else {
			solution.isSolution = false;
		}
		return solution;
	}

	public void prepareChildForDrag(View child) {
		markCellsAsUnoccupiedForView(child);
	}

	/*
	 * This seems like it should be obvious and straight-forward, but when the
	 * direction vector needs to match with the notion of the dragView pushing
	 * other views, we have to employ a slightly more subtle notion of the
	 * direction vector. The question is what two points is the vector between?
	 * The center of the dragView and its desired destination? Not quite, as
	 * this doesn't necessarily coincide with the interaction of the dragView
	 * and items occupying those cells. Instead we use some heuristics to often
	 * lock the vector to up, down, left or right, which helps make pushing feel
	 * right.
	 */
	
	/**
	 * <h1> 用来得到下降的方向向量 </h1>
	 * 
	 * 这看起来应该是明显的和直接的，
	 * 但是方向向量需要配合DragView推动其他的View
	 * 因此我们要采用一个稍微更微妙的方向矢量的概念。
	 * 现在的问题是两点之间的向量？该中心的dragView和其所需的目的地呢？
	 * 不尽然因为这并不一定一致的dragView和项目占用的那些细胞的相互作用。
	 * 相反，我们使用一些启发方式，往往锁定向量向上，向下，向左或向右，这有助于解决推动的不对劲。
	 * 
	 * @param dragViewCenterX dragView中心x
	 * @param dragViewCenterY dragView中心y
	 * @param spanX dragView宽跨度
	 * @param spanY dragView高跨度
	 * @param dragView 拖动的对象
	 * @param resultDirection 返回向量结果保存在该数组中
	 */
	private void getDirectionVectorForDrop(int dragViewCenterX,
			int dragViewCenterY, int spanX, int spanY, View dragView,
			int[] resultDirection) {
		int[] targetDestination = new int[2];

		findNearestArea(dragViewCenterX, dragViewCenterY, spanX, spanY,
				targetDestination);
		Rect dragRect = new Rect();
		//创建相应矩形
		regionToRect(targetDestination[0], targetDestination[1], spanX, spanY,
				dragRect);
		dragRect.offset(dragViewCenterX - dragRect.centerX(), dragViewCenterY
				- dragRect.centerY());

		Rect dropRegionRect = new Rect();
		//设定边界矩形，记录相交的View
		getViewsIntersectingRegion(targetDestination[0], targetDestination[1],
				spanX, spanY, dragView, dropRegionRect, mIntersectingViews);

		// 得到边界矩形的宽高
		int dropRegionSpanX = dropRegionRect.width();
		int dropRegionSpanY = dropRegionRect.height();

		
		regionToRect(dropRegionRect.left, dropRegionRect.top,
				dropRegionRect.width(), dropRegionRect.height(), dropRegionRect);
		
		int deltaX = (dropRegionRect.centerX() - dragViewCenterX) / spanX;
		int deltaY = (dropRegionRect.centerY() - dragViewCenterY) / spanY;

		if (dropRegionSpanX == mCountX || spanX == mCountX) {
			deltaX = 0;
		}
		if (dropRegionSpanY == mCountY || spanY == mCountY) {
			deltaY = 0;
		}

		if (deltaX == 0 && deltaY == 0) {
			// No idea what to do, give a random direction.
			// 不知道该怎么办，给予一个随机方向
			resultDirection[0] = 1;
			resultDirection[1] = 0;
		} else {
			computeDirectionVector(deltaX, deltaY, resultDirection);
		}
	}

	// For a given cell and span, fetch the set of views intersecting the
	// region.
	/**
	 * 对于给定的单元格和跨度，取集Views相交的区域
	 * 
	 * @param cellX 指定的格子x
	 * @param cellY 指定的格子y
	 * @param spanX x跨度
	 * @param spanY y跨度
	 * @param dragView 拖动的View
	 * @param boundingRect 边界矩形
	 * @param intersectingViews 所相交的view
	 * 
	 */
	private void getViewsIntersectingRegion(int cellX, int cellY, int spanX,
			int spanY, View dragView, Rect boundingRect,
			ArrayList<View> intersectingViews) {
		//设定区域大小的矩形
		if (boundingRect != null) {
			boundingRect.set(cellX, cellY, cellX + spanX, cellY + spanY);
		}
		//清空集合
		intersectingViews.clear();
		//创建比较的矩形r0为原型
		Rect r0 = new Rect(cellX, cellY, cellX + spanX, cellY + spanY);
		Rect r1 = new Rect();
		//获得当前mShortcutsAndWidgets中子控件的数量
		final int count = mShortcutsAndWidgets.getChildCount();
		//循环比较
		for (int i = 0; i < count; i++) {
			//得到子控件
			View child = mShortcutsAndWidgets.getChildAt(i);
			//如果子控件为拖动的控件则跳过
			if (child == dragView)
				continue;
			//得到子控件的大小
			LayoutParams lp = (LayoutParams) child.getLayoutParams();
			//将子控件的大小设置给r1
			r1.set(lp.cellX, lp.cellY, lp.cellX + lp.cellHSpan, lp.cellY
					+ lp.cellVSpan);
			// 比较两个矩形是否有交集
			if (Rect.intersects(r0, r1)) {
				//如果有则将此View添加到集合中
				mIntersectingViews.add(child);
				if (boundingRect != null) {
					//如果边界矩形不为空，合并此矩形。将边界矩形扩大
					boundingRect.union(r1);
				}
			}
		}
	}

	boolean isNearestDropLocationOccupied(int pixelX, int pixelY, int spanX,
			int spanY, View dragView, int[] result) {
		result = findNearestArea(pixelX, pixelY, spanX, spanY, result);
		getViewsIntersectingRegion(result[0], result[1], spanX, spanY,
				dragView, null, mIntersectingViews);
		return !mIntersectingViews.isEmpty();
	}

	/**
	 * 重置图标的位置
	 */
	void revertTempState() {
		if (!isItemPlacementDirty() || DESTRUCTIVE_REORDER)
			return;
		final int count = mShortcutsAndWidgets.getChildCount();
		for (int i = 0; i < count; i++) {
			View child = mShortcutsAndWidgets.getChildAt(i);
			LayoutParams lp = (LayoutParams) child.getLayoutParams();
			if (lp.tmpCellX != lp.cellX || lp.tmpCellY != lp.cellY) {
				lp.tmpCellX = lp.cellX;
				lp.tmpCellY = lp.cellY;
				animateChildToPosition(child, lp.cellX, lp.cellY,
						REORDER_ANIMATION_DURATION, 0, false, false);
			}
		}
		completeAndClearReorderHintAnimations();
		setItemPlacementDirty(false);
	}

	boolean createAreaForResize(int cellX, int cellY, int spanX, int spanY,
			View dragView, int[] direction, boolean commit) {
		int[] pixelXY = new int[2];
		regionToCenterPoint(cellX, cellY, spanX, spanY, pixelXY);

		// First we determine if things have moved enough to cause a different
		// layout
		ItemConfiguration swapSolution = simpleSwap(pixelXY[0], pixelXY[1],
				spanX, spanY, spanX, spanY, direction, dragView, true,
				new ItemConfiguration());

		setUseTempCoords(true);
		if (swapSolution != null && swapSolution.isSolution) {
			// If we're just testing for a possible location (MODE_ACCEPT_DROP),
			// we don't bother
			// committing anything or animating anything as we just want to
			// determine if a solution
			// exists
			copySolutionToTempState(swapSolution, dragView);
			setItemPlacementDirty(true);
			animateItemsToSolution(swapSolution, dragView, commit);

			if (commit) {
				commitTempPlacement();
				completeAndClearReorderHintAnimations();
				setItemPlacementDirty(false);
			} else {
				beginOrAdjustHintAnimations(swapSolution, dragView,
						REORDER_ANIMATION_DURATION);
			}
			mShortcutsAndWidgets.requestLayout();
		}
		return swapSolution.isSolution;
	}

	/**
	 * 
	 * @param pixelX
	 * @param pixelY
	 * @param minSpanX
	 * @param minSpanY
	 * @param spanX
	 * @param spanY
	 * @param dragView
	 * @param result
	 * @param resultSpan
	 * @param mode
	 * @return
	 */
	int[] createArea(int pixelX, int pixelY, int minSpanX, int minSpanY,
			int spanX, int spanY, View dragView, int[] result,
			int resultSpan[], int mode) {
		// First we determine if things have moved enough to cause a different
		// layout
		// 首先我们确定,如果认为已经移动到足以引起不同布局的事情.
		// 得到最适合的格子坐标
		result = findNearestArea(pixelX, pixelY, spanX, spanY, result);

		if (resultSpan == null) {
			resultSpan = new int[2];
		}

		// When we are checking drop validity or actually dropping, we don't
		// recompute the
		// direction vector, since we want the solution to match the preview,
		// and it's possible
		// that the exact position of the item has changed to result in a new
		// reordering outcome.
		/*
		 * 当我们正在检查下降有效性或实际下降，我们不重新计算方向向量。因为我们希望解决
		 * 相匹配的预览图，可能他的确切位置已经改变，导致一个新的重新排序结果
		 */
		if ((mode == MODE_ON_DROP || mode == MODE_ON_DROP_EXTERNAL || mode == MODE_ACCEPT_DROP)
				&& mPreviousReorderDirection[0] != INVALID_DIRECTION) {
			mDirectionVector[0] = mPreviousReorderDirection[0];
			mDirectionVector[1] = mPreviousReorderDirection[1];
			// We reset this vector after drop
			if (mode == MODE_ON_DROP || mode == MODE_ON_DROP_EXTERNAL) {
				mPreviousReorderDirection[0] = INVALID_DIRECTION;
				mPreviousReorderDirection[1] = INVALID_DIRECTION;
			}
		} else {
			getDirectionVectorForDrop(pixelX, pixelY, spanX, spanY, dragView,
					mDirectionVector);
			mPreviousReorderDirection[0] = mDirectionVector[0];
			mPreviousReorderDirection[1] = mDirectionVector[1];
		}

		ItemConfiguration swapSolution = simpleSwap(pixelX, pixelY, minSpanX,
				minSpanY, spanX, spanY, mDirectionVector, dragView, true,
				new ItemConfiguration());

		// We attempt the approach which doesn't shuffle views at all
		ItemConfiguration noShuffleSolution = findConfigurationNoShuffle(
				pixelX, pixelY, minSpanX, minSpanY, spanX, spanY, dragView,
				new ItemConfiguration());

		ItemConfiguration finalSolution = null;
		if (swapSolution.isSolution
				&& swapSolution.area() >= noShuffleSolution.area()) {
			finalSolution = swapSolution;
		} else if (noShuffleSolution.isSolution) {
			finalSolution = noShuffleSolution;
		}

		boolean foundSolution = true;
		if (!DESTRUCTIVE_REORDER) {
			setUseTempCoords(true);
		}

		if (finalSolution != null) {
			result[0] = finalSolution.dragViewX;
			result[1] = finalSolution.dragViewY;
			resultSpan[0] = finalSolution.dragViewSpanX;
			resultSpan[1] = finalSolution.dragViewSpanY;

			// If we're just testing for a possible location (MODE_ACCEPT_DROP),
			// we don't bother
			// committing anything or animating anything as we just want to
			// determine if a solution
			// exists
			if (mode == MODE_DRAG_OVER || mode == MODE_ON_DROP
					|| mode == MODE_ON_DROP_EXTERNAL) {
				if (!DESTRUCTIVE_REORDER) {
					copySolutionToTempState(finalSolution, dragView);
				}
				setItemPlacementDirty(true);
				animateItemsToSolution(finalSolution, dragView,
						mode == MODE_ON_DROP);

				if (!DESTRUCTIVE_REORDER
						&& (mode == MODE_ON_DROP || mode == MODE_ON_DROP_EXTERNAL)) {
					commitTempPlacement();
					completeAndClearReorderHintAnimations();
					setItemPlacementDirty(false);
				} else {
					beginOrAdjustHintAnimations(finalSolution, dragView,
							REORDER_ANIMATION_DURATION);
				}
			}
		} else {
			foundSolution = false;
			result[0] = result[1] = resultSpan[0] = resultSpan[1] = -1;
		}

		if ((mode == MODE_ON_DROP || !foundSolution) && !DESTRUCTIVE_REORDER) {
			setUseTempCoords(false);
		}

		mShortcutsAndWidgets.requestLayout();
		return result;
	}

	void setItemPlacementDirty(boolean dirty) {
		mItemPlacementDirty = dirty;
	}

	boolean isItemPlacementDirty() {
		return mItemPlacementDirty;
	}

	/**
	 * 类中有HashMap<View, CellAndSpan> map。用来存放View对应的格子和跨度信息
	 * 
	 * @author Administrator
	 * 
	 */
	private class ItemConfiguration {
		HashMap<View, CellAndSpan> map = new HashMap<View, CellAndSpan>();
		boolean isSolution = false;
		int dragViewX, dragViewY, dragViewSpanX, dragViewSpanY;

		int area() {
			return dragViewSpanX * dragViewSpanY;
		}
	}

	/**
	 * 用来存放位置和跨度
	 * 
	 * @author Administrator
	 * 
	 */
	private class CellAndSpan {
		int x, y;
		int spanX, spanY;

		public CellAndSpan(int x, int y, int spanX, int spanY) {
			this.x = x;
			this.y = y;
			this.spanX = spanX;
			this.spanY = spanY;
		}
	}

	/**
	 * Find a vacant area that will fit the given bounds nearest the requested
	 * cell location. Uses Euclidean distance to score multiple vacant areas.
	 * 
	 * @param pixelX
	 *            The X location at which you want to search for a vacant area.
	 * @param pixelY
	 *            The Y location at which you want to search for a vacant area.
	 * @param spanX
	 *            Horizontal span of the object.
	 * @param spanY
	 *            Vertical span of the object.
	 * @param ignoreView
	 *            Considers space occupied by this view as unoccupied
	 * @param result
	 *            Previously returned value to possibly recycle.
	 * @return The X, Y cell of a vacant area that can contain this object,
	 *         nearest the requested location.
	 */
	int[] findNearestVacantArea(int pixelX, int pixelY, int spanX, int spanY,
			View ignoreView, int[] result) {
		return findNearestArea(pixelX, pixelY, spanX, spanY, ignoreView, true,
				result);
	}

	/**
	 * Find a vacant area that will fit the given bounds nearest the requested
	 * cell location. Uses Euclidean distance to score multiple vacant areas.
	 * 
	 * @param pixelX
	 *            The X location at which you want to search for a vacant area.
	 * @param pixelY
	 *            The Y location at which you want to search for a vacant area.
	 * @param minSpanX
	 *            The minimum horizontal span required
	 * @param minSpanY
	 *            The minimum vertical span required
	 * @param spanX
	 *            Horizontal span of the object.
	 * @param spanY
	 *            Vertical span of the object.
	 * @param ignoreView
	 *            Considers space occupied by this view as unoccupied
	 * @param result
	 *            Previously returned value to possibly recycle.
	 * @return The X, Y cell of a vacant area that can contain this object,
	 *         nearest the requested location.
	 */
	int[] findNearestVacantArea(int pixelX, int pixelY, int minSpanX,
			int minSpanY, int spanX, int spanY, View ignoreView, int[] result,
			int[] resultSpan) {
		return findNearestArea(pixelX, pixelY, minSpanX, minSpanY, spanX,
				spanY, ignoreView, true, result, resultSpan, mOccupied);
	}

	/**
	 * Find a starting cell position that will fit the given bounds nearest the
	 * requested cell location. Uses Euclidean distance to score multiple vacant
	 * areas. 查找起始单元格位置，将合适给定边界最接近要求的单元格位置.
	 * 
	 * @param pixelX
	 *            The X location at which you want to search for a vacant area.
	 * @param pixelY
	 *            The Y location at which you want to search for a vacant area.
	 * @param spanX
	 *            Horizontal span of the object.
	 * @param spanY
	 *            Vertical span of the object.
	 * @param ignoreView
	 *            Considers space occupied by this view as unoccupied
	 * @param result
	 *            Previously returned value to possibly recycle.
	 * @return The X, Y cell of a vacant area that can contain this object,
	 *         nearest the requested location.
	 */
	int[] findNearestArea(int pixelX, int pixelY, int spanX, int spanY,
			int[] result) {
		return findNearestArea(pixelX, pixelY, spanX, spanY, null, false,
				result);
	}

	boolean existsEmptyCell() {
		return findCellForSpan(null, 1, 1);
	}

	/**
	 * Finds the upper-left coordinate of the first rectangle in the grid that
	 * can hold a cell of the specified dimensions. If intersectX and intersectY
	 * are not -1, then this method will only return coordinates for rectangles
	 * that contain the cell (intersectX, intersectY)
	 * 
	 * @param cellXY
	 *            The array that will contain the position of a vacant cell if
	 *            such a cell can be found.
	 * @param spanX
	 *            The horizontal span of the cell we want to find.
	 * @param spanY
	 *            The vertical span of the cell we want to find.
	 * 
	 * @return True if a vacant cell of the specified dimension was found, false
	 *         otherwise.
	 */
	public boolean findCellForSpan(int[] cellXY, int spanX, int spanY) {
		return findCellForSpanThatIntersectsIgnoring(cellXY, spanX, spanY, -1,
				-1, null, mOccupied);
	}

	/**
	 * Like above, but ignores any cells occupied by the item "ignoreView"
	 * 
	 * @param cellXY
	 *            The array that will contain the position of a vacant cell if
	 *            such a cell can be found.
	 * @param spanX
	 *            The horizontal span of the cell we want to find.
	 * @param spanY
	 *            The vertical span of the cell we want to find.
	 * @param ignoreView
	 *            The home screen item we should treat as not occupying any
	 *            space
	 * @return
	 */
	boolean findCellForSpanIgnoring(int[] cellXY, int spanX, int spanY,
			View ignoreView) {
		return findCellForSpanThatIntersectsIgnoring(cellXY, spanX, spanY, -1,
				-1, ignoreView, mOccupied);
	}

	/**
	 * Like above, but if intersectX and intersectY are not -1, then this method
	 * will try to return coordinates for rectangles that contain the cell
	 * [intersectX, intersectY]
	 * 
	 * @param spanX
	 *            The horizontal span of the cell we want to find.
	 * @param spanY
	 *            The vertical span of the cell we want to find.
	 * @param ignoreView
	 *            The home screen item we should treat as not occupying any
	 *            space
	 * @param intersectX
	 *            The X coordinate of the cell that we should try to overlap
	 * @param intersectX
	 *            The Y coordinate of the cell that we should try to overlap
	 * 
	 * @return True if a vacant cell of the specified dimension was found, false
	 *         otherwise.
	 */
	boolean findCellForSpanThatIntersects(int[] cellXY, int spanX, int spanY,
			int intersectX, int intersectY) {
		return findCellForSpanThatIntersectsIgnoring(cellXY, spanX, spanY,
				intersectX, intersectY, null, mOccupied);
	}

	/**
	 * The superset of the above two methods
	 */
	boolean findCellForSpanThatIntersectsIgnoring(int[] cellXY, int spanX,
			int spanY, int intersectX, int intersectY, View ignoreView,
			boolean occupied[][]) {
		// mark space take by ignoreView as available (method checks if
		// ignoreView is null)
		markCellsAsUnoccupiedForView(ignoreView, occupied);

		boolean foundCell = false;
		while (true) {
			int startX = 0;
			if (intersectX >= 0) {
				startX = Math.max(startX, intersectX - (spanX - 1));
			}
			int endX = mCountX - (spanX - 1);
			if (intersectX >= 0) {
				endX = Math.min(endX, intersectX + (spanX - 1)
						+ (spanX == 1 ? 1 : 0));
			}
			int startY = 0;
			if (intersectY >= 0) {
				startY = Math.max(startY, intersectY - (spanY - 1));
			}
			int endY = mCountY - (spanY - 1);
			if (intersectY >= 0) {
				endY = Math.min(endY, intersectY + (spanY - 1)
						+ (spanY == 1 ? 1 : 0));
			}

			for (int y = startY; y < endY && !foundCell; y++) {
				inner: for (int x = startX; x < endX; x++) {
					for (int i = 0; i < spanX; i++) {
						for (int j = 0; j < spanY; j++) {
							if (occupied[x + i][y + j]) {
								// small optimization: we can skip to after the
								// column we just found
								// an occupied cell
								x += i;
								continue inner;
							}
						}
					}
					if (cellXY != null) {
						cellXY[0] = x;
						cellXY[1] = y;
					}
					foundCell = true;
					break;
				}
			}
			if (intersectX == -1 && intersectY == -1) {
				break;
			} else {
				// if we failed to find anything, try again but without any
				// requirements of
				// intersecting
				intersectX = -1;
				intersectY = -1;
				continue;
			}
		}

		// re-mark space taken by ignoreView as occupied
		markCellsAsOccupiedForView(ignoreView, occupied);
		return foundCell;
	}

	/**
	 * A drag event has begun over this layout. It may have begun over this
	 * layout (in which case onDragChild is called first), or it may have begun
	 * on another layout.
	 */
	void onDragEnter() {
		mDragEnforcer.onDragEnter();
		mDragging = true;
	}

	/**
	 * Called when drag has left this CellLayout or has been completed
	 * (successfully or not)
	 */
	void onDragExit() {
		mDragEnforcer.onDragExit();
		// This can actually be called when we aren't in a drag, e.g. when
		// adding a new
		// item to this layout via the customize drawer.
		// Guard against that case.
		if (mDragging) {
			mDragging = false;
		}

		// Invalidate the drag data
		mDragCell[0] = mDragCell[1] = -1;
		mDragOutlineAnims[mDragOutlineCurrent].animateOut();
		mDragOutlineCurrent = (mDragOutlineCurrent + 1)
				% mDragOutlineAnims.length;
		revertTempState();
		setIsDragOverlapping(false);
	}

	/**
	 * Mark a child as having been dropped. At the beginning of the drag
	 * operation, the child may have been on another screen, but it is
	 * re-parented before this method is called.
	 * 
	 * @param child
	 *            The child that is being dropped
	 */
	void onDropChild(View child) {
		if (child != null) {
			LayoutParams lp = (LayoutParams) child.getLayoutParams();
			lp.dropped = true;
			child.requestLayout();
		}
	}

	/**
	 * Computes a bounding rectangle for a range of cells
	 * 
	 * @param cellX
	 *            X coordinate of upper left corner expressed as a cell position
	 * @param cellY
	 *            Y coordinate of upper left corner expressed as a cell position
	 * @param cellHSpan
	 *            Width in cells
	 * @param cellVSpan
	 *            Height in cells
	 * @param resultRect
	 *            Rect into which to put the results
	 */
	public void cellToRect(int cellX, int cellY, int cellHSpan, int cellVSpan,
			Rect resultRect) {
		final int cellWidth = mCellWidth;
		final int cellHeight = mCellHeight;
		final int widthGap = mWidthGap;
		final int heightGap = mHeightGap;

		final int hStartPadding = getPaddingLeft();
		final int vStartPadding = getPaddingTop();

		int width = cellHSpan * cellWidth + ((cellHSpan - 1) * widthGap);
		int height = cellVSpan * cellHeight + ((cellVSpan - 1) * heightGap);

		int x = hStartPadding + cellX * (cellWidth + widthGap);
		int y = vStartPadding + cellY * (cellHeight + heightGap);

		resultRect.set(x, y, x + width, y + height);
	}

	/**
	 * Computes the required horizontal and vertical cell spans to always fit
	 * the given rectangle.
	 * 
	 * @param width
	 *            Width in pixels
	 * @param height
	 *            Height in pixels
	 * @param result
	 *            An array of length 2 in which to store the result (may be
	 *            null).
	 */
	public int[] rectToCell(int width, int height, int[] result) {
		return rectToCell(getResources(), width, height, result);
	}

	public static int[] rectToCell(Resources resources, int width, int height,
			int[] result) {
		// Always assume we're working with the smallest span to make sure we
		// reserve enough space in both orientations.
		int actualWidth = resources
				.getDimensionPixelSize(R.dimen.workspace_cell_width);
		int actualHeight = resources
				.getDimensionPixelSize(R.dimen.workspace_cell_height);
		int smallerSize = Math.min(actualWidth, actualHeight);

		// Always round up to next largest cell
		int spanX = (int) Math.ceil(width / (float) smallerSize);
		int spanY = (int) Math.ceil(height / (float) smallerSize);

		if (result == null) {
			return new int[] { spanX, spanY };
		}
		result[0] = spanX;
		result[1] = spanY;
		return result;
	}

	public int[] cellSpansToSize(int hSpans, int vSpans) {
		int[] size = new int[2];
		size[0] = hSpans * mCellWidth + (hSpans - 1) * mWidthGap;
		size[1] = vSpans * mCellHeight + (vSpans - 1) * mHeightGap;
		return size;
	}

	/**
	 * Calculate the grid spans needed to fit given item
	 */
	public void calculateSpans(ItemInfo info) {
		final int minWidth;
		final int minHeight;

		if (info instanceof LauncherAppWidgetInfo) {
			minWidth = ((LauncherAppWidgetInfo) info).minWidth;
			minHeight = ((LauncherAppWidgetInfo) info).minHeight;
		} else if (info instanceof PendingAddWidgetInfo) {
			minWidth = ((PendingAddWidgetInfo) info).minWidth;
			minHeight = ((PendingAddWidgetInfo) info).minHeight;
		} else {
			// It's not a widget, so it must be 1x1
			info.spanX = info.spanY = 1;
			return;
		}
		int[] spans = rectToCell(minWidth, minHeight, null);
		info.spanX = spans[0];
		info.spanY = spans[1];
	}

	/**
	 * Find the first vacant cell, if there is one.
	 * 
	 * @param vacant
	 *            Holds the x and y coordinate of the vacant cell
	 * @param spanX
	 *            Horizontal cell span.
	 * @param spanY
	 *            Vertical cell span.
	 * 
	 * @return True if a vacant cell was found
	 */
	public boolean getVacantCell(int[] vacant, int spanX, int spanY) {

		return findVacantCell(vacant, spanX, spanY, mCountX, mCountY, mOccupied);
	}

	public static boolean findVacantCell(int[] vacant, int spanX, int spanY,
			int xCount, int yCount, boolean[][] occupied) {

		for (int y = 0; y < yCount; y++) {
			for (int x = 0; x < xCount; x++) {
				boolean available = !occupied[x][y];
				out: for (int i = x; i < x + spanX - 1 && x < xCount; i++) {
					for (int j = y; j < y + spanY - 1 && y < yCount; j++) {
						available = available && !occupied[i][j];
						if (!available)
							break out;
					}
				}

				if (available) {
					vacant[0] = x;
					vacant[1] = y;
					return true;
				}
			}
		}

		return false;
	}

	private void clearOccupiedCells() {
		for (int x = 0; x < mCountX; x++) {
			for (int y = 0; y < mCountY; y++) {
				mOccupied[x][y] = false;
			}
		}
	}

	public void onMove(View view, int newCellX, int newCellY, int newSpanX,
			int newSpanY) {
		markCellsAsUnoccupiedForView(view);
		markCellsForView(newCellX, newCellY, newSpanX, newSpanY, mOccupied,
				true);
	}

	public void markCellsAsOccupiedForView(View view) {
		markCellsAsOccupiedForView(view, mOccupied);
	}

	public void markCellsAsOccupiedForView(View view, boolean[][] occupied) {
		if (view == null || view.getParent() != mShortcutsAndWidgets)
			return;
		LayoutParams lp = (LayoutParams) view.getLayoutParams();
		markCellsForView(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan,
				occupied, true);
	}

	/**
	 * 标记单元格为空的
	 * 
	 * @param view
	 */
	public void markCellsAsUnoccupiedForView(View view) {
		markCellsAsUnoccupiedForView(view, mOccupied);
	}

	/**
	 * 标记单元格为空的
	 * 
	 * @param view
	 */
	public void markCellsAsUnoccupiedForView(View view, boolean occupied[][]) {
		if (view == null || view.getParent() != mShortcutsAndWidgets)
			return;
		LayoutParams lp = (LayoutParams) view.getLayoutParams();
		markCellsForView(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan,
				occupied, false);
	}

	/**
	 * 给View标记格子，将需要的部分置空（方法对View的所要占用的格子赋了false)
	 * 
	 * @param cellX
	 *            view的x
	 * @param cellY
	 *            view的y
	 * @param spanX
	 *            view的跨度x
	 * @param spanY
	 *            view的跨度y
	 * @param occupied
	 *            填入的是全局boolean类型的二维数组
	 * @param value
	 */
	private void markCellsForView(int cellX, int cellY, int spanX, int spanY,
			boolean[][] occupied, boolean value) {
		if (cellX < 0 || cellY < 0)
			return;
		for (int x = cellX; x < cellX + spanX && x < mCountX; x++) {
			for (int y = cellY; y < cellY + spanY && y < mCountY; y++) {
				occupied[x][y] = value;
			}
		}
	}

	public int getDesiredWidth() {
		return getPaddingLeft() + getPaddingRight() + (mCountX * mCellWidth)
				+ (Math.max((mCountX - 1), 0) * mWidthGap);
	}

	public int getDesiredHeight() {
		return getPaddingTop() + getPaddingBottom() + (mCountY * mCellHeight)
				+ (Math.max((mCountY - 1), 0) * mHeightGap);
	}

	public boolean isOccupied(int x, int y) {
		if (x < mCountX && y < mCountY) {
			return mOccupied[x][y];
		} else {
			throw new RuntimeException(
					"Position exceeds the bound of this CellLayout");
		}
	}

	@Override
	public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
		return new CellLayout.LayoutParams(getContext(), attrs);
	}

	@Override
	protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
		return p instanceof CellLayout.LayoutParams;
	}

	@Override
	protected ViewGroup.LayoutParams generateLayoutParams(
			ViewGroup.LayoutParams p) {
		return new CellLayout.LayoutParams(p);
	}

	public static class CellLayoutAnimationController extends
			LayoutAnimationController {
		public CellLayoutAnimationController(Animation animation, float delay) {
			super(animation, delay);
		}

		@Override
		protected long getDelayForView(View view) {
			return (int) (Math.random() * 150);
		}
	}

	public static class LayoutParams extends ViewGroup.MarginLayoutParams {
		/**
		 * Horizontal location of the item in the grid.
		 */
		@ViewDebug.ExportedProperty
		public int cellX;

		/**
		 * Vertical location of the item in the grid.
		 */
		@ViewDebug.ExportedProperty
		public int cellY;

		/**
		 * Temporary horizontal location of the item in the grid during reorder
		 */
		public int tmpCellX;

		/**
		 * Temporary vertical location of the item in the grid during reorder
		 */
		public int tmpCellY;

		/**
		 * Indicates that the temporary coordinates should be used to layout the
		 * items
		 */
		public boolean useTmpCoords;

		/**
		 * Number of cells spanned horizontally by the item.
		 */
		@ViewDebug.ExportedProperty
		public int cellHSpan;

		/**
		 * Number of cells spanned vertically by the item.
		 */
		@ViewDebug.ExportedProperty
		public int cellVSpan;

		/**
		 * Indicates whether the item will set its x, y, width and height
		 * parameters freely, or whether these will be computed based on cellX,
		 * cellY, cellHSpan and cellVSpan.
		 */
		public boolean isLockedToGrid = true;

		/**
		 * Indicates whether this item can be reordered. Always true except in
		 * the case of the the AllApps button.
		 */
		public boolean canReorder = true;

		// X coordinate of the view in the layout.
		@ViewDebug.ExportedProperty
		public int x;
		// Y coordinate of the view in the layout.
		@ViewDebug.ExportedProperty
		public int y;

		public boolean dropped;

		public LayoutParams(Context c, AttributeSet attrs) {
			super(c, attrs);
			cellHSpan = 1;
			cellVSpan = 1;
		}

		public LayoutParams(ViewGroup.LayoutParams source) {
			super(source);
			cellHSpan = 1;
			cellVSpan = 1;
		}

		public LayoutParams(LayoutParams source) {
			super(source);
			this.cellX = source.cellX;
			this.cellY = source.cellY;
			this.cellHSpan = source.cellHSpan;
			this.cellVSpan = source.cellVSpan;
		}

		public LayoutParams(int cellX, int cellY, int cellHSpan, int cellVSpan) {
			super(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
			this.cellX = cellX;
			this.cellY = cellY;
			this.cellHSpan = cellHSpan;
			this.cellVSpan = cellVSpan;
		}

		public void setup(int cellWidth, int cellHeight, int widthGap,
				int heightGap) {
			if (isLockedToGrid) {
				final int myCellHSpan = cellHSpan;
				final int myCellVSpan = cellVSpan;
				final int myCellX = useTmpCoords ? tmpCellX : cellX;
				final int myCellY = useTmpCoords ? tmpCellY : cellY;

				width = myCellHSpan * cellWidth
						+ ((myCellHSpan - 1) * widthGap) - leftMargin
						- rightMargin;
				height = myCellVSpan * cellHeight
						+ ((myCellVSpan - 1) * heightGap) - topMargin
						- bottomMargin;
				x = (int) (myCellX * (cellWidth + widthGap) + leftMargin);
				y = (int) (myCellY * (cellHeight + heightGap) + topMargin);
			}
		}

		public String toString() {
			return "(" + this.cellX + ", " + this.cellY + ")";
		}

		public void setWidth(int width) {
			this.width = width;
		}

		public int getWidth() {
			return width;
		}

		public void setHeight(int height) {
			this.height = height;
		}

		public int getHeight() {
			return height;
		}

		public void setX(int x) {
			this.x = x;
		}

		public int getX() {
			return x;
		}

		public void setY(int y) {
			this.y = y;
		}

		public int getY() {
			return y;
		}
	}

	// This class stores info for two purposes:
	// 1. When dragging items (mDragInfo in Workspace), we store the View, its
	// cellX & cellY,
	// its spanX, spanY, and the screen it is on
	// 2. When long clicking on an empty cell in a CellLayout, we save
	// information about the
	// cellX and cellY coordinates and which page was clicked. We then set this
	// as a tag on
	// the CellLayout that was long clicked
	/**
	 * 这个类存储信息有两个目的： 1当拖动拖动item时（mDragInfo在Workspace）， 我们保存屏幕上这个View中的 cellX &
	 * cellY, spanX, spanY, and the screen
	 * */
	public static final class CellInfo {
		public View cell;// 当前这个item对应的View
		int cellX = -1;// 该item水平方向上的起始单元格
		int cellY = -1;// 该item垂直方向上的起始单元格
		int spanX;// 该item水平方向上占据的单元格数目
		int spanY;// 该item垂直方向上占据的单元格数目
		int screen; // 所在的屏幕
		long container;

		@Override
		public String toString() {
			return "Cell[view=" + (cell == null ? "null" : cell.getClass())
					+ ", x=" + cellX + ", y=" + cellY + "]";
		}
	}

	public boolean lastDownOnOccupiedCell() {
		return mLastDownOnOccupiedCell;
	}
}
