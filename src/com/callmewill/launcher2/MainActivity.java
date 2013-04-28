package com.callmewill.launcher2;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.callmewill.launcher2.widget.DemoWidget;
public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		demoWidget=(DemoWidget) findViewById(R.id.demoWidget);
		
//		handler.postDelayed(new Runnable() {
//			
//			@Override
//			public void run() {
//				demoWidget.setTranslationX(800);
//			}
//		}, 2000);
	}
	private Handler handler=new Handler();
	DemoWidget demoWidget;
	public void click(View v){
		
		ValueAnimator animator=new ValueAnimator();
		animator.setFloatValues(5);
		animator.setDuration(5000);
		animator.setInterpolator(new LinearInterpolator());
		animator.addUpdateListener(new AnimatorUpdateListener() {
			
			@Override
			public void onAnimationUpdate(ValueAnimator animation) {
				// TODO Auto-generated method stub
                float r = ((Float) animation.getAnimatedValue()).floatValue();
                demoWidget.setTranslationX(demoWidget.getTranslationX()+r);
                demoWidget.requestLayout();
			}
		});
		
		animator.start();
	}
}
