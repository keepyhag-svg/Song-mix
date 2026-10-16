package com.metrolist.remix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.metrolist.remix.innertube.InnerTubeScraper
import com.metrolist.remix.playback.AuthenticRemixEngine
import com.metrolist.remix.ui.MetroMixMinimalScreen

class MainActivity : ComponentActivity() {
    private lateinit var scraper: InnerTubeScraper
    private lateinit var remixEngine: AuthenticRemixEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        scraper = InnerTubeScraper()
        remixEngine = AuthenticRemixEngine(applicationContext, scraper)

        setContent {
            MetroMixMinimalScreen(
                scraper = scraper,
                remixEngine = remixEngine
            )
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            remixEngine.release()
        }
        super.onDestroy()
    }
}
