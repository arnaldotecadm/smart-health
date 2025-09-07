package com.yourname.smarthealth.adapter

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.time.ZoneOffset

class ZoneOffsetAdapter : JsonSerializer<ZoneOffset>, JsonDeserializer<ZoneOffset> {
    override fun serialize(
        src: ZoneOffset,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        return JsonPrimitive(src.toString()) // e.g. "+01:00"
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ZoneOffset {
        return ZoneOffset.of(json.asString)
    }
}