package com.bankapp.cardservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@EnableCaching
@SpringBootApplication
class CardServiceApplication

fun main(args: Array<String>) {
    runApplication<CardServiceApplication>(*args)
}
