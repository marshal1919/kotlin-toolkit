/*
 * Module: r2-shared-kotlin
 * Developers: Mickaël Menu
 *
 * Copyright (c) 2020. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.shared.format

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.parser.xml.ElementNode
import org.readium.r2.shared.parser.xml.XmlParser
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.Publication
import java.io.InputStream
import java.nio.charset.Charset
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile


/**
 * A companion type of [Format.Sniffer] holding the type hints (file extensions, media types) and
 * providing an access to the file content.
 *
 * @param content Underlying content holder.
 * @param mediaTypes Media type hints.
 * @param fileExtensions File extension hints.
 */
class FormatSnifferContext internal constructor(
    private val content: FormatSnifferContent? = null,
    mediaTypes: List<String>,
    fileExtensions: List<String>
) {

    /** Media type hints. */
    val mediaTypes: List<MediaType> = mediaTypes
        .mapNotNull { MediaType.parse(it) }

    /** File extension hints. */
    val fileExtensions: List<String> = fileExtensions
        .map { it.toLowerCase(Locale.ROOT) }

    // Metadata

    /** Finds the first [Charset] declared in the media types' `charset` parameter. */
    val charset: Charset? by lazy {
        this.mediaTypes.mapNotNull { it.charset }.firstOrNull()
    }

    /** Returns whether this context has any of the given file extensions, ignoring case. */
    fun hasFileExtension(vararg fileExtensions: String): Boolean {
        for (fileExtension in fileExtensions) {
            if (this.fileExtensions.contains(fileExtension.toLowerCase(Locale.ROOT))) {
                return true
            }
        }
        return false
    }

    /**
     * Returns whether this context has any of the given media type, ignoring case and extra
     * parameters.
     *
     * Implementation note: Use [MediaType] to handle the comparison to avoid edge cases.
     */
    fun hasMediaType(vararg mediaTypes: String): Boolean {
        @Suppress("NAME_SHADOWING")
        val mediaTypes = mediaTypes.mapNotNull { MediaType.parse(it) }
        for (mediaType in mediaTypes) {
            if (this.mediaTypes.any { mediaType.contains(it) }) {
                return true
            }
        }
        return false
    }

    // Content

    /**
     * Content as plain text.
     *
     * It will extract the charset parameter from the media type hints to figure out an encoding.
     * Otherwise, fallback on UTF-8.
     */
    suspend fun contentAsString(): String? {
        if (!loadedContentAsString) {
            loadedContentAsString = true
            _contentAsString = content?.read()?.toString(charset ?: Charset.defaultCharset())
        }
        return _contentAsString
    }

    private var loadedContentAsString: Boolean = false
    private var _contentAsString: String? = null

    /** Content as an XML document. */
    suspend fun contentAsXml(): ElementNode? {
        if (!loadedContentAsXml) {
            loadedContentAsXml = true
            _contentAsXml = withContext(Dispatchers.IO) {
                try {
                    stream()?.let { XmlParser().parse(it) }
                } catch (e: Exception) {
                    null
                }
            }
        }

        return _contentAsXml
    }

    private var loadedContentAsXml: Boolean = false
    private var _contentAsXml: ElementNode? = null

    /**
     * Content as a ZIP archive.
     * Warning: ZIP is only supported for a local file, for now.
     */
    suspend fun contentAsZip(): ZipFile? {
        if (!loadedContentAsZip) {
            loadedContentAsZip = true
            _contentAsZip = withContext(Dispatchers.IO) {
                try {
                    (content as? FormatSnifferFileContent)?.let {
                        ZipFile(it.file)
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }

        return _contentAsZip
    }

    private var loadedContentAsZip: Boolean = false
    private var _contentAsZip: ZipFile? = null

    /**
     * Content parsed from JSON.
     */
    suspend fun contentAsJson(): JSONObject? =
        try {
            contentAsString()?.let { JSONObject(it) }
        } catch (e: Exception) {
            null
        }

    /** Publication parsed from the content. */
    suspend fun contentAsRwpm(): Publication? =
            Manifest.fromJSON(contentAsJson())
                ?.let { Publication(it) }

    /**
     * Raw bytes stream of the content.
     *
     * A byte stream can be useful when sniffers only need to read a few bytes at the beginning of
     * the file.
     */
    suspend fun stream(): InputStream? = content?.stream()

    /**
     * Reads the file signature, aka magic number, at the beginning of the content, up to [length]
     * bytes.
     *
     * i.e. https://en.wikipedia.org/wiki/List_of_file_signatures
     */
    suspend fun readFileSignature(length: Int): String? = withContext(Dispatchers.IO) {
            try {
            stream()?.let {
                val buffer = ByteArray(length)
                it.read(buffer, 0, length)
                String(buffer)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns whether the content is a JSON object containing all of the given root keys.
     */
    internal suspend fun containsJsonKeys(vararg keys: String): Boolean {
        val json = contentAsJson() ?: return false
        return json.keys().asSequence().toSet().containsAll(keys.toList())
    }

    /**
     * Returns whether a ZIP entry exists in this file.
     */
    internal suspend fun containsZipEntryAt(path: String): Boolean =
        try {
            contentAsZip()?.getEntry(path) != null
        } catch (e: Exception) {
            false
        }

    /**
     * Returns the ZIP entry data at the given [path] in this file.
     */
    internal suspend fun readZipEntryAt(path: String): ByteArray? {
        val archive = contentAsZip() ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val entry = archive.getEntry(path)
                archive.getInputStream(entry).readBytes()
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Returns whether all the ZIP entry paths satisfy the given `predicate`.
     */
    internal suspend fun zipEntriesAllSatisfy(predicate: (ZipEntry) -> Boolean): Boolean =
        try {
            contentAsZip()?.entries()?.asSequence()?.all(predicate) == true
        } catch (e: Exception) {
            false
        }

}
