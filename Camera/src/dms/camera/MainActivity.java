package dms.camera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;

public class MainActivity extends Activity implements OnTouchListener, Callback, OnClickListener, AutoFocusCallback, PictureCallback,
		ShutterCallback, PreviewCallback {
	private SurfaceView svPreview;
	private SurfaceHolder shPreview;
	private Button btnCapture;
	private Camera camera;

	/**
	 * Variable use autofocus
	 */
	private long avgAutofocusTime = -1;
	private long lastStartAutofocus = 0;

	/**
	 * byte array store data of image
	 */
	private byte[] bSnapShot;

	private final static SimpleDateFormat SDF = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.getDefault());

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initControls();
	}

	@SuppressWarnings("deprecation")
	private void initControls() {

		this.svPreview = (SurfaceView) findViewById(R.id.svPreview);
		this.svPreview.setOnTouchListener(this);
		this.shPreview = this.svPreview.getHolder();
		this.svPreview.setDrawingCacheQuality(100);
		this.svPreview.setDrawingCacheEnabled(true);

		this.shPreview.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		this.shPreview.addCallback(this);

		this.btnCapture = (Button) findViewById(R.id.btnCapture);
		this.btnCapture.setOnClickListener(this);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		return false;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		camera.startPreview();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		camera = Camera.open();
		camera.setDisplayOrientation(90);
		camera.setPreviewCallback(this);
		enableFlash();		try {
			camera.setPreviewDisplay(holder);
		} catch (IOException exception) {
			camera.release();
			camera = null;
		}
	}

	private boolean enableFlash() {
		if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
			Parameters p = camera.getParameters();
			p.setFlashMode(Parameters.FLASH_MODE_ON);
			camera.setParameters(p);
			camera.startPreview();
			return true;
		}
		return false;
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		camera.stopPreview();
		camera.release();
		camera = null;
	}

	@Override
	public void onClick(View v) {
		if (v == btnCapture) {
			imageCapture();
		}
	}

	/**
	 * Take picture
	 */
	public void imageCapture() {
		startAutoFocus();
	}

	@Override
	public void onAutoFocus(boolean success, Camera camera) {
		long completeAutofocus = System.currentTimeMillis();
		synchronized (camera) {
			if (bSnapShot != null) {
				// average autofocus speed
				if (avgAutofocusTime == -1) {
					avgAutofocusTime = completeAutofocus - lastStartAutofocus;
				} else {
					avgAutofocusTime += completeAutofocus - lastStartAutofocus;
					avgAutofocusTime /= 2;
				}
				camera.takePicture(this, null, null, this);

				try {
					camera.cancelAutoFocus();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * start autofocus
	 */
	private void startAutoFocus() {
		lastStartAutofocus = System.currentTimeMillis();
		try {
			camera.autoFocus(this);
		} catch (RuntimeException re) {
			onAutoFocus(true, camera);
		}
	}

	/**
	 * save image to sdcard
	 */
	private void saveImage() {
		FileOutputStream filecon = null;
		try {
			File directory = new File(Environment.getExternalStorageDirectory().getPath());
			if (!directory.exists()) {
				directory.mkdir();
			}
			File file = new File(directory.getAbsolutePath() + "/NTD" + SDF.format(new Date()) + ".jpg");
			filecon = new FileOutputStream(file);
			filecon.write(bSnapShot);
			filecon.close();
			rotateImageFile(file, 90);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (filecon != null) {
				try {
					filecon.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/**
	 * rotate image
	 * 
	 * @param file
	 *            : file need rotate
	 * @param rotation
	 *            : rotate
	 * @return : true if rotate success | false if rotate fail
	 */
	private boolean rotateImageFile(File file, int rotation) {
		Boolean rotationSuccess = false;
		Bitmap bitmapOri = null;
		Bitmap bitmapRotate = null;
		FileOutputStream filecon = null;
		try {
			bitmapOri = BitmapFactory.decodeFile(file.getAbsolutePath());
			Matrix mat = new Matrix();
			mat.postRotate(rotation);
			bitmapRotate = Bitmap.createBitmap(bitmapOri, 0, 0, bitmapOri.getWidth(), bitmapOri.getHeight(), mat, true);
			bitmapOri.recycle();
			bitmapOri = null;
			filecon = new FileOutputStream(file);
			bitmapRotate.compress(CompressFormat.JPEG, 100, filecon);
			bitmapRotate.recycle();
			bitmapRotate = null;
			rotationSuccess = true;
		} catch (OutOfMemoryError e) {
			System.gc();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			if (bitmapOri != null) {
				bitmapOri.recycle();
				bitmapOri = null;
			}
			if (bitmapRotate != null) {
				bitmapRotate.recycle();
				bitmapRotate = null;
			}
			if (filecon != null) {
				try {
					filecon.close();
				} catch (IOException e) {
				}
				filecon = null;
			}
		}
		return rotationSuccess;
	}

	@Override
	public void onPictureTaken(byte[] data, Camera camera) {
		bSnapShot = data;
		saveImage();
		camera.startPreview();
	}

	@Override
	public void onShutter() {

	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		bSnapShot = data;
	}

}
