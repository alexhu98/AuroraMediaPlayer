package com.prettygoodcomputing.a4

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData

class MainViewModel(application: Application): AndroidViewModel(application) {

    var hello = MutableLiveData<String>()
    var toolbarTitle = MutableLiveData<String>()
    var toolbarSubtitle = MutableLiveData<String>()

    init {
        hello.value = "Hi!"
        toolbarTitle.value = "toolbar Title"
        toolbarSubtitle.value = "toolbar Subtitle"
    }
}
