package net.rpcs3

import android.app.Activity
import android.os.Bundle

class RPCS3Activity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rpcs3)

        listenUsbEvents(this)

        val surfaceView = findViewById<GraphicsFrame>(R.id.surfaceView)
        surfaceView.boot(intent.getStringExtra("path")!!)
    }
}