package com.ping.app

import org.junit.Assert.*
import org.junit.Test

class FirebaseConfigTest {
    @Test fun acceptsFirebaseConsoleObjectWithoutChangingValues() {
        val json = FirebaseConfig.parse("""const firebaseConfig = {
            apiKey: "key=with=equals",
            databaseURL: "https://example-default-rtdb.firebaseio.com",
            projectId: "example"
        };""")
        FirebaseConfig.validate(json)
        assertEquals("key=with=equals", json.getString("apiKey"))
    }

    @Test fun editingFieldsPreservesAdditionalKeysAndClearsOptionalValues() {
        val original = FirebaseConfig.parse("""{
            "apiKey":"key", "databaseURL":"https://example.firebaseio.com",
            "appId":"example-app-id", "extra":{"enabled":true}
        }""")
        val values = FirebaseConfig.fields(original) + ("appId" to "") + ("projectId" to "updated")
        val roundTrip = FirebaseConfig.parse(FirebaseConfig.mergeFields(original, values).toString())
        assertEquals("updated", roundTrip.getString("projectId"))
        assertEquals("key", roundTrip.getString("apiKey"))
        assertTrue(roundTrip.getJSONObject("extra").getBoolean("enabled"))
        assertFalse(roundTrip.has("appId"))
        assertEquals("example-app-id", original.getString("appId"))
    }

    @Test fun sampleIncludesAllFieldsButCannotBeSaved() {
        val sample = FirebaseConfig.parse(FirebaseConfig.sample)
        assertEquals(4, sample.length())
        FirebaseConfig.keys.forEach { assertTrue(sample.has(it)) }
        assertThrows(IllegalArgumentException::class.java) { FirebaseConfig.validate(sample) }
    }

    @Test fun rejectsMissingRequiredValuesAndInsecureDatabaseUrl() {
        for (source in listOf("{}", """{apiKey:"key"}""",
            """{apiKey:"key",databaseURL:"http://example.firebaseio.com"}""")) {
            assertThrows(IllegalArgumentException::class.java) {
                FirebaseConfig.validate(FirebaseConfig.parse(source))
            }
        }
    }

    @Test fun rejectsNonTextConfigValues() {
        assertThrows(IllegalArgumentException::class.java) {
            FirebaseConfig.parse("""{projectId:123}""")
        }
    }

    @Test fun olderConfigsRemainReadableWithoutUsingExtraFields() {
        val json = FirebaseConfig.parse("""{apiKey:"key",databaseURL:"https://example.firebaseio.com",
            authDomain:"example.firebaseapp.com",messagingSenderId:"123",measurementId:"G-EXAMPLE"}""")
        FirebaseConfig.validate(json)
        assertEquals(setOf("apiKey", "databaseURL", "projectId", "appId"), FirebaseConfig.fields(json).keys)
    }
}
