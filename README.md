# json-ktor-client

JSON functionality for ktor HTTP clients

This library provides ktor client interface integration for the [`json-kotlin`](https://github.com/pwall567/json-kotlin)
library.

## Quick Start

In the `HttpClient` engine specification, use:
```kotlin
    val client = HttpClient(HttpClientEngine) {
        install(JsonFeature) {
            jsonKtorClient {}
        }
    }
```

Customizations (e.g custom serialization or deserialization) may be specified within the lambda supplied to the
`jsonKtorClient` function:
```kotlin
    val client = HttpClient(HttpClientEngine) {
        install(JsonFeature) {
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

Later versions will allow JSON array content to be streamed to a Kotlin co-routine `Channel` for asynchronous
processing.
Watch this space.

## Dependency Specification

The latest version of the library is 0.4, and it may be obtained from the Maven Central repository.

### Maven
```xml
    <dependency>
      <groupId>net.pwall.json</groupId>
      <artifactId>json-ktor-client</artifactId>
      <version>0.4</version>
    </dependency>
```
### Gradle
```groovy
    implementation 'net.pwall.json:json-ktor-client:0.4'
```
### Gradle (kts)
```kotlin
    implementation("net.pwall.json:json-ktor-client:0.4")
```

Peter Wall

2020-02-02
