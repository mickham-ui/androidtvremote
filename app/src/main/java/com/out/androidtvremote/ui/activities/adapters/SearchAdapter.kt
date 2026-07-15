package com.out.androidtvremote.ui.activities.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.connectsdk.device.ConnectableDevice
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textview.MaterialTextView
import com.out.androidtvremote.BuildConfig
import com.out.androidtvremote.R
import com.out.androidtvremote.interfaces.IFTDeviceAppListener
import com.out.androidtvremote.ui.activities.SearchActivity
import com.out.androidtvremote.ui.activities.viewmodel.SearchDeviceViewModel
import com.out.androidtvremote.utils.Constant
import kotlin.collections.get


class SearchAdapter(
    private val mContext: Context,
    private val connectDeviceListener: IFTDeviceAppListener.ConnectDeviceListener<ConnectableDevice>,
    private val searchDeviceViewModel: SearchDeviceViewModel
) : RecyclerView.Adapter<SearchAdapter.ConnectableDeviceViewHolder?>() {
    private var mData: ArrayList<ConnectableDevice>? = null

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConnectableDeviceViewHolder {
        val view = LayoutInflater.from(mContext).inflate(R.layout.item_device, parent, false)
        return ConnectableDeviceViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ConnectableDeviceViewHolder, position: Int) {
        val device = getItem(position)

        if (BuildConfig.DEBUG){
            holder.tvNameDevice.text = "${device.friendlyName}"
            holder.tvDeviceModel.text = device.connectedServiceNames

        }
        else{
            holder.tvNameDevice.text = device.friendlyName
            holder.tvDeviceModel.text = device.connectedServiceNames
        }

        // Show/hide progress bar and connected icon based on the connection state
        val isConnected = device.isConnected
        holder.ivConnectedSymbol.visibility = if (isConnected) View.VISIBLE else View.GONE
        if (isConnected) {
            holder.progressBar.visibility = View.GONE
        } else {
            holder.progressBar.visibility = if (device.isConnecting) View.VISIBLE else View.GONE
        }

        if (searchDeviceViewModel.updateProgressbar.value == true) {
            holder.progressBar.visibility = View.GONE
        }

        holder.llItemDevice.setOnClickListener {
            if (!device.isConnected) {
                holder.progressBar.visibility = View.VISIBLE
            }
            connectDeviceListener.onItemSearchClickListener(device)
        }
    }

    override fun getItemCount(): Int {
        return searchDeviceViewModel.connectableDevices.value?.size ?: 0
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }


    private fun getItem(position: Int): ConnectableDevice {
        return searchDeviceViewModel.connectableDevices.value?.get(position)
            ?: throw IllegalStateException("Position $position is out of bounds for the list")
    }

    inner class ConnectableDeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNameDevice: TextView = itemView.findViewById(R.id.tv_device_name)
        val tvDeviceModel : MaterialTextView = itemView.findViewById(R.id.tv_device_model)
        val ivConnectedSymbol: MaterialButton = itemView.findViewById(R.id.btn_connected)
        val progressBar: CircularProgressIndicator = itemView.findViewById(R.id.progress_connecting)
        val llItemDevice: ConstraintLayout = itemView.findViewById(R.id.cl_device)

    }

}
