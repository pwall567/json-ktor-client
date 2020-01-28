/*
 * @(#) JSONKtorClient.kt
 *
 * json-ktor-client JSON functionality for ktor HTTP clients
 * Copyright (c) 2019, 2020 Peter Wall
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.pwall.json.ktor.client

import kotlinx.io.core.Input

import io.ktor.client.call.TypeInfo
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.JsonSerializer
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent

import net.pwall.json.JSONAuto
import net.pwall.json.JSONConfig
import net.pwall.json.JSONDeserializer
import net.pwall.json.JSONException
import net.pwall.json.stream.JSONStreamProcessor
import net.pwall.json.toKType
import net.pwall.util.pipeline.DecoderFactory
import java.nio.ByteBuffer

/**
 * JSON serializer and deserializer for ktor client calls, using the
 * [json-kotlin](https://github.com/pwall567/json-kotlin) library.
 *
 * @property    config  an optional [JSONConfig]
 * @constructor         creates a `JSONKtorClient` object for use in ktor client configuration.
 * @author  Peter Wall
 */
class JSONKtorClient(private val config: JSONConfig = JSONConfig.defaultConfig) : JsonSerializer {

    /**
     * Serialize data object for output.
     *
     * @param   data        the object to be serialized
     * @param   contentType the content type (assumed to be "application/json")
     */
    override fun write(data: Any, contentType: ContentType): OutgoingContent =
            TextContent(JSONAuto.stringify(data, config), contentType)

    /**
     * Deserialize data object from input.  This uses a pipelining JSON library to parse JSON on the fly.
     *
     * @param   type    the type information
     * @param   body    the response body to be deserialized
     */
    override fun read(type: TypeInfo, body: Input): Any {
        DecoderFactory.getDecoder(config.charset, JSONStreamProcessor()).use { pipeline ->
            val buffer = ByteBuffer.allocate(config.readBufferSize)
            while (!body.endOfInput) {
                body.readAvailable(buffer)
                buffer.flip()
                while (buffer.hasRemaining())
                    pipeline.accept(buffer.get().toInt())
                buffer.clear()
            }
//            while (!body.endOfInput)
//                pipeline.accept(body.readByte().toInt())
            if (!pipeline.isComplete)
                throw JSONException("Incomplete sequence")
            return JSONDeserializer.deserialize(type.reifiedType.toKType(), pipeline.result, config) ?:
                    throw JSONException("Input is null")
        }
    }

}

/**
 * Register the content converter and configure the [JSONConfig] used by it.
 *
 * @param   block   a block of code to initialise the [JSONConfig]
 */
fun JsonFeature.Config.jsonKtorClient(block: JSONConfig.() -> Unit = {}) {
    serializer = JSONKtorClient(JSONConfig().apply(block))
}
