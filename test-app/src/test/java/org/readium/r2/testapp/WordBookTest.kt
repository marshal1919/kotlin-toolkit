package org.readium.r2.testapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
//import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext


@RunWith(RobolectricTestRunner::class)
//@Config(constants = BuildConfig.class)
//@Config(application = Application.class)
class WordBookTest {
    private lateinit var db: WordBookDB
    private lateinit var table: WordBookDao
    //private lateinit var test: WordBookTest
    //test=openDB()
    //val a1=ActivityTestRule<>(MainActivity::class, true)
    @Test
    fun testGetId() {
        //val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(4==4)
    }

    @Test
    fun testInsert() {
        CoroutineScope(IO).launch{

            //assertTrue { 4==5 }
            //async(IO){}
        }
        runTest{
            table.insertWord(WordBook("book", "book explain"))
            table.insertWord(WordBook("good", "good explain"))
            table.insertWord(WordBook("yes", "yes explain"))
            val a=table.getAllWords()
            val s="book"
            val d=a.any { it.word==s }
            println("result is:$d")
            val c=table.getAllWordsEn()
            assertTrue(a.size==3)
            assertTrue { c.size==3 }
            val b=WordBook("a","b")
            b.id= a[1].id
            table.deleteWord(b)
            assertTrue { table.getAllWords().size==2 }
        }

    }

    @Test
    fun testGetExplain() {

    }

    @Before
    fun openDB(): Unit {
        //val a=ActivityScenarioRule(MainActivity::class.java)
        //val b=a.toString()
        //Log.d("1","ok")
        var context = ApplicationProvider.getApplicationContext<Context>()

        //Robolectric.setupActivity(MyActivity.class.java)
        //val activity = ActivityScenario.launch(MainActivity::class.java)

        //val context=TestApplication.getContext()
        db = Room.inMemoryDatabaseBuilder(context, WordBookDB::class.java).allowMainThreadQueries().build()
        table = db.wBookDao()
        //table.insertWord(WordBook("book", "book explain"))

    }
    @After
    fun closeDB(): Unit {
        db.close()
    }

}