package cn.biggee.hardware.camera2.MainActivity;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.constraint.ConstraintLayout;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.util.Collections;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            final Activity mainActivity = this;

            final TextureView mTextureView = new TextureView(mainActivity);
            ((ConstraintLayout) findViewById(R.id.mLayout)).addView(mTextureView);
            final Semaphore mCameraOpenCloseLock = new Semaphore(1);

            final int STATE_PREVIEW = 0;
            final int STATE_WAITING_LOCK = 1;
            final int STATE_WAITING_PRECAPTURE = 2;
            final int STATE_WAITING_NON_PRECAPTURE = 3;
            final int STATE_PICTURE_TAKEN = 4;

            final HandlerThread mBackgroundThread = new HandlerThread("CameraBackground");
            mBackgroundThread.start();
            final Handler mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

            final Object[] objects = new Object[2];
            //0:CameraDevice
            //1:mState

            final CameraManager manager = (CameraManager) mainActivity.getSystemService(Context.CAMERA_SERVICE);

            while (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                Toast.makeText(mainActivity, "Time out waiting to lock camera opening.", Toast.LENGTH_SHORT).show();
            }

            final CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
                private void process(CaptureResult result) {
                    switch ((int) objects[1]) {
                        case STATE_PREVIEW: {
                            // We have nothing to do when the camera preview is working normally.
                            break;
                        }
                        case STATE_WAITING_LOCK: {
                            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                            if (afState == null) {
                                //captureStillPicture();
                            } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                                // CONTROL_AE_STATE can be null on some devices
                                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                                if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                                    objects[1] = STATE_PICTURE_TAKEN;
                                    //captureStillPicture();
                                } else {
                                    //runPrecaptureSequence();
                                }
                            }
                            break;
                        }
                        case STATE_WAITING_PRECAPTURE: {
                            // CONTROL_AE_STATE can be null on some devices
                            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                            if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                                objects[1] = STATE_WAITING_NON_PRECAPTURE;
                            }
                            break;
                        }
                        case STATE_WAITING_NON_PRECAPTURE: {
                            // CONTROL_AE_STATE can be null on some devices
                            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                            if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                                objects[1] = STATE_PICTURE_TAKEN;
                                //captureStillPicture();
                            }
                            break;
                        }
                    }
                }
            };

            final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cameraDevice) {
                    // This method is called when the camera is opened.  We start camera preview here.
                    mCameraOpenCloseLock.release();
                    objects[0] = cameraDevice;
                    try {
                        SurfaceTexture texture = mTextureView.getSurfaceTexture();
                        assert texture != null;
                        // We configure the size of default buffer to be the size of camera preview we want.
                        texture.setDefaultBufferSize(1080, 1920);
                        // This is the output Surface we need to start preview.
                        Surface surface = new Surface(texture);
                        // We set up a CaptureRequest.Builder with the output Surface.
                        final CameraDevice mCameraDevice = (CameraDevice) objects[0];
                        final CaptureRequest.Builder mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        mPreviewRequestBuilder.addTarget(surface);
                        // Here, we create a CameraCaptureSession for camera preview.
                        mCameraDevice.createCaptureSession(Collections.singletonList(surface),// mImageReader.getSurface()),
                                new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                                        // The camera is already closed
                                        if (null == mCameraDevice) {
                                            return;
                                        }
                                        // When the session is ready, we start displaying the preview.
                                        try {
                                            // Auto focus should be continuous for camera preview.
                                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                            // Flash is automatically enabled when necessary.
                                            //setAutoFlash(mPreviewRequestBuilder);
                                            // Finally, we start displaying the camera preview.
                                            final CaptureRequest mPreviewRequest = mPreviewRequestBuilder.build();
                                            cameraCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
                                        } catch (CameraAccessException e) {
                                            e.printStackTrace();
                                        }
                                    }

                                    @Override
                                    public void onConfigureFailed(
                                            CameraCaptureSession cameraCaptureSession) {
                                        //showToast("Failed");
                                    }
                                }, null
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onDisconnected(CameraDevice cameraDevice) {
                    mCameraOpenCloseLock.release();
                    cameraDevice.close();
                    objects[0] = null;
                }

                @Override
                public void onError(CameraDevice cameraDevice, int error) {
                    mCameraOpenCloseLock.release();
                    cameraDevice.close();
                    objects[0] = null;
                    mainActivity.finish();
                }
            };

            final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                    try {
                        assert manager != null;
                        manager.openCamera(manager.getCameraIdList()[0], mStateCallback, mBackgroundHandler);
                    } catch (SecurityException | CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
                    //configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture texture) {
                }
            };

            if (mTextureView.isAvailable()) {
                try {
                    assert manager != null;
                    manager.openCamera(manager.getCameraIdList()[0], mStateCallback, mBackgroundHandler);
                } catch (SecurityException | CameraAccessException e) {
                    e.printStackTrace();
                }
            } else {
                mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
