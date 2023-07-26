/*
 * Copyright 2020 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.streamer.parser.epub

import com.mcxiaoke.koi.HASH
import com.mcxiaoke.koi.ext.toHexBytes
import kotlin.experimental.xor
import org.readium.r2.shared.publication.LazyPublicationResource
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.encryption.encryption
import org.readium.r2.shared.resource.ResourceTry
import org.readium.r2.shared.resource.TransformingResource

/**
 * Deobfuscates fonts according to https://www.w3.org/TR/epub-33/#sec-font-obfuscation
 */
internal class EpubDeobfuscator(private val pubId: String) {

    fun transform(resource: Publication.Resource): Publication.Resource = LazyPublicationResource(key = resource.key) {
        val link = resource.link()
        val algorithm = link.properties.encryption?.algorithm
        if (algorithm != null && algorithm2length.containsKey(algorithm)) {
            Publication.Resource(DeobfuscatingResource(resource, algorithm), link)
        } else {
            resource
        }
    }

    inner class DeobfuscatingResource(
        private val resource: Publication.Resource,
        private val algorithm: String
    ) : TransformingResource(resource) {

        // The obfuscation doesn't change the length of the resource.
        override suspend fun length(): ResourceTry<Long> =
            resource.length()

        override suspend fun transform(data: ResourceTry<ByteArray>): ResourceTry<ByteArray> =
            data.map { bytes ->
                val obfuscationLength: Int = algorithm2length[algorithm]
                    ?: return@map bytes

                val obfuscationKey: ByteArray = when (algorithm) {
                    "http://ns.adobe.com/pdf/enc#RC" -> getHashKeyAdobe(pubId)
                    else -> HASH.sha1(pubId).toHexBytes()
                }

                deobfuscate(bytes = bytes, obfuscationKey = obfuscationKey, obfuscationLength = obfuscationLength)
                bytes
            }
    }

    private val algorithm2length: Map<String, Int> = mapOf(
        "http://www.idpf.org/2008/embedding" to 1040,
        "http://ns.adobe.com/pdf/enc#RC" to 1024
    )

    private fun deobfuscate(bytes: ByteArray, obfuscationKey: ByteArray, obfuscationLength: Int) {
        @Suppress("NAME_SHADOWING")
        val toDeobfuscate = 0 until obfuscationLength
        for (i in toDeobfuscate)
            bytes[i] = bytes[i].xor(obfuscationKey[i % obfuscationKey.size])
    }

    private fun getHashKeyAdobe(pubId: String) =
        pubId.replace("urn:uuid:", "")
            .replace("-", "")
            .toHexBytes()
}
