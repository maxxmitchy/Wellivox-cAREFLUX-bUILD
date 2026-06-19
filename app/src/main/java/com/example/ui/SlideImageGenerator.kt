package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.io.File
import java.io.FileOutputStream

object SlideImageGenerator {
    fun generateSlide(context: Context, slide: CarouselSlide, theme: SlideTheme, isStoryFormat: Boolean, layoutTheme: SlideLayoutTheme, totalSlides: Int): Pair<Uri?, Bitmap?> {
        val aspectRatio = if (isStoryFormat) 9f / 16f else 4f / 5f
        // Standard dimensions without forcing unnecessary 1080px upscaling
        val exportWidthPx = 1080
        val exportHeightPx = (exportWidthPx / aspectRatio).toInt()
        
        val activity = context as? ComponentActivity ?: return Pair(null, null)
        val rootView = activity.findViewById<android.view.ViewGroup>(android.R.id.content)

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                val customDensity = androidx.compose.ui.unit.Density(
                    density = exportWidthPx.toFloat() / 360f,
                    fontScale = 1.0f
                )
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides customDensity
                ) {
                    Box(modifier = Modifier.background(theme.cardBg).fillMaxSize()) {
                        SlideContent(slide, theme, layoutTheme, totalSlides, onHeadingChange = {}, onTextChange = {})
                    }
                }
            }
        }
        
        rootView.addView(composeView)
        
        val bitmap = Bitmap.createBitmap(exportWidthPx, exportHeightPx, Bitmap.Config.ARGB_8888)
        
        try {
            composeView.measure(
                MeasureSpec.makeMeasureSpec(exportWidthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(exportHeightPx, MeasureSpec.EXACTLY)
            )
            composeView.layout(0, 0, exportWidthPx, exportHeightPx)
            
            val canvas = Canvas(bitmap)
            composeView.draw(canvas)
        } finally {
            rootView.removeView(composeView)
        }

        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "slide_${System.currentTimeMillis()}.png")
        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush()
        out.close()
        
        val uri = try {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
        
        return Pair(uri, bitmap)
    }
}
