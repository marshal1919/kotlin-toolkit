/*
 * Copyright 2022 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.navigator.epub

import org.readium.r2.navigator.preferences.*
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Language

@ExperimentalReadiumApi
data class EpubDefaults(
    val language: Language? = null,
    val readingProgression: ReadingProgression? = null,
    val scroll: Boolean? = null,
    val spread: Spread? = null,
    val columnCount: ColumnCount? = null,
    val publisherStyles: Boolean? = null,
    val imageFilter: ImageFilter? = null,
    val fontSize: Double? = null,
    val letterSpacing: Double? = null,
    val lineHeight: Double? = null,
    val pageMargins: Double? = null,
    val paragraphIndent: Double? = null,
    val paragraphSpacing: Double? = null,
    val textAlign: TextAlign? = null,
    val textNormalization: TextNormalization? = null,
    val ligatures: Boolean? = null,
    val hyphens: Boolean? = null,
    val typeScale: Double? = null,
    val wordSpacing: Double? = null
)
