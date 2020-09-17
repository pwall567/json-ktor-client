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

import io.ktor.client.HttpClient
import io.ktor.client.call.TypeInfo
import io.ktor.client.features.feature
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.JsonSerializer
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.client.utils.EmptyContent
import io.ktor.features.NotFoundException
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.takeFrom
import io.ktor.util.KtorExperimentalAPI
import io.ktor.utils.io.core.Input
import io.ktor.utils.io.core.readAvailable

import net.pwall.json.JSONAuto
import net.pwall.json.JSONConfig
import net.pwall.json.JSONDeserializer
import net.pwall.json.JSONException
import net.pwall.json.JSONTypeRef
import net.pwall.json.ktor.JSONKtorFunctions.copyToPipeline
import net.pwall.json.ktor.JSONKtorFunctions.createOutgoingContent
import net.pwall.json.stream.JSONArrayCoPipeline
import net.pwall.json.stream.JSONStream
import net.pwall.json.toKType
import net.pwall.util.pipeline.CoDecoderFactory
import net.pwall.util.pipeline.DecoderFactory
import net.pwall.util.pipeline.simpleCoAcceptor

/**
 * JSON serializer and deserializer for ktor client calls, using the
 * [json-kotlin](https://github.com/pwall567/json-kotlin) library.
 *
 * @property    config  an optional [JSONConfig]
 * @constructor         creates a `JSONKtorClient` object for use in ktor client configuration.
 * @author  Peter Wall
 */
class JSONKtorClient(val config: JSONConfig = JSONConfig.defaultConfig) : JsonSerializer {

    /**
     * Serialize data object for output.
     *
     * @param   data        the object to be serialized
     * @param   contentType the content type (assumed to be "application/json")
     */
    override fun write(data: Any, contentType: ContentType): OutgoingContent {
        if (data is OutgoingContent)
            return data
        if (config.streamOutput) {
            val charset = contentType.charset() ?: config.charset
            return createOutgoingContent(data, contentType, charset, config)
        }
        return TextContent(JSONAuto.stringify(data, config), contentType)
    }

    /**
     * Deserialize data object from input.  This uses a pipelining JSON library to parse JSON on the fly.
     *
     * @param   type    the type information
     * @param   body    the response body to be deserialized
     */
    override fun read(type: TypeInfo, body: Input): Any {
        val pipeline = DecoderFactory.getDecoder(config.charset, JSONStream())
        val buffer = ByteArray(config.readBufferSize)
        while (!body.endOfInput) {
            val bytesRead = body.readAvailable(buffer, 0, buffer.size)
            if (bytesRead < 0)
                break
            for (i in 0 until bytesRead)
                pipeline.acceptInt(buffer[i].toInt() and 0xFF)
        }
        pipeline.close()
        return JSONDeserializer.deserialize(type.reifiedType.toKType(), pipeline.result, config) ?:
                throw JSONException("Input is null")
    }

}

/**
 * Register the content converter, supplying the [JSONConfig] to be used by it.
 *
 * @param   config      a [JSONConfig]
 * @param   contentType the content type (default `application/json`)
 */
@KtorExperimentalAPI
fun JsonFeature.Config.jsonKtorClient(config: JSONConfig, contentType: ContentType = ContentType.Application.Json) {
    serializer = JSONKtorClient(config)
    acceptContentTypes = listOf(contentType)
}

/**
 * Register the content converter, supplying the [JSONConfig] to be used by it.
 *
 * @param   config      a [JSONConfig]
 */
fun JsonFeature.Config.jsonKtorClient(config: JSONConfig) {
    serializer = JSONKtorClient(config)
}

/**
 * Register the content converter and configure a new [JSONConfig] to be used by it.
 *
 * @param   contentType the content type (default `application/json`)
 * @param   block       a block of code to initialise the [JSONConfig]
 */
@KtorExperimentalAPI
fun JsonFeature.Config.jsonKtorClient(contentType: ContentType = ContentType.Application.Json,
        block: JSONConfig.() -> Unit = {}) {
    serializer = JSONKtorClient(JSONConfig().apply(block))
    acceptContentTypes = listOf(contentType)
}

/**
 * Register the content converter and configure a new [JSONConfig] to be used by it.
 *
 * @param   block       a block of code to initialise the [JSONConfig]
 */
fun JsonFeature.Config.jsonKtorClient(block: JSONConfig.() -> Unit = {}) {
    serializer = JSONKtorClient(JSONConfig().apply(block))
}

/**
 * Receive a stream of objects deserialized from an input JSON array.
 *
 * @param   urlString       the URL string
 * @param   method          the HTTP method (default GET)
 * @param   body            an optional POST body
 * @param   headers         an optional set of [Headers]
 * @param   expectedStatus  the expected HTTP status code (default OK)
 * @param   block           a block of code to be invoked with each object
 * @param   T               the type of the objects
 */
@KtorExperimentalAPI
suspend inline fun <reified T: Any> HttpClient.receiveStreamJSON(urlString: String, method: HttpMethod = HttpMethod.Get,
        body: Any = EmptyContent, headers: Headers = Headers.Empty, expectedStatus: HttpStatusCode = HttpStatusCode.OK,
        crossinline block: suspend (T) -> Unit) {
    val config = (feature(JsonFeature)?.serializer as? JSONKtorClient)?.config ?: JSONConfig.defaultConfig
    val statement = request<HttpStatement> {
        url.takeFrom(urlString)
        this.method = method
        this.body = body
        this.headers.appendAll(headers)
    }
    statement.execute { response: HttpResponse ->
        when (response.status) {
            expectedStatus -> {
                val pipeline = CoDecoderFactory.getDecoder(config.charset, JSONArrayCoPipeline(simpleCoAcceptor {
                    block(JSONDeserializer.deserialize(JSONTypeRef.create<T>(false).refType, it, config) as T)
                }))
                response.content.copyToPipeline(pipeline, config.readBufferSize)
            }
            HttpStatusCode.NotFound -> throw NotFoundException()
            else -> throw JSONException("Unexpected status code - ${response.status}")
        }
    }
}
