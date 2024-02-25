package com.example.apptcc.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.example.apptcc.R;
import com.example.apptcc.helper.MLVideoHelperActivity;
import com.example.apptcc.helper.vision.VisionBaseProcessor;
import com.example.apptcc.helper.vision.posedetector.PoseDetectorProcessor;
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;

import java.io.File;
import java.io.IOException;

public class PoseDetectionActivity extends MLVideoHelperActivity {



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    protected VisionBaseProcessor setProcessor() {
        AccuratePoseDetectorOptions options = new AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .build();
        return new PoseDetectorProcessor(
                options,
                true,
                false,
                false,
                false,
                true,
                this,
                graphicOverlay,
                previewView
        );
    }



}