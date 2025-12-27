package com.example.morp_prj.data.api

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

class LenientBooleanDeserializer : JsonDeserializer<Boolean> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Boolean {
        if (json == null || json.isJsonNull) return false

        try {
            val prim = json.asJsonPrimitive
            when {
                prim.isBoolean -> return prim.asBoolean
                prim.isNumber -> return prim.asNumber.toInt() != 0
                prim.isString -> {
                    val s = prim.asString
                    if (s.equals("true", ignoreCase = true)) return true
                    if (s.equals("false", ignoreCase = true)) return false
                    return try {
                        s.toInt() != 0
                    } catch (e: NumberFormatException) {
                        false
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        return false
    }
}

