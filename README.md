# json-ktor-client

[![Build Status](https://travis-ci.org/pwall567/json-ktor-client.svg?branch=master)](https://travis-ci.org/pwall567/json-ktor-client)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/static/v1?label=Kotlin&message=v1.4.0&color=blue&logo=kotlin)](https://github.com/JetBrains/kotlin/releases/tag/v1.4.0)
[![Maven Central](https://img.shields.io/maven-central/v/net.pwall.json/json-ktor-client?label=Maven%20Central)](https://search.maven.org/search?q=g:%22net.pwall.json%22%20AND%20a:%22json-ktor-client%22)

JSON functionality for ktor HTTP clients

This library provides ktor client interface integration for the [`json-kotlin`](https://github.com/pwall567/json-kotlin)
library.

## Quick Start

In the `HttpClient` engine specification, use:
```kotlin
    val client = HttpClient(HttpClientEngine) {
        Json {
            jsonKtorClient {}
        }
    }
```

Customizations (e.g custom serialization or deserialization) may be specified within the lambda supplied to the
`jsonKtorClient` function:
```kotlin
    val client = HttpClient(HttpClientEngine) {
        Json {
            jsonKtorClient {
                fromJSON { json ->
                    require(json is JSONObject) { "Must be JSONObject" }
                    Example(json.getString("custom1"), json.getInt("custom2"))
                }
            }
        }
    }
```
For more details see the `json-kotlin` library.

## Streaming Input

From version 0.2 onwards, this library uses the `json-streaming` library for on-the-fly JSON parsing.
This means that the input data is parsed into an internal form as it is being read, and avoids the need to allocate
memory for the entire JSON text.

## Streaming Output

From version 0.5, the library will (optionally) stream output using a non-blocking output library
([`json-kotlin-nonblocking`](https://github.com/pwall567/json-kotlin-nonblocking)).

More documentation will be available shortly; in the meantime the unit test classes contain examples.

## Dependency Specification

The latest version of the library is 0.7, and it may be obtained from the Maven Central repository.

### Maven
```xml
    <dependency>
      <groupId>net.pwall.json</groupId>
      <artifactId>json-ktor-client</artifactId>
      <version>0.7</version>
    </dependency>
```
### Gradle
```groovy
    implementation 'net.pwall.json:json-ktor-client:0.7'
```
### Gradle (kts)
```kotlin
    implementation("net.pwall.json:json-ktor-client:0.7")
```

Peter Wall

2021-04-25
