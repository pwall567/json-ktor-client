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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.headersOf

import net.pwall.json.Dummy1
import net.pwall.json.Dummy3
import net.pwall.json.Dummy4
import net.pwall.json.JSONObject
import net.pwall.json.isJSON
import net.pwall.json.parseJSON
import net.pwall.json.stringifyJSON

class TestJSONKtorClient {

    @Test fun `client response should be converted to and from JSON`() {

        runBlocking {
            val client = HttpClient(MockEngine) {
                install(JsonFeature) {
                    jsonKtorClient {}
                }
                engine {
                    addHandler {
                        val responseObject = Dummy1("abc", 27)
                        respond(responseObject.stringifyJSON(), HttpStatusCode.OK,
                                headersOf("Content-Type", ContentType.Application.Json.toString()))
                    }
                }
            }

            client.use {
                it.get<HttpResponse>("/").use { response ->
                    val result = response.receive<Dummy1>()
                    assertEquals(Dummy1("abc", 27), result)
                }
            }
        }

    }

    @Test fun `client request body and response should be converted to and from JSON`() {

        runBlocking {
            val client = HttpClient(MockEngine) {
                install(JsonFeature) {
                    jsonKtorClient {}
                }
                engine {
                    addHandler { request ->
                        assertTrue(request.body is TextContent)
                        val requestObject: Dummy1? = (request.body as TextContent).text.parseJSON()
                        assertEquals(Dummy1("def", 88), requestObject)
                        val responseObject = Dummy3(requestObject!!, "OK")
                        respond(responseObject.stringifyJSON(), HttpStatusCode.OK,
                                headersOf("Content-Type", ContentType.Application.Json.toString()))
                    }
                }
            }

            client.use {
                it.post<HttpResponse>("/") {
                    contentType(ContentType.Application.Json)
                    body = Dummy1("def", 88)
                }.use { response ->
                    val result = response.receive<Dummy3>()
                    assertEquals(Dummy3(Dummy1("def", 88), "OK"), result)
                }
            }
        }

    }

    @Test fun `complex client request and response should be converted including custom serialization`() {

        runBlocking {
            val client = HttpClient(MockEngine) {
                install(JsonFeature) {
                    jsonKtorClient {
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
                }
                engine {
                    addHandler { request ->
                        assertTrue(request.body is TextContent)
                        val requestObject: Dummy3? = (request.body as TextContent).text.parseJSON()
                        // Dummy3 was serialized with custom serialization, reversing the text field
                        assertEquals(Dummy3(Dummy1("Hello", 2000), "DLROW"), requestObject)
                        val responseObject = Dummy4(listOf(requestObject!!.dummy1), "Magenta")
                        respond(responseObject.stringifyJSON(), HttpStatusCode.OK,
                                headersOf("Content-Type", ContentType.Application.Json.toString()))
                    }
                }
            }

            client.use {
                it.post<HttpResponse>("/") {
                    contentType(ContentType.Application.Json)
                    body = Dummy3(Dummy1("Hello", 2000), "WORLD")
                }.use { response ->
                    val result = response.receive<Dummy4>()
                    assertEquals(Dummy4(listOf(Dummy1("Hello", 2000)), "Magenta"), result)
                }
            }
        }

    }

}
