package io.github.salomax.neotool.banking

import io.micronaut.runtime.Micronaut

object Application {
    @JvmStatic
    fun main(args: Array<String>) {
        Micronaut
            .build(*args)
            .packages("io.github.salomax.neotool.banking")
            .start()
    }
}

