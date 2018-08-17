package com.joegruff.viacoinaddressscanner.helpers

import android.location.Address
import android.os.Handler
import android.os.Message
import android.util.Log
import com.joegruff.viacoinaddressscanner.R
import org.json.JSONObject
import org.json.JSONTokener
import java.util.*
import kotlin.concurrent.schedule

const val JSON_AMOUNT = "amount"
const val JSON_ADDRESS: String = "address"
const val JSON_TITLE = "title"
const val JSON_TIMESTAMP = "timestamp"
const val JSON_OLD_AMOUNT = "oldamount"
const val JSON_BEING_WATCHED = "beingwatched"

class AddressObject : AsyncObserver {


    var address = ""
    var title = ""
    var amount = -1.0
    var isUpdating = false
    var isValid = false
    var isBeingWatched = false
    var oldestAmount = 0.0
    var oldestTimestamp = Date().time.toDouble()
    var delegates: MutableList<AsyncObserver>? = null
    var hasBeenInitiated = false


    constructor(jsonObject: JSONObject) {
        address = jsonObject.getString(JSON_ADDRESS)
        title = jsonObject.getString(JSON_TITLE)
        amount = jsonObject.getDouble(JSON_AMOUNT)
        oldestAmount = jsonObject.getDouble(JSON_OLD_AMOUNT)
        oldestTimestamp = jsonObject.getDouble(JSON_TIMESTAMP)
        isBeingWatched = jsonObject.getBoolean(JSON_BEING_WATCHED)
        hasBeenInitiated = true
        isValid = true
        fiveminuteupdate()
    }

    constructor(add: String) {
        address = add
        fiveminuteupdate()
    }

    fun toJSON(): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put(JSON_ADDRESS, address)
        jsonObject.put(JSON_TITLE, title)
        jsonObject.put(JSON_AMOUNT, amount)
        jsonObject.put(JSON_OLD_AMOUNT, oldestAmount)
        jsonObject.put(JSON_TIMESTAMP, oldestTimestamp)
        jsonObject.put(JSON_BEING_WATCHED, isBeingWatched)
        return jsonObject
    }


    override fun processbegan() {
        isUpdating = true
        try {
            delegates?.forEach {processbegan()}
        } catch (e: Exception) {
            Log.d("addressobject", "processbegin " + e.printStackTrace())
        }

    }

    fun fiveminuteupdate() {
        update()
        Handler().postDelayed({
            fiveminuteupdate()
        }, 60000 * 5)


    }

    fun update() {
        Log.d("update", "isupdating = " + isUpdating)
        if (!isUpdating) {
            isUpdating = true
            GetInfoFromWeb(this, address).execute()
        }
    }

    override fun processfinished(output: String?) {

        var sendToDelegates = output


        if (output != null) {
            val token = JSONTokener(output).nextValue()
            if (token is JSONObject) {
                val addressString = token.getString("addrStr")
                val amountString = token.getString("balance")
                val amountDoubleFromString = amountString.toDouble()
                //val amountDoubleFromString = 1.0
                val elapsedHrsSinceChange = (Date().time.toDouble()-oldestTimestamp)/(1000*60*60)
                Log.d("time", "elapsed hrs since change " + elapsedHrsSinceChange + " and oldest timestamp " + oldestTimestamp + " and current time " + Date().time.toDouble())
                if (address == addressString) {

                    if (amount < 0) {
                        //dont imply a change if this is the initiation
                        oldestTimestamp = Date().time.toDouble()
                        oldestAmount = amountDoubleFromString
                        amount = amountDoubleFromString
                    } else if (amount != amountDoubleFromString ){
                        //record change
                        oldestTimestamp = Date().time.toDouble()
                        oldestAmount = amount
                        amount = amountDoubleFromString
                    } else if (elapsedHrsSinceChange > 24){
                        Log.d("24hrspassed","forgot oldestamount")
                        //forget older changes
                        oldestAmount = amount
                    }


                    Log.d("addressobject", "process finished " + output)
                    sendToDelegates = toJSON().toString()
                    if (!isValid) {
                        isValid = true
                    }
                }
            }
        }
        isUpdating = false

        try {
            delegates?.forEach{processfinished(sendToDelegates)}
        } catch (e: Exception) {

        }


    }


}