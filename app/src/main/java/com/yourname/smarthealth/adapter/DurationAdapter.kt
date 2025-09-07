package com.yourname.smarthealth.adapter

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.time.Duration

class DurationAdapter : JsonSerializer<Duration>, JsonDeserializer<Duration> {
    override fun serialize(
        src: Duration,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        return JsonPrimitive(src.toString()) // e.g. "PT18M46.874S"
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Duration {
        return Duration.parse(json.asString)
    }
}