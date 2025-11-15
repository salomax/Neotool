package io.github.salomax.neotool.test.http

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient

fun <T> HttpClient.exchangeAsString(req: HttpRequest<T>): HttpResponse<String> =
  this.toBlocking().exchange(req, String::class.java)
