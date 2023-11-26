package org.readium.r2.testapp

//import androidx.test.ext.junit.runners.AndroidJUnit4
/*import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test*/
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

//@RunWith(AndroidJUnit4::class)
@RunWith(RobolectricTestRunner::class)
class WordBookDBTest {

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
    }

    @Test
    fun wBookDao() {
        val a=17
        assertTrue(a==17)
    }
}