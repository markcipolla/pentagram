package com.pentagram.airplay

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceView
import kotlin.math.roundToInt

class AspectRatioSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "AspectRatioSurfaceView"
    }

    private var videoWidth = 0
    private var videoHeight = 0

    fun setVideoSize(width: Int, height: Int) {
        if (width == videoWidth && height == videoHeight) {
            Log.d(TAG, "setVideoSize: Same size, ignoring (${width}x${height})")
            return
        }
        Log.w(TAG, "setVideoSize: ${videoWidth}x${videoHeight} → ${width}x${height}")
        videoWidth = width
        videoHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = MeasureSpec.getSize(widthMeasureSpec)
        var height = MeasureSpec.getSize(heightMeasureSpec)

        Log.d(TAG, "onMeasure: Available space ${width}x${height}, Video size ${videoWidth}x${videoHeight}")

        if (videoWidth > 0 && videoHeight > 0) {
            val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
            val screenAspect = width.toFloat() / height.toFloat()

            Log.d(TAG, "  Video aspect: $videoAspect, Screen aspect: $screenAspect")

            if (screenAspect > videoAspect) {
                // Screen is wider than video - fit to height
                val newWidth = (height * videoAspect).roundToInt()
                Log.d(TAG, "  Screen wider - fit to height: ${width}x${height} → ${newWidth}x${height}")
                width = newWidth
            } else {
                // Screen is taller than video - fit to width
                val newHeight = (width / videoAspect).roundToInt()
                Log.d(TAG, "  Screen taller - fit to width: ${width}x${height} → ${width}x${newHeight}")
                height = newHeight
            }
        }

        Log.w(TAG, "  → Final measured dimensions: ${width}x${height}")
        setMeasuredDimension(width, height)
    }
}
