/*
 * @(#) TestJSONKtorClient.kt
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

import kotlin.test.Test
import kotlin.test.expect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.features.json.Json
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.util.KtorExperimentalAPI
import io.ktor.utils.io.ByteChannel

import net.pwall.json.Dummy1
import net.pwall.json.Dummy3
import net.pwall.json.Dummy4
import net.pwall.json.JSONConfig
import net.pwall.json.JSONObject
import net.pwall.json.isJSON
import net.pwall.json.parseJSON
import net.pwall.json.stringifyJSON
import net.pwall.pipeline.StringAcceptor
import net.pwall.pipeline.StringCoAcceptor
import net.pwall.pipeline.codec.CoDecoderFactory
import net.pwall.pipeline.codec.DecoderFactory

class TestJSONKtorClient {

    @Test fun `client response should be converted to and from JSON`() = runBlocking {
        val client = HttpClient(MockEngine) {
            Json {
                jsonKtorClient {}
            }
            engine {
                addHandler {
                    val responseObject = Dummy1("abc", 27)
                    respond(responseObject.stringifyJSON(), HttpStatusCode.OK, jsonHeaders)
                }
            }
        }

        val response = client.get<Dummy1>("/")
        expect(Dummy1("abc", 27)) { response }
    }

    @Test fun `client request body and response should be converted to and from JSON`() = runBlocking {
        val client = HttpClient(MockEngine) {
            Json {
                jsonKtorClient {}
            }
            engine {
                addHandler { request ->
                    val requestObject: Dummy1? = request.getRequestContent().parseJSON()
                    expect(Dummy1("def", 88)) { requestObject }
                    val responseObject = Dummy3(requestObject!!, "OK")
                    respond(responseObject.stringifyJSON(), HttpStatusCode.OK, jsonHeaders)
                }
            }
        }

        val response = client.post<Dummy3>("/") {
            contentType(ContentType.Application.Json)
            body = Dummy1("def", 88)
        }
        expect(Dummy3(Dummy1("def", 88), "OK")) { response }
    }

    @KtorExperimentalAPI
    @Test fun `should receive client data via streaming interface`() = runBlocking {
        val client = HttpClient(MockEngine) {
            Json {
                jsonKtorClient {
                    streamOutput = true
                    readBufferSize = 2048
                }
            }
            engine {
                addHandler {
                    val responseObject = listOf(Dummy1("first response", 98765), Dummy1("second", 888))
                    respond(responseObject.stringifyJSON(), HttpStatusCode.OK, jsonHeaders)
                }
            }
        }
        var number = 0
        client.receiveStreamJSON<Dummy1>("/") {
            when (number++) {
                0 -> expect(Dummy1("first response", 98765)) { it }
                1 -> expect(Dummy1("second", 888)) { it }
            }
        }
    }

    @Test fun `complex client request and response should be converted including custom serialization`() = runBlocking {
        val client = HttpClient(MockEngine) {
            val config = JSONConfig().apply {
                toJSON<Dummy3> { obj ->
                    obj?.let {
                        JSONObject(
                            mapOf(
                                "dummy1" to JSONObject(
                                    mapOf("field1" isJSON it.dummy1.field1, "field2" isJSON it.dummy1.field2)),
                                "text" isJSON it.text.reversed()
                            )
                        )
                    }
                }
            }
            Json {
                jsonKtorClient(config)
            }
            engine {
                addHandler { request ->
                    val requestObject: Dummy3? = request.getRequestContent().parseJSON()
                    // Dummy3 was serialized with custom serialization, reversing the text field
                    expect(Dummy3(Dummy1("Hello", 2000), "DLROW")) { requestObject }
                    val responseObject = Dummy4(listOf(requestObject!!.dummy1), "Magenta")
                    respond(responseObject.stringifyJSON(), HttpStatusCode.OK, jsonHeaders)
                }
            }
        }
        val response = client.post<Dummy4>("/") {
            contentType(ContentType.Application.Json)
            body = Dummy3(Dummy1("Hello", 2000), "WORLD")
        }
        expect(Dummy4(listOf(Dummy1("Hello", 2000)), "Magenta")) { response }
    }

    private suspend fun HttpRequestData.getRequestContent(): String {
        val outgoingContent = body
        val charset = outgoingContent.contentType?.charset() ?: Charsets.UTF_8
        when (outgoingContent) {
            is OutgoingContent.WriteChannelContent -> {
                val channel = ByteChannel()
                val pipeline = CoDecoderFactory.getDecoder(charset, StringCoAcceptor())
                val channelRead = CoroutineScope(Dispatchers.Default).launch {
                    while (!channel.isClosedForRead) {
                        pipeline.accept(channel.readByte().toInt() and 0xFF)
                    }
                    pipeline.close()
                }
                outgoingContent.writeTo(channel)
                channelRead.join()
                return pipeline.result
            }
            is OutgoingContent.ByteArrayContent -> {
                val pipeline = DecoderFactory.getDecoder(charset, StringAcceptor())
                pipeline.accept(outgoingContent.bytes())
                return pipeline.result
            }
            else -> throw IllegalArgumentException("Can't handle ${outgoingContent::class}")
        }
    }

    companion object {
        val jsonHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())
    }

}
