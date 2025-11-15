
package io.github.salomax.neotool.example

import io.micronaut.runtime.Micronaut.build

object Application {
    @JvmStatic
    fun main(args: Array<String>) {
        build(*args)
            .packages("io.github.salomax.neotool.example")
            .start()
    }
}
