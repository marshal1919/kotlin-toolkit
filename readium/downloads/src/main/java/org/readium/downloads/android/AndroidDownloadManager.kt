/*
 * Copyright 2023 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.downloads.android

import android.app.DownloadManager as SystemDownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.readium.downloads.DownloadManager
import org.readium.r2.shared.units.Hz

public class AndroidDownloadManager(
    private val context: Context,
    private val destStorage: Storage,
    private val dirType: String,
    private val refreshRate: Hz,
    private val listener: DownloadManager.Listener
) : DownloadManager {

    public enum class Storage {
        App,
        Shared;
    }

    private val coroutineScope: CoroutineScope =
        MainScope()

    private val progressJob: Job = coroutineScope.launch {
        while (true) {
            val cursor = downloadManager.query(SystemDownloadManager.Query())
            notify(cursor)
            delay((1.0 / refreshRate.value).seconds)
        }
    }

    private val downloadManager: SystemDownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as SystemDownloadManager

    override fun submit(request: DownloadManager.Request): DownloadManager.RequestId {
        val uri = Uri.parse(request.url.toString())
        val filename = filenameForUri(uri.toString())
        val androidRequest = createRequest(uri, filename, request.headers, request.title, request.description)
        val downloadId = downloadManager.enqueue(androidRequest)
        return DownloadManager.RequestId(downloadId)
    }

    private fun filenameForUri(uri: String): String =
        uri.substring(uri.lastIndexOf('/') + 1)
            .let { it.substring(0, 1).uppercase(Locale.getDefault()) + it.substring(1) }

    private fun createRequest(
        uri: Uri,
        filename: String,
        headers: Map<String, List<String>>,
        title: String,
        description: String
    ): SystemDownloadManager.Request =
        SystemDownloadManager.Request(uri)
            .setNotificationVisibility(SystemDownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestination(filename)
            .setHeaders(headers)
            .setTitle(title)
            .setDescription(description)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

    private fun SystemDownloadManager.Request.setHeaders(
        headers: Map<String, List<String>>
    ): SystemDownloadManager.Request {
        for (header in headers) {
            for (value in header.value) {
                addRequestHeader(header.key, value)
            }
        }
        return this
    }

    private fun SystemDownloadManager.Request.setDestination(
        filename: String
    ): SystemDownloadManager.Request {
        when (destStorage) {
            Storage.App ->
                setDestinationInExternalFilesDir(context, dirType, filename)

            Storage.Shared ->
                setDestinationInExternalPublicDir(dirType, filename)
        }
        return this
    }

    private fun notify(cursor: Cursor) = cursor.use {
        while (cursor.moveToNext()) {
            val facade = DownloadCursorFacade(cursor)

            val id = DownloadManager.RequestId(facade.id)

            Log.d("AndroidDownloadManager", "${facade.id} ${facade.localUri}")

            when (facade.status) {
                SystemDownloadManager.STATUS_FAILED -> {
                    listener.onDownloadFailed(id, mapErrorCode(facade.reason!!))
                    downloadManager.remove(id.value)
                }
                SystemDownloadManager.STATUS_PAUSED -> {}
                SystemDownloadManager.STATUS_PENDING -> {}
                SystemDownloadManager.STATUS_SUCCESSFUL -> {
                    val destUri = Uri.parse(facade.localUri!!)
                    listener.onDownloadCompleted(id, destUri)
                    downloadManager.remove(id.value)
                }
                SystemDownloadManager.STATUS_RUNNING -> {
                    val total = facade.total
                    if (total > 0) {
                        listener.onDownloadProgressed(id, facade.downloadedSoFar, total)
                    }
                }
            }
        }
    }

    private fun mapErrorCode(code: Int): DownloadManager.Error =
        when (code) {
            401, 403 ->
                DownloadManager.Error.Forbidden
            404 ->
                DownloadManager.Error.NotFound
            500, 501 ->
                DownloadManager.Error.Server
            502, 503, 504 ->
                DownloadManager.Error.Unreachable
            SystemDownloadManager.ERROR_CANNOT_RESUME ->
                DownloadManager.Error.CannotResume
            SystemDownloadManager.ERROR_DEVICE_NOT_FOUND ->
                DownloadManager.Error.DeviceNotFound
            SystemDownloadManager.ERROR_FILE_ERROR ->
                DownloadManager.Error.FileError
            SystemDownloadManager.ERROR_HTTP_DATA_ERROR ->
                DownloadManager.Error.HttpData
            SystemDownloadManager.ERROR_INSUFFICIENT_SPACE ->
                DownloadManager.Error.InsufficientSpace
            SystemDownloadManager.ERROR_TOO_MANY_REDIRECTS ->
                DownloadManager.Error.TooManyRedirects
            SystemDownloadManager.ERROR_UNHANDLED_HTTP_CODE ->
                DownloadManager.Error.Unknown
            SystemDownloadManager.ERROR_UNKNOWN ->
                DownloadManager.Error.Unknown
            else ->
                DownloadManager.Error.Unknown
        }

    public override fun close() {
        progressJob.cancel()
    }
}
