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

## Dependency Specification

The latest version of the library is 0.1, and it may be found the the Maven Central repository.

### Maven
```xml
    <dependency>
      <groupId>net.pwall.json</groupId>
      <artifactId>json-ktor-client</artifactId>
      <version>0.1</version>
    </dependency>
```
### Gradle
```groovy
    implementation "net.pwall.json:json-ktor-client:0.1"
```
### Gradle (kts)
```kotlin
    implementation("net.pwall.json:json-ktor-client:0.1")
```

Peter Wall

2019-11-17
