package com.amwangfan.omnireader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.amwangfan.omnireader.ui.OmniReaderApp
import com.amwangfan.omnireader.ui.OmniReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmniReaderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OmniReaderApp()
                }
            }
        }
    }
}
