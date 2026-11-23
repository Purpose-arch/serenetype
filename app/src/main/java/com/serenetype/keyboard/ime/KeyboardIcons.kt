package com.serenetype.keyboard.ime

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object KeyboardIcons {

    private val stroke = SolidColor(Color.Black)

    val Return: ImageVector by lazy {
        ImageVector.Builder("Return", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = stroke, strokeLineWidth = 2.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(19f, 6f)
                verticalLineTo(12.2f)
                curveTo(19f, 14.3f, 17.3f, 16f, 15.2f, 16f)
                horizontalLineTo(5.5f)
                moveTo(9.5f, 11.8f)
                lineTo(5f, 16f)
                lineTo(9.5f, 20.2f)
            }
        }.build()
    }

    val Backspace: ImageVector by lazy {
        ImageVector.Builder("Backspace", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = stroke, strokeLineWidth = 2.4f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(9.4f, 5.5f)
                horizontalLineTo(18.5f)
                curveTo(19.88f, 5.5f, 21f, 6.62f, 21f, 8f)
                verticalLineTo(16f)
                curveTo(21f, 17.38f, 19.88f, 18.5f, 18.5f, 18.5f)
                horizontalLineTo(9.4f)
                lineTo(3.2f, 12f)
                close()
                moveTo(12.4f, 9.6f)
                lineTo(17f, 14.4f)
                moveTo(17f, 9.6f)
                lineTo(12.4f, 14.4f)
            }
        }.build()
    }

    val SelectAll: ImageVector by lazy {
        ImageVector.Builder("SelectAll", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = stroke, strokeLineWidth = 2.35f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // Rounded corner brackets matching Copy/Clipboard geometry
                moveTo(4f, 8.5f); verticalLineTo(6.5f); curveTo(4f, 5.12f, 5.12f, 4f, 6.5f, 4f); horizontalLineTo(8.5f)
                moveTo(15.5f, 4f); horizontalLineTo(17.5f); curveTo(18.88f, 4f, 20f, 5.12f, 20f, 6.5f); verticalLineTo(8.5f)
                moveTo(20f, 15.5f); verticalLineTo(17.5f); curveTo(20f, 18.88f, 18.88f, 20f, 17.5f, 20f); horizontalLineTo(15.5f)
                moveTo(8.5f, 20f); horizontalLineTo(6.5f); curveTo(5.12f, 20f, 4f, 18.88f, 4f, 17.5f); verticalLineTo(15.5f)
                // Clean inner text lines matching Clipboard weight
                moveTo(8.5f, 10.2f); horizontalLineTo(15.5f)
                moveTo(8.5f, 13.8f); horizontalLineTo(13.5f)
            }
        }.build()
    }

    val Copy: ImageVector by lazy {
        ImageVector.Builder("Copy", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = stroke, strokeLineWidth = 2.35f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(9.5f, 8.5f)
                horizontalLineTo(18f)
                curveTo(19.38f, 8.5f, 20.5f, 9.62f, 20.5f, 11f)
                verticalLineTo(18f)
                curveTo(20.5f, 19.38f, 19.38f, 20.5f, 18f, 20.5f)
                horizontalLineTo(9.5f)
                curveTo(8.12f, 20.5f, 7f, 19.38f, 7f, 18f)
                verticalLineTo(11f)
                curveTo(7f, 9.62f, 8.12f, 8.5f, 9.5f, 8.5f)
                close()
                moveTo(15.2f, 5.5f)
                verticalLineTo(5.2f)
                curveTo(15.2f, 4.0f, 14.2f, 3.2f, 13f, 3.2f)
                horizontalLineTo(6f)
                curveTo(4.62f, 3.2f, 3.5f, 4.32f, 3.5f, 5.7f)
                verticalLineTo(13.5f)
                curveTo(3.5f, 14.7f, 4.4f, 15.6f, 5.5f, 15.6f)
            }
        }.build()
    }

    val Paste: ImageVector by lazy {
        ImageVector.Builder("Paste", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = stroke, strokeLineWidth = 2.35f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(8.5f, 5f)
                horizontalLineTo(6.8f)
                curveTo(5.53f, 5f, 4.5f, 6.03f, 4.5f, 7.3f)
                verticalLineTo(18.2f)
                curveTo(4.5f, 19.47f, 5.53f, 20.5f, 6.8f, 20.5f)
                horizontalLineTo(17.2f)
                curveTo(18.47f, 20.5f, 19.5f, 19.47f, 19.5f, 18.2f)
                verticalLineTo(7.3f)
                curveTo(19.5f, 6.03f, 18.47f, 5f, 17.2f, 5f)
                horizontalLineTo(15.5f)
                moveTo(9.5f, 3.2f)
                horizontalLineTo(14.5f)
                curveTo(15.05f, 3.2f, 15.5f, 3.65f, 15.5f, 4.2f)
                verticalLineTo(6f)
                curveTo(15.5f, 6.55f, 15.05f, 7f, 14.5f, 7f)
                horizontalLineTo(9.5f)
                curveTo(8.95f, 7f, 8.5f, 6.55f, 8.5f, 6f)
                verticalLineTo(4.2f)
                curveTo(8.5f, 3.65f, 8.95f, 3.2f, 9.5f, 3.2f)
                close()
                moveTo(12f, 10.5f); lineTo(12f, 16.5f)
                moveTo(9.2f, 13.8f); lineTo(12f, 16.5f); lineTo(14.8f, 13.8f)
            }
        }.build()
    }

    val Clipboard: ImageVector by lazy {
        ImageVector.Builder("Clipboard", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = stroke, strokeLineWidth = 2.35f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(8.5f, 5f)
                horizontalLineTo(6.8f)
                curveTo(5.53f, 5f, 4.5f, 6.03f, 4.5f, 7.3f)
                verticalLineTo(18.2f)
                curveTo(4.5f, 19.47f, 5.53f, 20.5f, 6.8f, 20.5f)
                horizontalLineTo(17.2f)
                curveTo(18.47f, 20.5f, 19.5f, 19.47f, 19.5f, 18.2f)
                verticalLineTo(7.3f)
                curveTo(19.5f, 6.03f, 18.47f, 5f, 17.2f, 5f)
                horizontalLineTo(15.5f)
                moveTo(9.5f, 3.2f)
                horizontalLineTo(14.5f)
                curveTo(15.05f, 3.2f, 15.5f, 3.65f, 15.5f, 4.2f)
                verticalLineTo(6f)
                curveTo(15.5f, 6.55f, 15.05f, 7f, 14.5f, 7f)
                horizontalLineTo(9.5f)
                curveTo(8.95f, 7f, 8.5f, 6.55f, 8.5f, 6f)
                verticalLineTo(4.2f)
                curveTo(8.5f, 3.65f, 8.95f, 3.2f, 9.5f, 3.2f)
                close()
                moveTo(8.5f, 11.2f); lineTo(15.5f, 11.2f)
                moveTo(8.5f, 14.8f); lineTo(13.5f, 14.8f)
            }
        }.build()
    }

    val Emoji: ImageVector by lazy {
        ImageVector.Builder("Emoji", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = stroke, strokeLineWidth = 2.35f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3.4f)
                arcTo(8.6f, 8.6f, 0f, true, true, 11.99f, 3.4f)
                close()
                moveTo(9f, 9.2f); verticalLineTo(10.5f)
                moveTo(15f, 9.2f); verticalLineTo(10.5f)
                moveTo(8.5f, 14.1f)
                curveTo(9.4f, 15.7f, 10.7f, 16.5f, 12f, 16.5f)
                curveTo(13.3f, 16.5f, 14.6f, 15.7f, 15.5f, 14.1f)
            }
        }.build()
    }

    val ShiftOutline: ImageVector by lazy {
        ImageVector.Builder("ShiftOutline", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = stroke, strokeLineWidth = 2.45f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3.8f)
                lineTo(4.2f, 12.2f)
                horizontalLineTo(8.8f)
                verticalLineTo(19.2f)
                horizontalLineTo(15.2f)
                verticalLineTo(12.2f)
                horizontalLineTo(19.8f)
                close()
            }
        }.build()
    }

    val ShiftFilled: ImageVector by lazy {
        ImageVector.Builder("ShiftFilled", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = stroke, stroke = stroke, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 4f)
                lineTo(4.5f, 12.2f)
                horizontalLineTo(9f)
                verticalLineTo(19f)
                horizontalLineTo(15f)
                verticalLineTo(12.2f)
                horizontalLineTo(19.5f)
                close()
            }
        }.build()
    }

    val CapsLockFilled: ImageVector by lazy {
        ImageVector.Builder("CapsLockFilled", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = stroke, stroke = stroke, strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3.2f)
                lineTo(4.6f, 11f)
                horizontalLineTo(9f)
                verticalLineTo(16f)
                horizontalLineTo(15f)
                verticalLineTo(11f)
                horizontalLineTo(19.4f)
                close()
                moveTo(8.5f, 19f)
                horizontalLineTo(15.5f)
                verticalLineTo(20.6f)
                horizontalLineTo(8.5f)
                close()
            }
        }.build()
    }

    val CheckCircle: ImageVector by lazy {
        ImageVector.Builder("CheckCircle", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = stroke, strokeLineWidth = 2.35f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3.4f)
                arcTo(8.6f, 8.6f, 0f, true, true, 11.99f, 3.4f)
                close()
                moveTo(8.4f, 12.2f)
                lineTo(10.9f, 14.7f)
                lineTo(15.8f, 9.6f)
            }
        }.build()
    }
}
