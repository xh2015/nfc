package com.facilityone.wireless.tool;

import android.content.Context;
import android.widget.Toast;

public class ShowToast {
	public static void toast(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }
}
