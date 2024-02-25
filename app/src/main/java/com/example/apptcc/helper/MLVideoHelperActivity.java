package com.example.apptcc.helper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.apptcc.R;
import com.example.apptcc.helper.vision.GraphicOverlay;
import com.example.apptcc.helper.vision.VisionBaseProcessor;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public abstract class MLVideoHelperActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA = 1001;
    protected PreviewView previewView;
    protected GraphicOverlay graphicOverlay;
    private TextView outputTextView;
    private ExtendedFloatingActionButton addFaceButton;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private Executor executor = Executors.newSingleThreadExecutor();

    private VisionBaseProcessor processor;
    private ImageAnalysis imageAnalysis;

    private static final String TAG = "PoseDetectionActivity";
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private String outputFilePath;

    private Button btn_teste_gravao;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mlvideo_helper);

        previewView = findViewById(R.id.camera_source_preview);
        graphicOverlay = findViewById(R.id.graphic_overlay);
        outputTextView = findViewById(R.id.output_text_view);
        addFaceButton = findViewById(R.id.button_add_face);
        btn_teste_gravao = findViewById(R.id.btn_teste_gravacao);

        cameraProviderFuture = ProcessCameraProvider.getInstance(getApplicationContext());

        processor = setProcessor();

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        } else {
            initSource();
        }
        grava();
    }

    private void grava(){
        btn_teste_gravao.setOnClickListener(v -> {
            if (isRecording == false){
                startRecording();
            } else {
                stopRecording();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (processor != null) {
            processor.stop();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initSource();
        }
    }

    protected void setOutputText(String text) {
        outputTextView.setText(text);
    }

    private void initSource() {

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(getApplicationContext()));
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        int lensFacing = getLensFacing();
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build();

        setFaceDetector(lensFacing);
        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview);
    }

    /**
     * The face detector provides face bounds whose coordinates, width and height depend on the
     * preview's width and height, which is guaranteed to be available after the preview starts
     * streaming.
     */
    private void setFaceDetector(int lensFacing) {
        previewView.getPreviewStreamState().observe(this, new Observer<PreviewView.StreamState>() {
            @Override
            public void onChanged(PreviewView.StreamState streamState) {
                if (streamState != PreviewView.StreamState.STREAMING) {
                    return;
                }

                View preview = previewView.getChildAt(0);
                float width = preview.getWidth() * preview.getScaleX();
                float height = preview.getHeight() * preview.getScaleY();
                float rotation = preview.getDisplay().getRotation();
                if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
                    float temp = width;
                    width = height;
                    height = temp;
                }

                imageAnalysis.setAnalyzer(
                        executor,
                        createFaceDetector((int) width, (int) height, lensFacing)
                );
                previewView.getPreviewStreamState().removeObserver(this);
            }
        });
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private ImageAnalysis.Analyzer createFaceDetector(int width, int height, int lensFacing) {
        graphicOverlay.setPreviewProperties(width, height, lensFacing);
        return imageProxy -> {
            if (imageProxy.getImage() == null) {
                imageProxy.close();
                return;
            }
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            // converting from YUV format
            processor.detectInImage(imageProxy, toBitmap(imageProxy.getImage()), rotationDegrees);
            // after done, release the ImageProxy object
            imageProxy.close();
        };
    }

    private Bitmap toBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    protected int getLensFacing() {
        return CameraSelector.LENS_FACING_BACK;
    }

    protected abstract VisionBaseProcessor setProcessor();

    public void makeAddFaceVisible() {
        addFaceButton.setVisibility(View.VISIBLE);
    }

    public void onAddFaceClicked(View view) {

    }

    private void startRecording() {
        if (isRecording) {
            return;
        }

        mediaRecorder = new MediaRecorder();
        outputFilePath = getOutputMediaFile().toString();

        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setVideoEncodingBitRate(10000000);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setVideoSize(1280, 720);
            mediaRecorder.setOutputFile(outputFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
        } catch (IOException e) {
            Log.e(TAG, "Erro ao iniciar a gravação: " + e.getMessage());
            Toast.makeText(this, "Erro ao iniciar a gravação.", Toast.LENGTH_SHORT).show();
            releaseMediaRecorder();
        }
    }

    private void stopRecording() {
        if (isRecording && mediaRecorder != null) {
            mediaRecorder.stop();
            releaseMediaRecorder();
            Toast.makeText(this, "Vídeo salvo em: " + outputFilePath, Toast.LENGTH_SHORT).show();
            isRecording = false;
        }
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private File getOutputMediaFile() {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM), "Camera");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "Falha ao criar diretório");
                return null;
            }
        }

        return new File(mediaStorageDir.getPath() + File.separator +
                "VID_" + System.currentTimeMillis() + ".mp4");
    }

}
