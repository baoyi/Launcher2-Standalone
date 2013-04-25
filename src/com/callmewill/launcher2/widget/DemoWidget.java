package com.callmewill.launcher2.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.EmbossMaskFilter;
import android.graphics.MaskFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.callmewill.launcher2.HolographicOutlineHelper;
import com.callmewill.launcher2.LauncherApplication;
import com.callmewill.launcher2.R;

public class DemoWidget extends View {
	public DemoWidget(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}

	public DemoWidget(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	public DemoWidget(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
	}

	int i = 0;
	boolean work = true;

	@Override
	public void onDraw(Canvas canvas) {
		canvas.drawColor(Color.WHITE);
		LauncherApplication application = (LauncherApplication) getContext()
				.getApplicationContext();
		Drawable d = application.mIconCache.getFullResDefaultActivityIcon();
		d.draw(canvas);
		Paint paint = new Paint();
		paint.setColor(0xffcccc00);
		paint.setTextSize(30);
		/**
		 * 画布先操作，在进行其他操作。
		 */
		canvas.save();
		canvas.rotate(i);
		canvas.drawText("hello    i  am drawing " + i, 0, getPaddingTop() + 30,
				paint);
		canvas.translate(50, 50);
		if (work) {
			i++;
		} else {
			i--;
		}
		canvas.restore();
		if (i > 90) {
			work = false;
		}
		if (i < 0) {
			work = true;
		}
		Bitmap bitmap = BitmapFactory.decodeResource(getResources(),
				R.drawable.ic_launcher);
		// canvas.drawBitmap(bitmap, 0, 0, paint);
		postInvalidate();
		Bitmap bitmap1 = drawImageDropShadow(bitmap);
		canvas.drawBitmap(bitmap1, 50, 50, new Paint());

	}

	private final HolographicOutlineHelper mOutlineHelper = new HolographicOutlineHelper();

	/**
	 * 
	 * add shadow to bitmap
	 * 
	 * @param originalBitmap
	 * @return
	 */
	private Bitmap drawImageDropShadow(Bitmap originalBitmap) {

		MaskFilter blurFilter = new BlurMaskFilter(50,
				BlurMaskFilter.Blur.NORMAL);
		Paint shadowPaint = new Paint();
		shadowPaint.setAlpha(100);
		final int outlineColor = getResources().getColor(
				android.R.color.holo_blue_light);

		shadowPaint.setColor(outlineColor);
		shadowPaint.setMaskFilter(blurFilter);

		int[] offsetXY = new int[2];
		Bitmap shadowBitmap = originalBitmap
				.extractAlpha(shadowPaint, offsetXY);

		Bitmap shadowImage32 = shadowBitmap.copy(Bitmap.Config.ARGB_8888, true);
		Canvas c = new Canvas(shadowImage32);
		c.drawBitmap(originalBitmap, offsetXY[0], offsetXY[1], null);

		return shadowImage32;
	}
	private Bitmap drawImageDropShadow1(Bitmap originalBitmap) {
		// 设置光源的方向    
		float[] direction = new float[]{ 1, 1, 1 };    
		  //设置环境光亮度    
		  float light = 0.4f;    
		  // 选择要应用的反射等级    
		  float specular = 6;    
		  // 向mask应用一定级别的模糊    
		  float blur = 3.5f;    
		EmbossMaskFilter emboss=new EmbossMaskFilter(direction,light,specular,blur);    
		// 应用mask    
		Paint shadowPaint = new Paint();
		shadowPaint.setAlpha(30);
		final int outlineColor = getResources().getColor(
				android.R.color.holo_blue_light);

		shadowPaint.setColor(outlineColor);
		shadowPaint.setMaskFilter(emboss);

		int[] offsetXY = new int[2];
		Bitmap shadowBitmap = originalBitmap
				.extractAlpha(shadowPaint, offsetXY);

		Bitmap shadowImage32 = shadowBitmap.copy(Bitmap.Config.ARGB_8888, true);
		Canvas c = new Canvas(shadowImage32);
		c.drawBitmap(originalBitmap, offsetXY[0], offsetXY[1], null);

		return shadowImage32;
	}

}
