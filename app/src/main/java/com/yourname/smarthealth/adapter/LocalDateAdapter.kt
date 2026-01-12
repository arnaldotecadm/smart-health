package com.yourname.smarthealth.adapter

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class LocalDateAdapter : JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {

    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    override fun serialize(
        src: LocalDate?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        if (src == null) return JsonNull.INSTANCE
        return JsonPrimitive(src.format(formatter))
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): LocalDate {
        if (json == null || json.isJsonNull) {
            throw JsonParseException("LocalDate cannot be null")
        }
        return LocalDate.parse(json.asString, formatter)
    }
}
