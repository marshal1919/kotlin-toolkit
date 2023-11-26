package org.readium.r2.testapp

import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.text.SpannableString
import android.widget.Toast


class DictData(context: Context) {
    public var spanPos:Pair<Int,Int> = Pair(0,0)
    private var db: SQLiteDatabase? = null
    var isOpened:Boolean=false
    init{
        // 从preferences中读取数据
        val sharedPreferences = context.getSharedPreferences("dictpref", Context.MODE_PRIVATE)
        //val editor = sharedPreferences.edit()
        val path = sharedPreferences.getString("uri", null)
        isOpened = open(path)
        if(!isOpened)
            Toast.makeText(context, "字典安装不正确！", Toast.LENGTH_SHORT).show()

    }

    fun open(dbName: String?): Boolean {
        if(dbName==null) return false
        return try {
            db = SQLiteDatabase.openDatabase(dbName, null, SQLiteDatabase.OPEN_READONLY);
            true
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        }

    }

    fun getResult(cond: String): SpannableString {
        val sql="SELECT word,phonetic,definition,translation,exchange,tag from (select * from stardict where word=?) a  " +
            "left join tag b on a.id=b.id "
        val resultSet = db?.rawQuery(sql, arrayOf(cond))
        var rsContent:String=""
        var posStart=0;//需要不同的颜色的位置
        var posEnd=0;//需要不同的颜色的位置
        if (resultSet != null && resultSet.moveToFirst()) {
            // 获取每行记录的数据
            //rsContent+=resultSet.getString(0)
            rsContent+="音标:["+resultSet.getString(1)+"]"
            val tag=resultSet.getInt(5)
            if(tag!=null) {
                var s = ""
                if ((tag and 0b01) == 0b01) s += "中"
                if ((tag and 0b10) == 0b10) s += "高"
                if ((tag and 0b100) == 0b100) s += "研"
                if ((tag and 0b1000) == 0b1000) s += "4"
                if ((tag and 0b10000) == 0b10000) s += "6"
                if ((tag and 0b100000) == 0b100000) s += "雅"
                if ((tag and 0b1000000) == 0b1000000) s += "T"
                if ((tag and 0b10000000) == 0b10000000) s += "G"
                rsContent += " $s"
            }
            //rsContent+="\n"+resultSet.getString(2);
            rsContent+="\n"+resultSet.getString(3);

            if(resultSet.getString(4)!=null)
                rsContent+="\n"
            val sort=resultSet.getString(4).split("/")

            for(x in sort){
                val sign=x.split(":")
                var s=""
                if(sign[0] == "1") {
                    if (sign[1] == "p")
                        s = " 当前状态:过去式 "
                    else if (sign[1] == "d")
                        s = " 当前状态:过去分词 "
                    else if (sign[1] == "i")
                        s = " 当前状态:现在分词 "
                    else if (sign[1] == "3")
                        s = " 当前状态:第三人称单数 "
                    else if (sign[1] == "r")
                        s = " 当前状态:比较级 "
                    else if (sign[1] == "t")
                        s = " 当前状态:最高级 "
                    else if (sign[1] == "s")
                        s = " 当前状态:名词复数 "
                    else if (sign[1] == "0")
                        s = " 当前状态:原型 "
                }
                else if(sign[0]=="0"){
                    s=" 原型:"
                    posStart=rsContent.length+s.length
                    s+=sign[1]
                    posEnd=rsContent.length+s.length
                }
                else if(sign[0]=="p")
                    s=" 过去式:"+sign[1]
                else if(sign[0]=="d")
                    s=" 过去分词:"+sign[1]
                else if(sign[0]=="i")
                    s=" 现在分词:"+sign[1]
                else if(sign[0]=="3")
                    s=" 第三人称单数:"+sign[1]
                else if(sign[0]=="r")
                    s=" 比较级:"+sign[1]
                else if(sign[0]=="t")
                    s=" 最高级:"+sign[1]
                else if(sign[0]=="s")
                    s=" 名词复数:"+sign[1]

                rsContent+=s
            }


            /*| 类型 | 说明                                                       |
            | ---- | ---------------------------------------------------------- |
            | p    | 过去式（did）                                              |
            | d    | 过去分词（done）                                           |
            | i    | 现在分词（doing）                                          |
            | 3    | 第三人称单数（does）                                       |
            | r    | 形容词比较级（-er）                                        |
            | t    | 形容词最高级（-est）                                       |
            | s    | 名词复数形式                                               |
            | 0    | Lemma，如 perceived 的 Lemma 是 perceive                   |
            | 1    | Lemma 的变换形式，比如 s 代表 apples 是其 lemma 的复数形式 |*/



        }
        spanPos=Pair(posStart,posEnd)
        val spanRsContent = SpannableString(rsContent)

        resultSet?.close()
        return spanRsContent
    }

    protected fun finalize() {
        //if(db != null)
        db?.close()
    }

    public val isNull: Boolean
        get() = db == null
}