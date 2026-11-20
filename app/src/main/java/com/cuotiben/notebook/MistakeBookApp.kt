package com.cuotiben.notebook

import android.app.Application
import com.cuotiben.notebook.data.AppDatabase
import com.cuotiben.notebook.data.MistakeRepository

class MistakeBookApp : Application() {

    val repository: MistakeRepository by lazy {
        MistakeRepository(AppDatabase.get(this).mistakeDao(), this)
    }
}
