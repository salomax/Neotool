package io.github.salomax.neotool.test.http

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType

fun get(path: String): HttpRequest<Any> = HttpRequest.GET<Any>(path)

fun jsonPost(path: String, body: Any): HttpRequest<Any> =
  HttpRequest.POST(path, body).contentType(MediaType.APPLICATION_JSON_TYPE)

fun jsonPut(path: String, body: Any): HttpRequest<Any> =
  HttpRequest.PUT(path, body).contentType(MediaType.APPLICATION_JSON_TYPE)
