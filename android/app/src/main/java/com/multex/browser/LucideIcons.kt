package com.multex.browser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The "settings-2" (two sliders) icon from lucide, the icon set the web preview uses. */
val LucideSettings2: ImageVector by lazy {
    ImageVector.Builder(
        name = "Settings2",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(20f, 7f)
            horizontalLineTo(11f)
            moveTo(14f, 17f)
            horizontalLineTo(5f)
            // circle at (17, 17), r = 3
            moveTo(20f, 17f)
            arcTo(3f, 3f, 0f, true, true, 14f, 17f)
            arcTo(3f, 3f, 0f, true, true, 20f, 17f)
            close()
            // circle at (7, 7), r = 3
            moveTo(10f, 7f)
            arcTo(3f, 3f, 0f, true, true, 4f, 7f)
            arcTo(3f, 3f, 0f, true, true, 10f, 7f)
            close()
        }
    }.build()
}
