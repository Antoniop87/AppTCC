package com.example.apptcc.activities

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.example.apptcc.R

class MainActivity : AppCompatActivity() {

    private lateinit var btn_button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()

        btn_button.setOnClickListener {
            val i = Intent(this, PoseDetectionActivity::class.java)
            startActivity(i)
        }

    }

    private fun initView(){
        btn_button = findViewById(R.id.btn_teste)
    }

}