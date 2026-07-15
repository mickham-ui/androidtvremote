package com.out.androidtvremote.interfaces


interface IFTDeviceAppListener {

    interface ConnectDeviceListener<T> {
        fun onItemSearchClickListener(item: T)
    }

    interface onCastItemClickListener<T>{
        fun onCastItemClick(item : T)
    }




}