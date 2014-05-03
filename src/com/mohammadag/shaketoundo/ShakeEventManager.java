package com.mohammadag.shaketoundo;

/* 
 * MohammadAG: Taken from http://bit.ly/1iKs99S
 *             Slightly modified to work in the context of this module
 */

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import de.robv.android.xposed.XposedBridge;

public class ShakeEventManager implements SensorEventListener {
	private static final int MOV_COUNTS = 2;
	private static final float MOV_THRESHOLD = 15;
	private static final long SHAKE_WINDOW_TIME_INTERVAL = 3000;
	private SensorManager sManager;
	private Sensor s;
	private int counter = 0;
	private long firstMovTime = 0;
	private OnShakeEventListener listener;
	private TextViewUndoRedo mUndoRedo;
	private Context mContext;
	private boolean mDialogShowing;
	private AlertDialog mAlertDialog;

	public static interface OnShakeEventListener {
		public void onShake();
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

	public ShakeEventManager(Context context) {
		mContext = context;
		sManager = (SensorManager)  context.getSystemService(Context.SENSOR_SERVICE);
		s = sManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
	}

	public void register() {
		sManager.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
	}

	public void unregister() {
		sManager.unregisterListener(this);
		if (mAlertDialog != null)
			mAlertDialog.cancel();
	}

	public void cleanup() {
		sManager = null;
		mContext = null;
		listener = null;
		s = null;
		mUndoRedo = null;
	}

	private float calcMaxAcceleration(SensorEvent event) {	 
		float accX = event.values[0];
		float accY = event.values[1];
		float accZ = event.values[2];

		float max1 = Math.max(accX, accY);
		return Math.max(max1, accZ);
	}

	@Override
	public void onSensorChanged(SensorEvent sensorEvent) {
		float maxAcc = calcMaxAcceleration(sensorEvent);
		Log.d("SwA", "Max Acc ["+maxAcc+"]");
		if (maxAcc >= MOV_THRESHOLD) {
			if (counter == 0) {
				counter++;
				firstMovTime = System.currentTimeMillis();
				XposedBridge.log("First move..");
			} else {
				if ((System.currentTimeMillis() - firstMovTime) < SHAKE_WINDOW_TIME_INTERVAL) {
					counter++;
				} else {
					Log.d("Xposed", "Second move outside time interval: " + (System.currentTimeMillis() - firstMovTime));
					counter++;
					firstMovTime = System.currentTimeMillis();
					return;
				}
				XposedBridge.log("Move counter ["+counter+"]");

				if (counter >= MOV_COUNTS) {
					XposedBridge.log("Shook me allll niggght long");
					if (listener != null) {
						listener.onShake();
					}

					showUndoRedoDialog();
					resetAllData();
				}
			}
		}

	}

	private void resetAllData() {
		Log.d("Xposed", "reset all values");
		counter = 0;
		firstMovTime = 0;
	}

	public void setOnShakeListener(OnShakeEventListener listener) {
		this.listener = listener;
	}

	public void setTextViewUndoRedo(TextViewUndoRedo undoRedo) {
		mUndoRedo = undoRedo;
	}

	public TextViewUndoRedo getUndoRedo() {
		return mUndoRedo;
	}

	public Context getContext() {
		return mContext;
	}

	private void showUndoRedoDialog() {
		if (mContext == null)
			return;

		if (mDialogShowing) {
			resetAllData();
			return;
		}

		mDialogShowing = true;

		boolean canUndo = mUndoRedo.getCanUndo();
		boolean canRedo = mUndoRedo.getCanRedo();

		if (!canUndo && !canRedo)
			return;

		AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
		builder.setTitle("");
		if (canUndo) {
			builder.setPositiveButton(ResourceHelper.getOwnString(mContext, R.string.undo), new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					mUndoRedo.undo();
					mDialogShowing = false;
				}
			});
		}

		if (canRedo) {
			builder.setNegativeButton(ResourceHelper.getOwnString(mContext, R.string.redo), new OnClickListener() {	
				@Override
				public void onClick(DialogInterface dialog, int which) {
					mUndoRedo.redo();
					mDialogShowing = false;
				}
			});
		}

		builder.setOnCancelListener(new OnCancelListener() {	
			@Override
			public void onCancel(DialogInterface arg0) {
				mDialogShowing = false;
			}
		});

		mAlertDialog = builder.show();
	}
}
