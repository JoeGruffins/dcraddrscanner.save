package com.joegruff.decredaddressscanner.types

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlin.coroutines.CoroutineContext


const val NO_CONNECTION = "no_connection"

class GetInfoFromWeb(
    private val addr: Address,
    private val ctx: Context,
) : ViewModel(), CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + SupervisorJob()

    private val urlStr = UserSettings.get(ctx).url()
    private fun doInBackground() {
        try {
            getTicketInfo()
        } catch (e: Exception) {
            // Most likely due to failing to retrieve an endpoint, which is expected depending on
            // status.
        }
        val url = URL(urlStr + "address/" + addr.address + "/totals")
        addr.updateBalanceFromWebJSON(ctx, getGetResp(url))
        if (addr.ticketStatus == TicketStatus.SPENDABLE.Name) {
            addr.checkTicketSpent()
        }
    }

    private fun getGetResp(url: URL): String {
        val urlConnection = url.openConnection()
        urlConnection.connectTimeout = 5000
        val bufferedReader = BufferedReader(InputStreamReader(urlConnection.getInputStream()))
        val line = bufferedReader.use { it.readText() }
        bufferedReader.close()
        return line
    }

    // Ticket status is incremented from unmined -> immature -> live -> voted/expired/missed ->
    // (maybe revoked) -> spendable -> spent
    // TODO: Consider adding logic for a ticket that is originally mined but later becomes unmined
    //  due to a reorg.
    private fun getTicketInfo() {
        // Nothing to do if this isn`t a stake commitment.
        if (addr.ticketTXID == "") return
        fun status(): TicketStatus = ticketStatusFromName(addr.ticketStatus)
        // Return if status has reached spendable.
        val s = status()
        if (s == TicketStatus.SPENDABLE || s == TicketStatus.SPENT) return
        if (addr.address == "" || s == TicketStatus.UNMINED || s == TicketStatus.UNKNOWN) {
            val txURL = URL(urlStr + "tx/" + addr.ticketTXID)
            val txStr = getGetResp(txURL)
            val txToken = JSONTokener(txStr).nextValue()
            if (txToken !is JSONObject) {
                throw Exception("unknown JSON")
            }
            // If no address this is initiation.
            if (addr.address == "") {
                addr.initTicketFromWebJSON(txToken)
            }
            if (!addr.checkTicketMinedWebJSON(txToken)) return
        }
        if (status() == TicketStatus.IMMATURE) {
            if (!addr.checkTicketLive()) return
        }
        // Ticket is live.
        if (status() == TicketStatus.LIVE || addr.ticketSpendable == 0.0) {
            val statusURL = URL(urlStr + "tx/" + addr.ticketTXID + "/tinfo")
            val webStatus = getGetResp(statusURL)
            // Check if voted. If so populate ticketSpendable with a time.
            if (addr.checkTicketVotedWebJSON(webStatus)) {
                val token = JSONTokener(webStatus).nextValue()
                if (token !is JSONObject) {
                    throw Exception("unknown JSON")
                }
                val block = token.getJSONObject("lottery_block")
                val height = block.getInt("height")
                val blockURL = URL(urlStr + "block/" + height)
                val blockDetails = JSONTokener(getGetResp(blockURL)).nextValue()
                if (blockDetails !is JSONObject) {
                    throw Exception("unknown JSON")
                }
                val t = blockDetails.getInt("time").toDouble()
                val net = netFromName(addr.network)

                addr.ticketSpendable =
                    t + (net.TicketMaturity * net.TargetTimePerBlock * wiggleFactor)
            } else {
                // If not voted check if it was missed, expired, or revoked and when that is spendable.
                if (addr.checkTicketMissedWebJSON(webStatus) || addr.checkTicketExpired() || addr.checkTicketRevokedWebJSON(
                        webStatus
                    )
                ) {
                    val token = JSONTokener(webStatus).nextValue()
                    if (token !is JSONObject) {
                        throw Exception("unknown JSON")
                    }
                    val revocation = token.getString("revocation")
                    val revURL = URL(urlStr + "tx/" + revocation)
                    val revDetails = JSONTokener(getGetResp(revURL)).nextValue()
                    if (revDetails !is JSONObject) {
                        throw Exception("unknown JSON")
                    }
                    val block = revDetails.getJSONObject("block")
                    val t = block.getInt("time").toDouble()
                    val net = netFromName(addr.network)
                    addr.ticketSpendable =
                        t + (net.TicketMaturity * net.TargetTimePerBlock * wiggleFactor)
                }
            }
        }
        // Voted or revoked. Check until spendable.
        if (addr.ticketSpendable != 0.0) {
            addr.checkTicketSpendable()
        }
        // Spent must be checked after updating the balance.
    }

    fun execute() {
        addr.processBegan()
        launch {
            try {
                doInBackground()
                addr.processFinished(ctx)
            } catch (e: Exception) {
                when (e) {
                    is ConnectException, is UnknownHostException, is SocketTimeoutException ->
                        addr.processError(NO_CONNECTION)
                    else -> {
                        addr.processError(e.message ?: "unspecified error")
                    }
                }
            }
        }
    }
}