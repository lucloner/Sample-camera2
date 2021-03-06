package cn.biggee.hardware.camera2.MainActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.constraint.ConstraintLayout;
import android.view.Surface;
import android.view.TextureView;

import java.util.Collections;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            final Activity mainActivity = this;

            final TextureView mTextureView = new TextureView(mainActivity);
            ((ConstraintLayout) findViewById(R.id.mLayout)).addView(mTextureView);

            final CameraManager manager = (CameraManager) mainActivity.getSystemService(Context.CAMERA_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    this.requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
                }
            }

            new Handler().postDelayed(() -> {
                try {
                    manager.openCamera(manager.getCameraIdList()[0], new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(CameraDevice cameraDevice) {
                            try {
                                final SurfaceTexture texture = mTextureView.getSurfaceTexture();
                                assert texture != null;
                                texture.setDefaultBufferSize(mTextureView.getWidth(), mTextureView.getHeight());
                                final Surface surface = new Surface(texture);
                                final CaptureRequest.Builder mPreviewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                mPreviewRequestBuilder.addTarget(surface);
                                cameraDevice.createCaptureSession(Collections.singletonList(surface),
                                        new CameraCaptureSession.StateCallback() {
                                            @Override
                                            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                                                try {
                                                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                                    final CaptureRequest mPreviewRequest = mPreviewRequestBuilder.build();
                                                    cameraCaptureSession.setRepeatingRequest(mPreviewRequest, new CameraCaptureSession.CaptureCallback() {
                                                            }
                                                            , null);
                                                } catch (CameraAccessException e) {
                                                    e.printStackTrace();
                                                }
                                            }

                                            @Override
                                            public void onConfigureFailed(
                                                    CameraCaptureSession cameraCaptureSession) {
                                            }
                                        }, null
                                );
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onDisconnected(CameraDevice cameraDevice) {
                            cameraDevice.close();
                        }

                        @Override
                        public void onError(CameraDevice cameraDevice, int error) {
                            cameraDevice.close();
                            mainActivity.finish();
                        }
                    }, null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }, 100);//硬件延迟测定

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
