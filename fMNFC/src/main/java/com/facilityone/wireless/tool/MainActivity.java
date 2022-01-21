/*
 * Copyright 2011, The Android Open Source Project
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

package com.facilityone.wireless.tool;

import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.facilityone.wireless.fm_library.zxing.QRCodeCaptureActivity;

public class MainActivity extends Activity {
    private static final String TAG = "stickynotes";
    private boolean mWriteMode = false;
    NfcAdapter mNfcAdapter;
    TextView mNote;
    TextView mNoteRead;
    PendingIntent mNfcPendingIntent;
    IntentFilter[] mWriteTagFilters;
    IntentFilter[] mNdefExchangeFilters;
    Dialog dialog;
    public final static int SCANNIN_GREQUEST_CODE = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);        
        mNote = ((TextView) findViewById(R.id.note));
        mNoteRead = ((TextView) findViewById(R.id.noteRead));
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter==null) {
        	ShowToast.toast(MainActivity.this, "设备不支持nfc!");
        	return;
		}
        findViewById(R.id.write_tag).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				 disableNdefExchangeMode();
		         enableTagWriteMode();
		         dialog.show();				
			}
		});
        findViewById(R.id.read_tag).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				disableTagWriteMode();
	            enableNdefExchangeMode();
	            dialog.show();				
			}
		});
        findViewById(R.id.scan_code).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				QRCodeCaptureActivity.startActivity(MainActivity.this,
	                    SCANNIN_GREQUEST_CODE);				
			}
		});
		// Handle all of our received NFC intents in this activity.
		mNfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
				getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		// Intent filters for reading a note from a tag or exchanging over p2p.
		IntentFilter ndefDetected = new IntentFilter(
				NfcAdapter.ACTION_NDEF_DISCOVERED);
		try {
			ndefDetected.addDataType("text/plain");
		} catch (MalformedMimeTypeException e) {
		}
		mNdefExchangeFilters = new IntentFilter[] { ndefDetected };

		// Intent filters for writing to a tag
		IntentFilter tagDetected = new IntentFilter(
				NfcAdapter.ACTION_TAG_DISCOVERED);
		mWriteTagFilters = new IntentFilter[] { tagDetected };
		
		dialog = new AlertDialog.Builder(MainActivity.this).setTitle("贴近NFC标签")
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        disableTagWriteMode();
                        enableNdefExchangeMode();
                    }
                }).create();
    }

    @Override
    protected void onResume() {
        super.onResume();
//        mResumed = true;
        // Sticky notes received from Android
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            NdefMessage[] messages = getNdefMessages(getIntent());
            byte[] payload = messages[0].getRecords()[0].getPayload();
            setNoteBody(new String(payload));
            setIntent(new Intent()); // Consume this intent.
        }
        if (mNfcAdapter!=null) {
        	enableNdefExchangeMode();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
//        mResumed = false;
        if (mNfcAdapter!=null) {
        	mNfcAdapter.disableForegroundNdefPush(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // NDEF exchange mode
        if (!mWriteMode && NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            NdefMessage[] msgs = getNdefMessages(intent);
            String body = new String(msgs[0].getRecords()[0].getPayload());
            setNoteBody(body);
        }

        // Tag writing mode
        if (mWriteMode && NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            writeTag(getNoteAsNdef(), detectedTag);
        }
    }

    private void setNoteBody(String body) {
        mNoteRead.setText(body);
        if (dialog != null) {
        	dialog.cancel();
        }
    }

    private NdefMessage getNoteAsNdef() {
        byte[] textBytes = mNote.getText().toString().getBytes();
        NdefRecord textRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, "text/plain".getBytes(),
                new byte[] {}, textBytes);
        return new NdefMessage(new NdefRecord[] {
            textRecord
        });
    }

    NdefMessage[] getNdefMessages(Intent intent) {
        // Parse the intent
        NdefMessage[] msgs = null;
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (rawMsgs != null) {
                msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
            } else {
                // Unknown tag type
                byte[] empty = new byte[] {};
                NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, empty, empty, empty);
                NdefMessage msg = new NdefMessage(new NdefRecord[] {
                    record
                });
                msgs = new NdefMessage[] {
                    msg
                };
            }
        } else {
            Log.d(TAG, "Unknown intent.");
            finish();
        }
        return msgs;
    }

    private void enableNdefExchangeMode() {
        mNfcAdapter.enableForegroundNdefPush(MainActivity.this, getNoteAsNdef());
        mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, mNdefExchangeFilters, null);
    }

    private void disableNdefExchangeMode() {
        mNfcAdapter.disableForegroundNdefPush(this);
        mNfcAdapter.disableForegroundDispatch(this);
    }

    private void enableTagWriteMode() {
        mWriteMode = true;
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        mWriteTagFilters = new IntentFilter[] {
            tagDetected
        };
        mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, mWriteTagFilters, null);
    }

    private void disableTagWriteMode() {
        mWriteMode = false;
        mNfcAdapter.disableForegroundDispatch(this);
    }

    /**
     * 写入标签
     */
    boolean writeTag(NdefMessage message, Tag tag) {
        int size = message.toByteArray().length;

        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();

                if (!ndef.isWritable()) {
                    ShowToast.toast(MainActivity.this, "NFC标签只读!");
                    return false;
                }

                if (ndef.getMaxSize() < size) {
                    ShowToast.toast(MainActivity.this, "NFC标签容量太小!");
                    return false;
                }

                ndef.writeNdefMessage(message);
                ShowToast.toast(MainActivity.this, "数据写入成功!");
                dialog.cancel();
                return true;
            } else {
                NdefFormatable format = NdefFormatable.get(tag);
                if (format != null) {
                    try {
                        format.connect();
                        format.format(message);
                        return true;
                    } catch (IOException e) {
                        ShowToast.toast(MainActivity.this, "NFC标签格式化失败!");
                        return false;
                    }
                } else {
                    ShowToast.toast(MainActivity.this, "NFC标签不支持NDEF!");
                    return false;
                }
            }
        } catch (Exception e) {
            ShowToast.toast(MainActivity.this, "数据写入失败，请重新操作!");
            dialog.cancel();
        }

        return false;
    }

	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case SCANNIN_GREQUEST_CODE: // Scan QRCode back with result.
                if (resultCode == RESULT_OK) {
                    String str = "";
                    Bundle bundle = data.getExtras();
                    String qrcode = bundle.getString(QRCodeCaptureActivity.QRCODE_CONTENT);
                    String[] strings = qrcode.split("\\|");
                    if (strings.length>=4){
                        str = strings[0]+"|"+strings[1]+"|"+strings[3];
                        if(str.toCharArray().length>60){
                            str = strings[0];
                        }
                    }else if (strings.length>0){
                        str = strings[0];
                    }
                    mNote.setText(str);
                }
                break;
        }
    }
}