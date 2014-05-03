package com.mohammadag.shaketoundo;

import java.util.HashMap;

import android.graphics.Rect;
import android.widget.EditText;
import android.widget.TextView;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class XposedMod implements IXposedHookZygoteInit {
	private static HashMap<EditText,ShakeEventManager> mMap = new HashMap<EditText, ShakeEventManager>();

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		XposedHelpers.findAndHookMethod(TextView.class, "onFocusChanged", boolean.class, int.class,
				Rect.class, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				TextView textView = (TextView) param.thisObject;
				boolean isEditText = textView instanceof EditText;
				if (!isEditText)
					return;

				ShakeEventManager mgr = mMap.get((EditText)param.thisObject);
				if (mgr == null) {
					TextViewUndoRedo undoRedo = new TextViewUndoRedo(textView);
					mgr = new ShakeEventManager(textView.getContext());
					mgr.setTextViewUndoRedo(undoRedo);
					mMap.put((EditText)param.thisObject, mgr);
				}
				boolean focused = (Boolean) param.args[0];
				if (focused) {
					// Activate the SensorListener
					mgr.register();
				} else {
					// Save battery
					mgr.unregister();
				}
			}
		});

		XposedHelpers.findAndHookMethod(TextView.class, "onDetachedFromWindow", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				boolean isEditText = param.thisObject instanceof EditText;
				if (!isEditText)
					return;

				ShakeEventManager mgr = mMap.get((EditText)param.thisObject);
				if (mgr != null) {
					mgr.unregister();
					mgr.cleanup();
					mgr = null;
				}
				try {
					mMap.remove((EditText)param.thisObject);
				} catch (Throwable t) { }
			}
		});
	}
}
