/* Copyright 2025 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0; see the source headers of the four retained icons.
 */
package com.tradereview.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal fun materialIcon(name:String,autoMirror:Boolean=false,block:ImageVector.Builder.()->Unit):ImageVector =
    ImageVector.Builder(name=name,defaultWidth=24.dp,defaultHeight=24.dp,viewportWidth=24f,viewportHeight=24f,autoMirror=autoMirror).apply(block).build()
internal fun ImageVector.Builder.materialPath(block:PathBuilder.()->Unit) = path(fill=SolidColor(Color.Black),pathFillType=PathFillType.NonZero){block()}
