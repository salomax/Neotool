package io.github.salomax.neotool.test.assertions

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat

/** Assert status equals expected. */
fun HttpResponse<*>.shouldHaveStatus(expected: HttpStatus): HttpResponse<*> {
  assertThat(this.status).isEqualTo(expected)
  return this
}

/** Assert status is 2xx. */
fun HttpResponse<*>.shouldBeSuccessful(): HttpResponse<*> {
  assertThat(this.status.code).isBetween(200, 299)
  return this
}

/** Assert Content-Type contains application/json. */
fun HttpResponse<*>.shouldBeJson(): HttpResponse<*> {
  assertThat(this.contentType.isPresent).isTrue()
  assertThat(this.contentType.get().toString()).contains("application/json")
  return this
}

/** Assert body **/
fun HttpResponse<*>.shouldHaveNonEmptyBody(): HttpResponse<*>  {
  assertThat(this.body.isPresent).isTrue()
  assertThat(this.body).isNotNull()
  val body = this.body.get()
  if (body is String) {
    assertThat(body).isNotBlank()
  }
  return this
}

fun HttpResponse<*>.shouldHaveErrors() {
  assertThat(this.body.isPresent).isTrue()
  assertThat(this.body).isNotNull()
  val body = this.body.get()
  assertThat(body.toString()).contains("errors")
}
