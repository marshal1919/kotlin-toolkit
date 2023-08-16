/*
 * Copyright 2023 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.downloads.android

import android.app.DownloadManager
import android.database.Cursor

internal class DownloadCursorFacade(
    private val cursor: Cursor
) {

    val id: Long = cursor
        .getColumnIndex(DownloadManager.COLUMN_ID)
        .also { require(it != -1) }
        .let { cursor.getLong(it) }

    val localUri: String? = cursor
        .getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        .also { require(it != -1) }
        .let { cursor.getString(it) }

    val status: Int = cursor
        .getColumnIndex(DownloadManager.COLUMN_STATUS)
        .also { require(it != -1) }
        .let { cursor.getInt(it) }

    val total: Long = cursor
        .getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        .also { require(it != -1) }
        .let { cursor.getLong(it) }

    val downloadedSoFar: Long = cursor
        .getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        .also { require(it != -1) }
        .let { cursor.getLong(it) }

    val reason: Int? = cursor
        .getColumnIndex(DownloadManager.COLUMN_REASON)
        .also { require(it != -1) }
        .takeIf { status == DownloadManager.STATUS_FAILED || status == DownloadManager.STATUS_PAUSED }
        ?.let { cursor.getInt(it) }
}
