package com.joegruff.viacoinaddressscanner

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.joegruff.viacoinaddressscanner.helpers.AddressBook
import com.joegruff.viacoinaddressscanner.helpers.AddressObject
import com.joegruff.viacoinaddressscanner.helpers.GetInfoFromWeb
import org.json.JSONObject
import org.json.JSONTokener

class ViewAddressFragment : Fragment(), AsyncObserver {
    companion object {
        const val INTENT_DATA = "joe.viacoin.address.scanner.address"
        fun new(address: String): ViewAddressFragment {
            val args = Bundle()
            args.putSerializable(INTENT_DATA, address)
            val fragment = ViewAddressFragment()
            fragment.setArguments(args)
            return fragment
        }
    }

    var infoview: TextView? = null
    var addressbutton: Button? = null
    var imageview: ImageView? = null
    var labeledittext: EditText? = null
    var addressObject: AddressObject? = null
    var address = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        address = arguments?.getSerializable(INTENT_DATA) as String
        val v = inflater.inflate(R.layout.view_address_view, container, false)



        imageview = v.findViewById(R.id.view_address_view_qr_code)
        infoview = v.findViewById(R.id.view_address_view_info)
        addressbutton = v.findViewById(R.id.view_address_view_address_button)
        labeledittext = v.findViewById(R.id.view_address_view_label)

        AddressBook.getAddress(address)?.let {
            addressObject = it
            setupeditlabel()
            setinfoview()
            setupqrcode()
            setupaddressbutton()
        }

        GetInfoFromWeb(this, address).execute()

        return v
    }

    override fun onPause() {
        addressObject?.let {
            AddressBook.updateAddress(it)
            AddressBook.saveAddressBook(activity)
        }
        super.onPause()
    }

    override fun processfinished(output: String?) {
        if (output == null) {
            addressbutton?.setText(R.string.view_address_fragment_invalid_address)
            return
        }
        val token = JSONTokener(output).nextValue()
        if (token is JSONObject) {
            val addressString = token.getString("addrStr")
            val amountString = token.getString("balance")

            if (address == addressString) {
                if (addressObject == null) {
                    addressObject = AddressBook.newObjectFromAddress(addressString)
                    addressObject?.amount = amountString.toDouble()
                    activity?.let {
                        AddressBook.saveAddressBook(it)
                        setupeditlabel()
                        setupqrcode()
                        setupaddressbutton()
                    }
                }
                setinfoview()
            }

        }
    }




fun setinfoview() {
    var string = getString(R.string.view_address_fragment_balance)
    string += addressObject?.amount
    infoview?.setText(string)
}

fun setupeditlabel() {
    labeledittext?.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(p0: Editable?) {
        }

        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            addressObject?.title = p0.toString()
        }

    })
}

fun setupaddressbutton() {
    addressbutton?.setText(addressObject?.address)
}

fun setupqrcode() {
    try {
        val bitmap = textToQRBitmap(addressObject!!.address)
        imageview?.setImageBitmap(bitmap)
    } catch (e: WriterException) {
        e.printStackTrace()
    }
}

@Throws(WriterException::class)
fun textToQRBitmap(Value: String): Bitmap? {
    val bitMatrix: BitMatrix
    try {
        bitMatrix = MultiFormatWriter().encode(Value, BarcodeFormat.QR_CODE, 500, 500, null)
    } catch (Illegalargumentexception: IllegalArgumentException) {
        return null
    }

    val matrixWidth = bitMatrix.width
    val matrixHeight = bitMatrix.height
    val pixels = IntArray(matrixWidth * matrixHeight)

    for (y in 0 until matrixHeight) {
        val offset = y * matrixWidth
        for (x in 0 until matrixWidth) {
            pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
        }
    }
    val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.RGB_565)
    bitmap.setPixels(pixels, 0, 500, 0, 0, matrixWidth, matrixHeight)
    return bitmap
}
}


interface AsyncObserver {
    fun processfinished(output: String?)
}